// Package gateway owns the WebSocket layer: accepting connections,
// authenticating them against a session token before doing anything else,
// tracking which devices are currently online, and pushing envelopes to
// them as the relay router delivers messages.
package gateway

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"sync"
	"time"

	"github.com/gorilla/websocket"

	"privacy-messenger/relay"
)

const (
	writeWait      = 10 * time.Second
	pongWait       = 60 * time.Second
	pingInterval   = (pongWait * 9) / 10 // must be < pongWait
	maxMessageSize = 1 << 20             // 1 MiB per envelope; media goes over the separate media/ upload path, not this socket
)

// SessionValidator is the auth contract this package needs — implemented by
// auth.SessionService. Kept as an interface so the gateway doesn't need to
// know how sessions are actually stored.
type SessionValidator interface {
	Validate(ctx context.Context, token string) (*Session, error)
}

// Session mirrors the fields gateway needs from auth.Session, avoiding a
// direct dependency on the auth package's internal types.
type Session struct {
	UserID   string
	DeviceID string
}

// EnvelopeHandler processes an inbound envelope from a client (typically
// relay.Router.Route). Kept as a function type so gateway doesn't import
// relay's concrete Router struct directly.
type EnvelopeHandler func(ctx context.Context, envelope *relay.Envelope) error

// AckHandler processes a delivery acknowledgment from a client, deleting
// the confirmed message from durable storage (Cassandra). The userID and
// deviceID come from the authenticated connection, so a client can only
// acknowledge messages addressed to itself.
type AckHandler func(ctx context.Context, userID, deviceID, messageID string) error

// OfflineFlusher returns any envelopes queued for a device while it was
// disconnected, in delivery order, removing them from the queue. Kept as a
// function type so gateway doesn't depend on the queue package's concrete
// Redis-backed type — implemented by queue.OfflineQueue.Flush.
type OfflineFlusher func(ctx context.Context, userID, deviceID string) ([]*relay.Envelope, error)

var upgrader = websocket.Upgrader{
	// CheckOrigin is deliberately permissive: this is a native mobile app
	// talking to its own API, not a browser page vulnerable to CSRF-style
	// cross-origin WebSocket hijacking. If a web client is ever added,
	// this must be tightened to an explicit allowlist.
	CheckOrigin: func(r *http.Request) bool { return true },
}

type connection struct {
	ws        *websocket.Conn
	userID    string
	deviceID  string
	send      chan *relay.Envelope
	closeOnce sync.Once
}

// Registry tracks live connections, one per (userID, deviceID). It
// implements relay.Deliverer.
type Registry struct {
	mu    sync.RWMutex
	conns map[string]*connection // key: userID + ":" + deviceID
}

func NewRegistry() *Registry {
	return &Registry{conns: make(map[string]*connection)}
}

func connKey(userID, deviceID string) string {
	return userID + ":" + deviceID
}

// TryDeliver implements relay.Deliverer.
func (reg *Registry) TryDeliver(userID, deviceID string, envelope *relay.Envelope) bool {
	reg.mu.RLock()
	conn, ok := reg.conns[connKey(userID, deviceID)]
	reg.mu.RUnlock()
	if !ok {
		return false
	}

	select {
	case conn.send <- envelope:
		return true
	default:
		// Send buffer full — the client is connected but not keeping up.
		// Treat as "not deliverable right now" rather than blocking the
		// router or dropping the message silently; caller falls back to
		// the offline queue, which is the safe behavior either way.
		return false
	}
}

func (reg *Registry) register(c *connection) {
	reg.mu.Lock()
	defer reg.mu.Unlock()
	// If this device already has a connection (e.g. a reconnect race),
	// close the old one — only one authoritative connection per device.
	if existing, ok := reg.conns[connKey(c.userID, c.deviceID)]; ok {
		existing.close()
	}
	reg.conns[connKey(c.userID, c.deviceID)] = c
}

func (reg *Registry) unregister(c *connection) {
	reg.mu.Lock()
	defer reg.mu.Unlock()
	if current, ok := reg.conns[connKey(c.userID, c.deviceID)]; ok && current == c {
		delete(reg.conns, connKey(c.userID, c.deviceID))
	}
}

func (c *connection) close() {
	c.closeOnce.Do(func() {
		close(c.send)
		_ = c.ws.Close()
	})
}

// Server wires the registry, auth, and inbound-envelope handling together
// into an http.Handler suitable for mounting at e.g. /v1/ws.
type Server struct {
	registry     *Registry
	sessions     SessionValidator
	onEnvelope   EnvelopeHandler
	onAck        AckHandler
	flushOffline OfflineFlusher
	logger       *slog.Logger
}

func NewServer(sessions SessionValidator, onEnvelope EnvelopeHandler, onAck AckHandler, flushOffline OfflineFlusher, logger *slog.Logger) *Server {
	return &Server{
		registry:     NewRegistry(),
		sessions:     sessions,
		onEnvelope:   onEnvelope,
		onAck:        onAck,
		flushOffline: flushOffline,
		logger:       logger,
	}
}

// Registry exposes the live-connection registry so it can be handed to
// relay.NewRouter as the Deliverer.
func (s *Server) Registry() *Registry { return s.registry }

var ErrMissingToken = errors.New("missing session token")

func (s *Server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	token := r.URL.Query().Get("token")
	if token == "" {
		http.Error(w, ErrMissingToken.Error(), http.StatusUnauthorized)
		return
	}

	// Auth happens BEFORE the WebSocket upgrade — an unauthenticated
	// request never gets a live socket, so there's no window where an
	// unverified client can send data into the system.
	sess, err := s.sessions.Validate(r.Context(), token)
	if err != nil {
		http.Error(w, "invalid session", http.StatusUnauthorized)
		return
	}

	ws, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		s.logger.Warn("websocket upgrade failed", "error", err)
		return
	}

	conn := &connection{
		ws:       ws,
		userID:   sess.UserID,
		deviceID: sess.DeviceID,
		send:     make(chan *relay.Envelope, 64),
	}

	s.registry.register(conn)
	s.logger.Info("device connected", "user_id", conn.userID, "device_id", conn.deviceID)

	go s.writePump(conn)
	go s.readPump(conn)

	if s.flushOffline != nil {
		queued, err := s.flushOffline(r.Context(), conn.userID, conn.deviceID)
		if err != nil {
			s.logger.Warn("failed to flush offline queue", "user_id", conn.userID, "device_id", conn.deviceID, "error", err)
		}
		for _, envelope := range queued {
			// Best-effort: the connection was just registered, so send
			// should succeed; if the buffer is somehow already full this
			// message is dropped rather than blocking — an edge case
			// worth revisiting if it shows up in practice.
			select {
			case conn.send <- envelope:
			default:
				s.logger.Warn("dropped queued envelope, send buffer full immediately after connect",
					"user_id", conn.userID, "device_id", conn.deviceID)
			}
		}
	}
}

func (s *Server) readPump(c *connection) {
	defer func() {
		s.registry.unregister(c)
		c.close()
		s.logger.Info("device disconnected", "user_id", c.userID, "device_id", c.deviceID)
	}()

	c.ws.SetReadLimit(maxMessageSize)
	_ = c.ws.SetReadDeadline(time.Now().Add(pongWait))
	c.ws.SetPongHandler(func(string) error {
		return c.ws.SetReadDeadline(time.Now().Add(pongWait))
	})

	for {
		_, raw, err := c.ws.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseNormalClosure) {
				s.logger.Warn("unexpected close", "user_id", c.userID, "device_id", c.deviceID, "error", err)
			}
			return
		}

		var envelope relay.Envelope
		if err := json.Unmarshal(raw, &envelope); err != nil {
			// Malformed input from an authenticated client — drop the
			// message, keep the connection alive. Never echo the raw
			// payload back into logs.
			s.logger.Warn("dropping malformed envelope", "user_id", c.userID, "device_id", c.deviceID)
			continue
		}

		// ACK envelopes are client-to-server only: confirm delivery of a
		// message so the server can delete it from durable storage. They
		// are never routed to another device.
		if envelope.Type == relay.EnvelopeTypeAck {
			if s.onAck != nil && envelope.MessageID != "" {
				ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
				if err := s.onAck(ctx, c.userID, c.deviceID, envelope.MessageID); err != nil {
					s.logger.Warn("ack handling failed", "message_id", envelope.MessageID, "error", err)
				}
				cancel()
			}
			continue
		}

		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		if err := s.onEnvelope(ctx, &envelope); err != nil {
			s.logger.Warn("envelope handling failed", "error", err)
		}
		cancel()
	}
}

func (s *Server) writePump(c *connection) {
	ticker := time.NewTicker(pingInterval)
	defer func() {
		ticker.Stop()
		c.close()
	}()

	for {
		select {
		case envelope, ok := <-c.send:
			_ = c.ws.SetWriteDeadline(time.Now().Add(writeWait))
			if !ok {
				_ = c.ws.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}
			payload, err := json.Marshal(envelope)
			if err != nil {
				s.logger.Error("failed to marshal outgoing envelope", "error", err)
				continue
			}
			if err := c.ws.WriteMessage(websocket.TextMessage, payload); err != nil {
				return
			}

		case <-ticker.C:
			_ = c.ws.SetWriteDeadline(time.Now().Add(writeWait))
			if err := c.ws.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

// DeliverDirect pushes an envelope to a connected device outside the normal
// inbound-message path — used when flushing the offline queue on reconnect.
func (s *Server) DeliverDirect(userID, deviceID string, envelope *relay.Envelope) error {
	if s.registry.TryDeliver(userID, deviceID, envelope) {
		return nil
	}
	return fmt.Errorf("device %s/%s is not connected", userID, deviceID)
}
