package relay

import (
	"context"
	"fmt"
)

// Deliverer attempts immediate delivery to a device that's currently
// connected. Implemented by gateway.Registry — kept as an interface here so
// relay doesn't depend on the WebSocket transport package (gateway depends
// on relay, not the other way around).
type Deliverer interface {
	// TryDeliver returns true if the device has a live connection and the
	// envelope was handed to it. False means "not connected right now" —
	// not a delivery failure to be retried against the same connection.
	TryDeliver(userID, deviceID string, envelope *Envelope) bool
}

// OfflineQueue holds envelopes for devices that aren't currently connected,
// until they reconnect. Implemented against Redis in queue/offline_queue.go.
type OfflineQueue interface {
	Enqueue(ctx context.Context, userID, deviceID string, envelope *Envelope) error
}

// PushService sends a silent "wake-up" ping to an offline device, prompting
// it to reconnect to the WebSocket and drain its OfflineQueue.
type PushService interface {
	PingDevice(ctx context.Context, userID, deviceID string) error
}

type Router struct {
	live    Deliverer
	offline OfflineQueue
	push    PushService
}

func NewRouter(live Deliverer, offline OfflineQueue, push PushService) *Router {
	return &Router{live: live, offline: offline, push: push}
}

// Route validates and delivers an envelope: immediately if the recipient
// device is connected, otherwise queued for delivery on reconnect.
func (r *Router) Route(ctx context.Context, envelope *Envelope) error {
	if err := envelope.Validate(); err != nil {
		return err
	}
	envelope.Stamp()

	if r.live.TryDeliver(envelope.RecipientUserID, envelope.RecipientDeviceID, envelope) {
		return nil
	}

	if err := r.offline.Enqueue(ctx, envelope.RecipientUserID, envelope.RecipientDeviceID, envelope); err != nil {
		return fmt.Errorf("queuing envelope for offline delivery: %w", err)
	}

	// Device is offline and message is queued. Send a background push to wake it up.
	// This ping contains NO ciphertext, just an "action: ping" to trigger a reconnect.
	if r.push != nullPushService {
		_ = r.push.PingDevice(ctx, envelope.RecipientUserID, envelope.RecipientDeviceID)
	}

	return nil
}

// nullPushService is a safe fallback if FCM isn't configured
var nullPushService PushService = nil
