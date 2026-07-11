// Package cassandra provides durable, write-ahead storage for ciphertext
// envelopes. It exists alongside queue.OfflineQueue (Redis) rather than
// instead of it: Redis is the fast path used to actually deliver queued
// messages in order on reconnect, while this store is the durability
// backstop — if Redis loses data (restart, eviction under memory pressure,
// etc.) before a message is delivered, it can still be recovered from here.
//
// Nothing in this file ever touches plaintext: only ciphertext blobs and
// routing identifiers, matching the same boundary the Postgres and Redis
// layers keep.
package cassandra

import (
	"context"
	"fmt"
	"time"

	"github.com/gocql/gocql"

	"privacy-messenger/relay"
)

type MessageStore struct {
	session *gocql.Session
	ttl     time.Duration
}

// NewSession builds a gocql session from a list of contact-point hosts and
// a keyspace. Call this once at startup and share the *gocql.Session across
// the process; it is safe for concurrent use.
func NewSession(hosts []string, keyspace string) (*gocql.Session, error) {
	cluster := gocql.NewCluster(hosts...)
	cluster.Keyspace = keyspace
	cluster.Consistency = gocql.Quorum
	cluster.Timeout = 5 * time.Second
	return cluster.CreateSession()
}

func NewMessageStore(session *gocql.Session, ttl time.Duration) *MessageStore {
	return &MessageStore{session: session, ttl: ttl}
}

// Save durably persists an envelope before delivery is attempted. The
// table's own default_time_to_live (see schema.cql) is a second, independent
// expiry safety net in case application-level deletion is ever missed.
func (s *MessageStore) Save(ctx context.Context, envelope *relay.Envelope) (messageID gocql.UUID, err error) {
	messageID = gocql.TimeUUID()

	err = s.session.Query(`
		INSERT INTO pending_messages
			(recipient_user_id, recipient_device_id, message_id, envelope_type,
			 sealed_sender_ct, message_ct, created_at)
		VALUES (?, ?, ?, ?, ?, ?, ?)
		USING TTL ?
	`,
		envelope.RecipientUserID,
		envelope.RecipientDeviceID,
		messageID,
		string(envelope.Type),
		envelope.SealedSenderCiphertext,
		envelope.MessageCiphertext,
		time.Now(),
		int(s.ttl.Seconds()),
	).WithContext(ctx).Exec()

	if err != nil {
		return gocql.UUID{}, fmt.Errorf("persisting envelope: %w", err)
	}
	return messageID, nil
}

// Delete removes a message once its delivery has been confirmed (client
// sent a receipt) — the whole point of durable storage is to hold data only
// as long as it might still be needed.
func (s *MessageStore) Delete(ctx context.Context, userID, deviceID string, messageID gocql.UUID) error {
	err := s.session.Query(`
		DELETE FROM pending_messages
		WHERE recipient_user_id = ? AND recipient_device_id = ? AND message_id = ?
	`, userID, deviceID, messageID).WithContext(ctx).Exec()

	if err != nil {
		return fmt.Errorf("deleting delivered envelope: %w", err)
	}
	return nil
}

// ListPending returns all envelopes still held for a device — used for
// recovery if the Redis queue was lost, or as a startup reconciliation
// check. Not used on the normal hot delivery path.
func (s *MessageStore) ListPending(ctx context.Context, userID, deviceID string) ([]*relay.Envelope, error) {
	iter := s.session.Query(`
		SELECT envelope_type, sealed_sender_ct, message_ct, created_at
		FROM pending_messages
		WHERE recipient_user_id = ? AND recipient_device_id = ?
	`, userID, deviceID).WithContext(ctx).Iter()

	var envelopes []*relay.Envelope
	var envelopeType string
	var sealedSenderCT, messageCT []byte
	var createdAt time.Time

	for iter.Scan(&envelopeType, &sealedSenderCT, &messageCT, &createdAt) {
		envelopes = append(envelopes, &relay.Envelope{
			RecipientUserID:        userID,
			RecipientDeviceID:      deviceID,
			Type:                   relay.EnvelopeType(envelopeType),
			SealedSenderCiphertext: sealedSenderCT,
			MessageCiphertext:      messageCT,
			ServerTimestamp:        createdAt.UnixMilli(),
		})
	}
	if err := iter.Close(); err != nil {
		return nil, fmt.Errorf("listing pending envelopes: %w", err)
	}
	return envelopes, nil
}
