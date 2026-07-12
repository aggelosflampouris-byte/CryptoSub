package postgres

import (
	"context"
	"errors"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type UserRepository struct {
	pool *pgxpool.Pool
}

func NewUserRepository(pool *pgxpool.Pool) *UserRepository {
	return &UserRepository{pool: pool}
}

// CreateOrGetUser is idempotent on public_key_hash: if a user already exists
// for this key (e.g. reinstalling the app with the same identity key), their
// existing id is returned rather than creating a duplicate account.
func (r *UserRepository) CreateOrGetUser(ctx context.Context, publicKeyHash string) (string, error) {
	var userID string
	err := r.pool.QueryRow(ctx, `
		INSERT INTO users (public_key_hash)
		VALUES ($1)
		ON CONFLICT (public_key_hash) DO UPDATE SET public_key_hash = EXCLUDED.public_key_hash
		RETURNING id
	`, publicKeyHash).Scan(&userID)
	return userID, err
}

func (r *UserRepository) RegisterDevice(ctx context.Context, userID, deviceID string, identityPublicKey, signedPreKey []byte, registrationID int) error {
	_, err := r.pool.Exec(ctx, `
		INSERT INTO devices (id, user_id, identity_public_key, signed_pre_key, registration_id)
		VALUES ($1, $2, $3, $4, $5)
		ON CONFLICT (user_id, id)
		DO UPDATE SET identity_public_key = EXCLUDED.identity_public_key,
		              signed_pre_key = EXCLUDED.signed_pre_key,
		              registration_id = EXCLUDED.registration_id
	`, deviceID, userID, identityPublicKey, signedPreKey, registrationID)
	return err
}

// UpdateFCMToken updates the FCM push token for a specific device.
func (r *UserRepository) UpdateFCMToken(ctx context.Context, userID, deviceID, fcmToken string) error {
	_, err := r.pool.Exec(ctx, `
		UPDATE devices
		SET fcm_token = $1
		WHERE user_id = $2 AND id = $3
	`, fcmToken, userID, deviceID)
	return err
}

// PreKeyBundle is what a client needs to start an X3DH handshake with a
// peer's device without that peer being online.
type PreKeyBundle struct {
	IdentityPublicKey []byte `json:"identity_public_key"`
	SignedPreKey      []byte `json:"signed_pre_key"`
	RegistrationID    int    `json:"registration_id"`
	OneTimePreKeyID   *int   `json:"one_time_pre_key_id,omitempty"`
	OneTimePreKey     []byte `json:"one_time_pre_key,omitempty"`
}

// FetchPreKeyBundle returns everything needed to start a session with a
// device, consuming one one-time prekey if available.
func (r *UserRepository) FetchPreKeyBundle(ctx context.Context, userID, deviceID string) (*PreKeyBundle, error) {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback(ctx)

	bundle := &PreKeyBundle{}
	err = tx.QueryRow(ctx, `
		SELECT identity_public_key, signed_pre_key, registration_id
		FROM devices
		WHERE user_id = $1 AND id = $2
	`, userID, deviceID).Scan(&bundle.IdentityPublicKey, &bundle.SignedPreKey, &bundle.RegistrationID)

	if errors.Is(err, pgx.ErrNoRows) {
		return nil, errors.New("device not found")
	}
	if err != nil {
		return nil, err
	}

	var keyID int
	var publicKey []byte
	err = tx.QueryRow(ctx, `
		SELECT key_id, public_key
		FROM one_time_prekeys
		WHERE user_id = $1 AND device_id = $2
		ORDER BY key_id ASC
		LIMIT 1
		FOR UPDATE SKIP LOCKED
	`, userID, deviceID).Scan(&keyID, &publicKey)

	switch {
	case errors.Is(err, pgx.ErrNoRows):
		// No one-time prekeys left — handshake still proceeds
	case err != nil:
		return nil, err
	default:
		if _, delErr := tx.Exec(ctx, `
			DELETE FROM one_time_prekeys WHERE user_id = $1 AND device_id = $2 AND key_id = $3
		`, userID, deviceID, keyID); delErr != nil {
			return nil, delErr
		}
		bundle.OneTimePreKeyID = &keyID
		bundle.OneTimePreKey = publicKey
	}

	if err := tx.Commit(ctx); err != nil {
		return nil, err
	}
	return bundle, nil
}

// UploadOneTimePreKeys lets a device top up its supply of one-time prekeys.
func (r *UserRepository) UploadOneTimePreKeys(ctx context.Context, userID, deviceID string, keys map[int][]byte) error {
	batch := &pgx.Batch{}
	for keyID, publicKey := range keys {
		batch.Queue(`
			INSERT INTO one_time_prekeys (user_id, device_id, key_id, public_key)
			VALUES ($1, $2, $3, $4)
			ON CONFLICT (user_id, device_id, key_id) DO NOTHING
		`, userID, deviceID, keyID, publicKey)
	}
	br := r.pool.SendBatch(ctx, batch)
	defer br.Close()

	for range keys {
		if _, err := br.Exec(); err != nil {
			return err
		}
	}
	return nil
}

// CountOneTimePreKeys returns the number of unused one-time prekeys still
// available for a device.
func (r *UserRepository) CountOneTimePreKeys(ctx context.Context, userID, deviceID string) (int, error) {
	var count int
	err := r.pool.QueryRow(ctx, `
		SELECT COUNT(*) FROM one_time_prekeys
		WHERE user_id = $1 AND device_id = $2
	`, userID, deviceID).Scan(&count)
	return count, err
}

// FindUserByID looks up a user by their UUID (used for contact adding via short ID).
func (r *UserRepository) FindUserByID(ctx context.Context, userID string) (string, error) {
	var id string
	err := r.pool.QueryRow(ctx, `
		SELECT id::text FROM users WHERE id = $1::uuid
	`, userID).Scan(&id)
	if errors.Is(err, pgx.ErrNoRows) {
		return "", errors.New("user not found")
	}
	return id, err
}
