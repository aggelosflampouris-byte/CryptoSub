package auth

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"errors"
)

// HashPhoneNumber produces a deterministic HMAC-SHA256 of a phone number,
// keyed by a server-side pepper. It is deterministic (not salted per-call)
// because the server needs to look up a user by phone number on login —
// but it is keyed, so the raw phone number can't be recovered or brute-forced
// without the pepper, and the pepper is never stored alongside the hashes.
//
// pepper MUST come from a secret manager / environment variable at startup
// (e.g. config.Load reading PHONE_HASH_PEPPER) — it must never be committed
// to source control or hardcoded.
func HashPhoneNumber(phoneNumber, pepper string) (string, error) {
	if pepper == "" {
		return "", errors.New("phone hash pepper is empty — refusing to hash with no key")
	}
	mac := hmac.New(sha256.New, []byte(pepper))
	mac.Write([]byte(phoneNumber))
	return hex.EncodeToString(mac.Sum(nil)), nil
}
