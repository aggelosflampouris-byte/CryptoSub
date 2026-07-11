// Session tokens are opaque random values, not JWTs. This is a deliberate
// choice: a self-issued signed token (JWT) that embeds user identity means
// anyone reading the server's memory or logs at the wrong moment could
// forge trust, and revocation requires a denylist anyway. An opaque token
// backed by a server-side session table is simpler to reason about and to
// revoke immediately (delete the row).
package auth

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"errors"
	"fmt"
	"time"
)

var ErrSessionInvalid = errors.New("session invalid or expired")

type Session struct {
	UserID    string
	DeviceID  string
	ExpiresAt time.Time
}

// SessionStore is the persistence contract. Concrete implementation in
// db/postgres.
type SessionStore interface {
	SaveSession(ctx context.Context, tokenHash []byte, userID, deviceID string, expiresAt time.Time) error
	GetSession(ctx context.Context, tokenHash []byte) (userID, deviceID string, expiresAt time.Time, err error)
	DeleteSession(ctx context.Context, tokenHash []byte) error
}

type SessionService struct {
	store SessionStore
	ttl   time.Duration
}

func NewSessionService(store SessionStore, ttl time.Duration) *SessionService {
	return &SessionService{store: store, ttl: ttl}
}

// IssueToken creates a new opaque session token, stores its hash, and
// returns the raw token to send to the client. The raw token is never
// persisted — only recoverable by the client that receives it once.
func (s *SessionService) IssueToken(ctx context.Context, userID, deviceID string) (string, error) {
	raw := make([]byte, 32) // 256 bits of entropy
	if _, err := rand.Read(raw); err != nil {
		return "", fmt.Errorf("generating session token: %w", err)
	}
	token := base64.RawURLEncoding.EncodeToString(raw)

	hash := hashToken(token)
	expiresAt := time.Now().Add(s.ttl)
	if err := s.store.SaveSession(ctx, hash, userID, deviceID, expiresAt); err != nil {
		return "", fmt.Errorf("saving session: %w", err)
	}
	return token, nil
}

// Validate looks up a session by the hash of the presented token. Comparison
// of the stored hash happens via the DB equality lookup; the token itself is
// never compared in plaintext across a network boundary.
func (s *SessionService) Validate(ctx context.Context, token string) (*Session, error) {
	hash := hashToken(token)
	userID, deviceID, expiresAt, err := s.store.GetSession(ctx, hash)
	if err != nil {
		return nil, ErrSessionInvalid
	}
	if time.Now().After(expiresAt) {
		_ = s.store.DeleteSession(ctx, hash)
		return nil, ErrSessionInvalid
	}
	return &Session{UserID: userID, DeviceID: deviceID, ExpiresAt: expiresAt}, nil
}

// Revoke immediately invalidates a session (e.g. on logout or device removal).
func (s *SessionService) Revoke(ctx context.Context, token string) error {
	return s.store.DeleteSession(ctx, hashToken(token))
}

func hashToken(token string) []byte {
	sum := sha256.Sum256([]byte(token))
	return sum[:]
}

// constantTimeEqual is kept available for any call site that ends up
// comparing two hashes directly rather than via a DB lookup.
func constantTimeEqual(a, b []byte) bool {
	return subtle.ConstantTimeCompare(a, b) == 1
}
