// Package postgres implements the storage interfaces required by the auth
// and gateway packages against PostgreSQL, via pgx. Every query below uses
// parameter placeholders ($1, $2, ...) — user input is never concatenated
// into SQL text, which rules out SQL injection through this layer.
package postgres

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type OTPRepository struct {
	pool *pgxpool.Pool
}

func NewOTPRepository(pool *pgxpool.Pool) *OTPRepository {
	return &OTPRepository{pool: pool}
}

// SaveOTP upserts the current code for a given phone hash, resetting the
// try counter — a new code request always starts a fresh attempt budget.
func (r *OTPRepository) SaveOTP(ctx context.Context, phoneHash string, codeHash []byte, expiresAt time.Time) error {
	_, err := r.pool.Exec(ctx, `
		INSERT INTO otp_codes (phone_hash, code_hash, expires_at, tries)
		VALUES ($1, $2, $3, 0)
		ON CONFLICT (phone_hash)
		DO UPDATE SET code_hash = EXCLUDED.code_hash,
		              expires_at = EXCLUDED.expires_at,
		              tries = 0
	`, phoneHash, codeHash, expiresAt)
	return err
}

func (r *OTPRepository) GetOTP(ctx context.Context, phoneHash string) ([]byte, time.Time, int, error) {
	var codeHash []byte
	var expiresAt time.Time
	var tries int

	err := r.pool.QueryRow(ctx, `
		SELECT code_hash, expires_at, tries
		FROM otp_codes
		WHERE phone_hash = $1
	`, phoneHash).Scan(&codeHash, &expiresAt, &tries)

	if errors.Is(err, pgx.ErrNoRows) {
		return nil, time.Time{}, 0, errors.New("no otp found for this phone number")
	}
	return codeHash, expiresAt, tries, err
}

func (r *OTPRepository) IncrementOTPTries(ctx context.Context, phoneHash string) error {
	_, err := r.pool.Exec(ctx, `
		UPDATE otp_codes SET tries = tries + 1 WHERE phone_hash = $1
	`, phoneHash)
	return err
}

func (r *OTPRepository) DeleteOTP(ctx context.Context, phoneHash string) error {
	_, err := r.pool.Exec(ctx, `
		DELETE FROM otp_codes WHERE phone_hash = $1
	`, phoneHash)
	return err
}
