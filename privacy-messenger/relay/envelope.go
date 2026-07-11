// Package relay routes encrypted envelopes between devices. The server only
// ever handles ciphertext plus the minimum routing metadata needed to
// deliver it — it cannot read message content, and sealed-sender delivery
// means it doesn't need to know who sent a message either, only who it's
// for.
package relay

import (
	"errors"
	"time"
)

// Envelope is the only shape of data the relay understands. Content is
// opaque ciphertext produced by the client's Double Ratchet / MLS layer —
// the relay does not parse, decrypt, or interpret it in any way.
type Envelope struct {
	// MessageID is assigned by the server after durable persistence to
	// Cassandra. Clients include this in acknowledgment messages so the
	// server can delete the confirmed envelope from durable storage.
	MessageID string `json:"message_id,omitempty"`

	// RecipientUserID / RecipientDeviceID identify where to deliver this.
	// These are the only routing fields the server strictly needs.
	RecipientUserID   string `json:"recipient_user_id"`
	RecipientDeviceID string `json:"recipient_device_id"`

	// SealedSenderCiphertext contains the sender's identity encrypted
	// inside the envelope itself (readable only by the recipient's client
	// after decryption), rather than as a separate plaintext field the
	// server could log. See sealed_sender.go.
	SealedSenderCiphertext []byte `json:"sealed_sender_ciphertext"`

	// MessageCiphertext is the actual Double-Ratchet-encrypted message
	// payload. Opaque to the server.
	MessageCiphertext []byte `json:"message_ciphertext"`

	// Type distinguishes ordinary messages from key-exchange/control
	// envelopes (e.g. a prekey message establishing a new session) so the
	// client knows how to process it, without revealing content.
	Type EnvelopeType `json:"type"`

	// ServerTimestamp is set by the relay on receipt, purely for delivery
	// ordering — not trusted for anything security-relevant on the client.
	ServerTimestamp int64 `json:"server_timestamp"`
}

type EnvelopeType string

const (
	EnvelopeTypePreKeyMessage EnvelopeType = "prekey_message" // first message of a new session
	EnvelopeTypeCiphertext    EnvelopeType = "ciphertext"     // ordinary ratcheted message
	EnvelopeTypeReceipt       EnvelopeType = "receipt"        // delivery/read receipt, still encrypted
	EnvelopeTypeGroupControl  EnvelopeType = "group_control"  // MLS group operation message
	EnvelopeTypeAck           EnvelopeType = "ack"            // client → server delivery acknowledgment
)

var ErrInvalidEnvelope = errors.New("envelope missing required fields")

// Validate checks structural correctness only — it has no way to (and
// should never try to) validate the ciphertext's contents.
func (e *Envelope) Validate() error {
	// ACK envelopes are client-to-server only and require just a MessageID.
	if e.Type == EnvelopeTypeAck {
		if e.MessageID == "" {
			return ErrInvalidEnvelope
		}
		return nil
	}

	if e.RecipientUserID == "" || e.RecipientDeviceID == "" {
		return ErrInvalidEnvelope
	}
	if len(e.MessageCiphertext) == 0 && e.Type != EnvelopeTypeGroupControl {
		return ErrInvalidEnvelope
	}
	switch e.Type {
	case EnvelopeTypePreKeyMessage, EnvelopeTypeCiphertext, EnvelopeTypeReceipt, EnvelopeTypeGroupControl:
	default:
		return ErrInvalidEnvelope
	}
	return nil
}

func (e *Envelope) Stamp() {
	e.ServerTimestamp = time.Now().UnixMilli()
}
