// Package auth handles phone verification and session issuance.
//
// Security notes:
//   - OTP codes are never stored in plaintext — only their SHA-256 hash,
//     so a database read (or leak) does not reveal usable codes.
//   - Comparisons use constant-time functions to avoid timing side channels.
//   - Codes expire and are single-use (consumed on successful verification).
package auth

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"errors"
	"fmt"
	"time"
)

const (
	otpLength   = 6
	otpTTL      = 5 * time.Minute
	otpMaxTries = 5 // per code, to slow brute force without locking users out forever
)

var (
	ErrOTPExpired      = errors.New("otp expired")
	ErrOTPInvalid      = errors.New("otp invalid")
	ErrOTPTooManyTries = errors.New("too many incorrect attempts, request a new code")
)

// OTPStore is the persistence contract this package needs. The concrete
// implementation lives in db/postgres — kept as an interface here so auth
// logic can be tested without a live database.
type OTPStore interface {
	SaveOTP(ctx context.Context, phoneHash string, codeHash []byte, expiresAt time.Time) error
	GetOTP(ctx context.Context, phoneHash string) (codeHash []byte, expiresAt time.Time, tries int, err error)
	IncrementOTPTries(ctx context.Context, phoneHash string) error
	DeleteOTP(ctx context.Context, phoneHash string) error
}

// SMSSender abstracts the SMS provider so no vendor is hardcoded into the
// auth flow. Implement this once against whichever provider is chosen.
type SMSSender interface {
	SendSMS(ctx context.Context, phoneNumber string, message string) error
}

type OTPService struct {
	store  OTPStore
	sender SMSSender
}

func NewOTPService(store OTPStore, sender SMSSender) *OTPService {
	return &OTPService{store: store, sender: sender}
}

// RequestOTP generates a fresh code, stores only its hash, and sends the
// plaintext code to the user's phone. phoneHash is a salted hash of the
// phone number (see HashPhoneNumber) — the raw number is never persisted.
func (s *OTPService) RequestOTP(ctx context.Context, phoneNumber, phoneHash string) error {
	code, err := generateNumericCode(otpLength)
	if err != nil {
		return fmt.Errorf("generating otp: %w", err)
	}

	hash := hashCode(code)
	if err := s.store.SaveOTP(ctx, phoneHash, hash, time.Now().Add(otpTTL)); err != nil {
		return fmt.Errorf("saving otp: %w", err)
	}

	message := fmt.Sprintf("Your verification code is %s. It expires in 5 minutes.", code)
	if err := s.sender.SendSMS(ctx, phoneNumber, message); err != nil {
		return fmt.Errorf("sending otp sms: %w", err)
	}
	return nil
}

// VerifyOTP checks a user-submitted code against the stored hash. On success
// the stored code is deleted so it cannot be reused (single-use).
func (s *OTPService) VerifyOTP(ctx context.Context, phoneHash, submittedCode string) error {
	storedHash, expiresAt, tries, err := s.store.GetOTP(ctx, phoneHash)
	if err != nil {
		return fmt.Errorf("looking up otp: %w", err)
	}

	if time.Now().After(expiresAt) {
		_ = s.store.DeleteOTP(ctx, phoneHash)
		return ErrOTPExpired
	}
	if tries >= otpMaxTries {
		_ = s.store.DeleteOTP(ctx, phoneHash)
		return ErrOTPTooManyTries
	}

	submittedHash := hashCode(submittedCode)
	if subtle.ConstantTimeCompare(storedHash, submittedHash) != 1 {
		if incErr := s.store.IncrementOTPTries(ctx, phoneHash); incErr != nil {
			return fmt.Errorf("recording failed otp attempt: %w", incErr)
		}
		return ErrOTPInvalid
	}

	return s.store.DeleteOTP(ctx, phoneHash)
}

// generateNumericCode returns a cryptographically random numeric string of
// the given length using crypto/rand (never math/rand).
func generateNumericCode(length int) (string, error) {
	digits := make([]byte, length)
	for i := range digits {
		b := make([]byte, 1)
		if _, err := rand.Read(b); err != nil {
			return "", err
		}
		// Rejection sampling to avoid modulo bias (256 % 10 != 0).
		for b[0] >= 250 {
			if _, err := rand.Read(b); err != nil {
				return "", err
			}
		}
		digits[i] = '0' + b[0]%10
	}
	return string(digits), nil
}

func hashCode(code string) []byte {
	sum := sha256.Sum256([]byte(code))
	return sum[:]
}
