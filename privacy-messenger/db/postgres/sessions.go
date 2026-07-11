package postgres

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type SessionRepository struct {
	pool *pgxpool.Pool
}

func NewSessionRepository(pool *pgxpool.Pool) *SessionRepository {
	return &SessionRepository{pool: pool}
}

func (r *SessionRepository) SaveSession(ctx context.Context, tokenHash []byte, userID, deviceID string, expiresAt time.Time) error {
	_, err := r.pool.Exec(ctx, `
		INSERT INTO sessions (token_hash, user_id, device_id, expires_at)
		VALUES ($1, $2, $3, $4)
	`, tokenHash, userID, deviceID, expiresAt)
	return err
}

func (r *SessionRepository) GetSession(ctx context.Context, tokenHash []byte) (userID, deviceID string, expiresAt time.Time, err error) {
	err = r.pool.QueryRow(ctx, `
		SELECT user_id, device_id, expires_at
		FROM sessions
		WHERE token_hash = $1
	`, tokenHash).Scan(&userID, &deviceID, &expiresAt)

	if errors.Is(err, pgx.ErrNoRows) {
		return "", "", time.Time{}, errors.New("session not found")
	}
	return userID, deviceID, expiresAt, err
}

func (r *SessionRepository) DeleteSession(ctx context.Context, tokenHash []byte) error {
	_, err := r.pool.Exec(ctx, `
		DELETE FROM sessions WHERE token_hash = $1
	`, tokenHash)
	return err
}

// PruneExpired deletes expired sessions. Intended to be called periodically
// by a background job (see cmd/janitor or a cron-triggered endpoint) —
// expired rows are otherwise harmless (Validate checks expiry itself) but
// left unpruned they'd grow the table indefinitely.
func (r *SessionRepository) PruneExpired(ctx context.Context) (int64, error) {
	tag, err := r.pool.Exec(ctx, `DELETE FROM sessions WHERE expires_at < now()`)
	if err != nil {
		return 0, err
	}
	return tag.RowsAffected(), nil
}
