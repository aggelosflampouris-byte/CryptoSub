// Package queue holds envelopes for devices that are not currently
// connected. Redis is used deliberately (not Postgres): this data is
// transient by design — every entry is deleted the moment it's delivered,
// and even undelivered entries expire after OfflineMessageTTL.
package queue

import (
	"context"
	"encoding/json"
	"fmt"
	"math"
	"time"

	"github.com/redis/go-redis/v9"

	"privacy-messenger/relay"
)

type OfflineQueue struct {
	client *redis.Client
	ttl    time.Duration
}

func NewOfflineQueue(client *redis.Client, ttl time.Duration) *OfflineQueue {
	return &OfflineQueue{client: client, ttl: ttl}
}

func queueKey(userID, deviceID string) string {
	return fmt.Sprintf("offline_queue:%s:%s", userID, deviceID)
}

// Enqueue appends an envelope to the recipient's queue and (re)sets the
// key's TTL. Using a list means delivery order is preserved (FIFO).
func (q *OfflineQueue) Enqueue(ctx context.Context, userID, deviceID string, envelope *relay.Envelope) error {
	payload, err := json.Marshal(envelope)
	if err != nil {
		return fmt.Errorf("marshaling envelope: %w", err)
	}

	key := queueKey(userID, deviceID)
	pipe := q.client.TxPipeline()
	pipe.RPush(ctx, key, payload)
	pipe.Expire(ctx, key, q.ttl)
	_, err = pipe.Exec(ctx)
	if err != nil {
		return fmt.Errorf("enqueuing envelope: %w", err)
	}
	return nil
}

// Flush atomically pops every queued envelope for a device and returns them
// in delivery order. Called when a device reconnects, before it's marked
// ready to receive live traffic, so nothing arrives out of order.
func (q *OfflineQueue) Flush(ctx context.Context, userID, deviceID string) ([]*relay.Envelope, error) {
	key := queueKey(userID, deviceID)

	// LPOP with a large count in one round trip, then delete the (now
	// empty, or still-partial) key's TTL isn't needed further since an
	// empty list key is auto-removed by Redis.
	raws, err := q.client.LPopCount(ctx, key, math.MaxInt32).Result()
	if err != nil && err != redis.Nil {
		return nil, fmt.Errorf("flushing offline queue: %w", err)
	}

	envelopes := make([]*relay.Envelope, 0, len(raws))
	for _, raw := range raws {
		var e relay.Envelope
		if err := json.Unmarshal([]byte(raw), &e); err != nil {
			// A corrupted queue entry shouldn't block delivery of the rest.
			continue
		}
		envelopes = append(envelopes, &e)
	}
	return envelopes, nil
}

// Depth reports how many envelopes are currently queued for a device —
// useful for client-side "N messages waiting" indicators or server metrics.
func (q *OfflineQueue) Depth(ctx context.Context, userID, deviceID string) (int64, error) {
	return q.client.LLen(ctx, queueKey(userID, deviceID)).Result()
}
