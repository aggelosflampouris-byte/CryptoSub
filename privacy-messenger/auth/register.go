// Package auth handles wallet-based identity registration and session management.
//
// Unlike phone-based registration, there is no OTP or SMS step. The client
// generates a cryptographic identity key pair locally and sends only the
// public half to the server. The user's identity IS their public key.
//
// This model is used by Session Messenger, Briar, and SimpleX Chat.
// It is strictly more private than Signal (which requires a phone number).
package auth

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
)

var ErrDeviceKeyMissing = errors.New("device public key and registration id are required")

// UserStore is the persistence contract for user/device records. Concrete
// implementation lives in db/postgres.
type UserStore interface {
	// CreateOrGetUser returns the existing user id for the given public key
	// hash, or creates a new user row and returns the new id. Idempotent.
	CreateOrGetUser(ctx context.Context, publicKeyHash string) (userID string, err error)

	// RegisterDevice stores a device's public identity key and signed
	// prekey bundle so other clients can initiate X3DH with it.
	RegisterDevice(ctx context.Context, userID, deviceID string, identityPublicKey, signedPreKey []byte, registrationID int) error
}

// RegistrationInput contains everything the client sends in a single POST
// to register. No phone number, no OTP — just cryptographic key material.
type RegistrationInput struct {
	DeviceID          string
	IdentityPublicKey []byte // client-generated; server never sees the private key
	SignedPreKey      []byte
	RegistrationID    int
	DisplayName       string // optional, user-chosen
}

type RegistrationResult struct {
	UserID       string
	SessionToken string
}

type RegistrationService struct {
	users   UserStore
	session *SessionService
}

func NewRegistrationService(users UserStore, session *SessionService) *RegistrationService {
	return &RegistrationService{users: users, session: session}
}

// Register performs single-step, zero-knowledge registration:
//  1. Derive a deterministic user identity from SHA256(identity_public_key)
//  2. Create or retrieve the user record (idempotent — reinstalling recovers the account)
//  3. Register the device's key material
//  4. Issue a session token
func (r *RegistrationService) Register(ctx context.Context, in RegistrationInput) (*RegistrationResult, error) {
	if len(in.IdentityPublicKey) == 0 || len(in.SignedPreKey) == 0 || in.RegistrationID == 0 {
		return nil, ErrDeviceKeyMissing
	}
	if in.DeviceID == "" {
		in.DeviceID = "1"
	}

	// Derive a deterministic identifier from the public key so that
	// reinstalling with the same key pair recovers the same account.
	publicKeyHash := HashPublicKey(in.IdentityPublicKey)

	userID, err := r.users.CreateOrGetUser(ctx, publicKeyHash)
	if err != nil {
		return nil, fmt.Errorf("creating user: %w", err)
	}

	if err := r.users.RegisterDevice(ctx, userID, in.DeviceID, in.IdentityPublicKey, in.SignedPreKey, in.RegistrationID); err != nil {
		return nil, fmt.Errorf("registering device: %w", err)
	}

	token, err := r.session.IssueToken(ctx, userID, in.DeviceID)
	if err != nil {
		return nil, fmt.Errorf("issuing session: %w", err)
	}

	return &RegistrationResult{UserID: userID, SessionToken: token}, nil
}

// HashPublicKey produces a hex-encoded SHA-256 of a public key.
// This is used as the stable user identifier in the database.
func HashPublicKey(publicKey []byte) string {
	sum := sha256.Sum256(publicKey)
	return hex.EncodeToString(sum[:])
}
