package auth

import (
	"context"
	"errors"
	"fmt"
)

var ErrDeviceKeyMissing = errors.New("device public key and registration id are required")

// UserStore is the persistence contract for user/device records. Concrete
// implementation lives in db/postgres. Note this store never touches
// message content — only identity and public key material.
type UserStore interface {
	// CreateOrGetUser returns the existing user id for phoneHash, or creates
	// a new user row and returns the new id. Idempotent by phoneHash.
	CreateOrGetUser(ctx context.Context, phoneHash string) (userID string, err error)

	// RegisterDevice stores a device's public identity key and signed
	// prekey bundle so other clients can initiate X3DH with it.
	RegisterDevice(ctx context.Context, userID, deviceID string, identityPublicKey, signedPreKey []byte, registrationID int) error
}

type RegistrationInput struct {
	PhoneNumber       string
	OTPCode           string
	DeviceID          string
	IdentityPublicKey []byte // client-generated; server never sees the private key
	SignedPreKey      []byte
	RegistrationID    int
}

type RegistrationResult struct {
	UserID       string
	SessionToken string
}

type RegistrationService struct {
	otp     *OTPService
	users   UserStore
	session *SessionService
	pepper  string // phone-hash pepper, injected from config, never hardcoded
}

func NewRegistrationService(otp *OTPService, users UserStore, session *SessionService, phoneHashPepper string) *RegistrationService {
	return &RegistrationService{otp: otp, users: users, session: session, pepper: phoneHashPepper}
}

// StartRegistration sends an OTP to the given phone number. It does not yet
// create any user record — no account exists until the code is verified.
func (r *RegistrationService) StartRegistration(ctx context.Context, phoneNumber string) error {
	phoneHash, err := HashPhoneNumber(phoneNumber, r.pepper)
	if err != nil {
		return fmt.Errorf("hashing phone number: %w", err)
	}
	return r.otp.RequestOTP(ctx, phoneNumber, phoneHash)
}

// CompleteRegistration verifies the OTP and, only on success, creates the
// user + device records and issues a session token.
func (r *RegistrationService) CompleteRegistration(ctx context.Context, in RegistrationInput) (*RegistrationResult, error) {
	if len(in.IdentityPublicKey) == 0 || len(in.SignedPreKey) == 0 || in.RegistrationID == 0 {
		return nil, ErrDeviceKeyMissing
	}

	phoneHash, err := HashPhoneNumber(in.PhoneNumber, r.pepper)
	if err != nil {
		return nil, fmt.Errorf("hashing phone number: %w", err)
	}

	if err := r.otp.VerifyOTP(ctx, phoneHash, in.OTPCode); err != nil {
		return nil, err // ErrOTPExpired / ErrOTPInvalid / ErrOTPTooManyTries surfaced as-is
	}

	userID, err := r.users.CreateOrGetUser(ctx, phoneHash)
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
