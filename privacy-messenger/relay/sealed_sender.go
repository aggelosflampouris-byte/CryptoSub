package relay

import "time"

// The WebSocket gateway authenticates the connection (so the server knows
// *a* valid device sent this envelope, for rate-limiting/abuse prevention),
// but that authenticated identity must never be attached to the envelope
// as it's routed or logged — only the recipient's client, decrypting
// SealedSenderCiphertext, learns who the sender was.
//
// This file exists to make that separation explicit and hard to violate
// by accident: LogSafeEnvelope is the only shape of envelope data that's
// allowed to reach a logger or metrics system.

// LogSafeEnvelope is a redacted view of an Envelope containing only what's
// safe to log: recipient routing info, size, and timing — never the
// sealed-sender ciphertext or message ciphertext, and never the
// authenticated sender identity from the connection.
type LogSafeEnvelope struct {
	RecipientUserID   string
	RecipientDeviceID string
	Type              EnvelopeType
	CiphertextSize    int
	ServerTimestamp   int64
}

// Redact converts an Envelope plus the authenticated (but never-to-be-logged)
// connection identity into something safe to pass to a logger or metrics
// pipeline. Callers in gateway/relay must route all logging through this —
// never log an Envelope or a connection's device ID directly next to it.
func Redact(e *Envelope) LogSafeEnvelope {
	return LogSafeEnvelope{
		RecipientUserID:   e.RecipientUserID,
		RecipientDeviceID: e.RecipientDeviceID,
		Type:              e.Type,
		CiphertextSize:    len(e.MessageCiphertext) + len(e.SealedSenderCiphertext),
		ServerTimestamp:   e.ServerTimestamp,
	}
}

// RateLimitKey returns a value usable for per-connection rate limiting
// (e.g. in the gateway's token bucket) that is scoped to the authenticated
// device but is never persisted or logged — used in-memory only, for the
// lifetime of enforcing the limit, then discarded.
func RateLimitKey(authenticatedUserID, authenticatedDeviceID string) string {
	return authenticatedUserID + ":" + authenticatedDeviceID
}

// deliveryWindow bounds how far in the past/future a client-claimed send
// time may reasonably be before the gateway treats ServerTimestamp as the
// only trustworthy ordering signal. Not currently enforced elsewhere in
// this package, but kept here so gateway code has one shared constant
// rather than each call site inventing its own tolerance.
const deliveryWindowSkewTolerance = 5 * time.Minute
