package main

import (
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"privacy-messenger/auth"
	"privacy-messenger/attachments"
	"privacy-messenger/db/postgres"
)

func registerHTTPRoutes(mux *http.ServeMux, reg *auth.RegistrationService, sessions *auth.SessionService, users *postgres.UserRepository, s3Store *attachments.S3Store) {
	// --- Registration & auth ---
	mux.HandleFunc("POST /v1/register", handleRegister(reg))
	mux.HandleFunc("POST /v1/logout", handleLogout(sessions))

	// --- Key exchange (X3DH prekey bundles) ---
	mux.HandleFunc("GET /v1/users/{userId}/devices/{deviceId}/bundle", handleFetchPreKeyBundle(users, sessions))
	mux.HandleFunc("POST /v1/devices/prekeys", handleUploadPreKeys(users, sessions))
	mux.HandleFunc("GET /v1/devices/prekeys/count", handlePreKeyCount(users, sessions))

	// --- Identity / Discovery ---
	mux.HandleFunc("GET /v1/users/{userId}", handleGetUser(users, sessions))

	// --- FCM ---
	mux.HandleFunc("POST /v1/devices/fcm", handleUpdateFCMToken(users, sessions))

	// --- Media Attachments (AWS S3) ---
	mux.HandleFunc("GET /v1/attachments/upload-url", handleGenerateUploadURL(s3Store, sessions))
}

// ---------------------------------------------------------------------------
// Auth helper
// ---------------------------------------------------------------------------

// authenticateRequest extracts and validates a session token from the
// Authorization header (Bearer scheme). Returns the authenticated session
// or an error.
func authenticateRequest(r *http.Request, sessions *auth.SessionService) (*auth.Session, error) {
	header := r.Header.Get("Authorization")
	if header == "" {
		return nil, errors.New("missing authorization header")
	}
	token := strings.TrimPrefix(header, "Bearer ")
	if token == header {
		// No "Bearer " prefix — treat the raw header value as the token
		// for backwards compatibility with simple clients.
		token = header
	}
	return sessions.Validate(r.Context(), token)
}

// ---------------------------------------------------------------------------
// Registration handlers
// ---------------------------------------------------------------------------

type registerRequest struct {
	DeviceID          string `json:"device_id"`
	IdentityPublicKey []byte `json:"identity_public_key"`
	SignedPreKey      []byte `json:"signed_pre_key"`
	RegistrationID    int    `json:"registration_id"`
	DisplayName       string `json:"display_name"`
}

type registerResponse struct {
	UserID       string `json:"user_id"`
	SessionToken string `json:"session_token"`
}

func handleRegister(reg *auth.RegistrationService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req registerRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeError(w, http.StatusBadRequest, "invalid request body")
			return
		}

		result, err := reg.Register(r.Context(), auth.RegistrationInput{
			DeviceID:          req.DeviceID,
			IdentityPublicKey: req.IdentityPublicKey,
			SignedPreKey:      req.SignedPreKey,
			RegistrationID:    req.RegistrationID,
			DisplayName:       req.DisplayName,
		})

		switch {
		case errors.Is(err, auth.ErrDeviceKeyMissing):
			writeError(w, http.StatusBadRequest, "device key material is required")
			return
		case err != nil:
			writeError(w, http.StatusInternalServerError, "registration failed")
			return
		}

		writeJSON(w, http.StatusOK, registerResponse{
			UserID:       result.UserID,
			SessionToken: result.SessionToken,
		})
	}
}

type logoutRequest struct {
	SessionToken string `json:"session_token"`
}

func handleLogout(sessions *auth.SessionService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req logoutRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.SessionToken == "" {
			writeError(w, http.StatusBadRequest, "session_token is required")
			return
		}
		if err := sessions.Revoke(r.Context(), req.SessionToken); err != nil {
			writeError(w, http.StatusInternalServerError, "could not revoke session")
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}
}

// ---------------------------------------------------------------------------
// Key exchange handlers
// ---------------------------------------------------------------------------

func handleFetchPreKeyBundle(users *postgres.UserRepository, sessions *auth.SessionService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		// Any authenticated user may fetch another user's bundle
		if _, err := authenticateRequest(r, sessions); err != nil {
			writeError(w, http.StatusUnauthorized, "invalid session")
			return
		}

		userID := r.PathValue("userId")
		deviceID := r.PathValue("deviceId")
		if userID == "" || deviceID == "" {
			writeError(w, http.StatusBadRequest, "user_id and device_id path parameters are required")
			return
		}

		bundle, err := users.FetchPreKeyBundle(r.Context(), userID, deviceID)
		if err != nil {
			writeError(w, http.StatusNotFound, "device not found or no keys available")
			return
		}

		writeJSON(w, http.StatusOK, bundle)
	}
}

func handleUploadPreKeys(users *postgres.UserRepository, sessions *auth.SessionService) http.HandlerFunc {
	type uploadRequest struct {
		Keys map[int][]byte `json:"keys"`
	}

	return func(w http.ResponseWriter, r *http.Request) {
		sess, err := authenticateRequest(r, sessions)
		if err != nil {
			writeError(w, http.StatusUnauthorized, "invalid session")
			return
		}

		var req uploadRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil || len(req.Keys) == 0 {
			writeError(w, http.StatusBadRequest, "keys map is required")
			return
		}

		if err := users.UploadOneTimePreKeys(r.Context(), sess.UserID, sess.DeviceID, req.Keys); err != nil {
			writeError(w, http.StatusInternalServerError, "failed to upload prekeys")
			return
		}

		w.WriteHeader(http.StatusNoContent)
	}
}

func handlePreKeyCount(users *postgres.UserRepository, sessions *auth.SessionService) http.HandlerFunc {
	type countResponse struct {
		Count int `json:"count"`
	}

	return func(w http.ResponseWriter, r *http.Request) {
		sess, err := authenticateRequest(r, sessions)
		if err != nil {
			writeError(w, http.StatusUnauthorized, "invalid session")
			return
		}

		count, err := users.CountOneTimePreKeys(r.Context(), sess.UserID, sess.DeviceID)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "could not count prekeys")
			return
		}

		writeJSON(w, http.StatusOK, countResponse{Count: count})
	}
}

// ---------------------------------------------------------------------------
// Identity / Discovery handler
// ---------------------------------------------------------------------------

type userResponse struct {
	UserID string `json:"user_id"`
}

// handleGetUser lets a client look up a user by their UUID to verify they exist.
// This supports the QR code / short-link flow.
func handleGetUser(users *postgres.UserRepository, sessions *auth.SessionService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if _, err := authenticateRequest(r, sessions); err != nil {
			writeError(w, http.StatusUnauthorized, "invalid session")
			return
		}

		userID := r.PathValue("userId")
		if userID == "" {
			writeError(w, http.StatusBadRequest, "user_id is required")
			return
		}

		id, err := users.FindUserByID(r.Context(), userID)
		if err != nil {
			writeError(w, http.StatusNotFound, "user not found")
			return
		}

		writeJSON(w, http.StatusOK, userResponse{
			UserID: id,
		})
	}
}

// ---------------------------------------------------------------------------
// FCM Token handler
// ---------------------------------------------------------------------------

type updateFCMRequest struct {
	FCMToken string `json:"fcm_token"`
}

func handleUpdateFCMToken(users *postgres.UserRepository, sessions *auth.SessionService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		sess, err := authenticateRequest(r, sessions)
		if err != nil {
			writeError(w, http.StatusUnauthorized, "invalid session")
			return
		}

		var req updateFCMRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.FCMToken == "" {
			writeError(w, http.StatusBadRequest, "fcm_token is required")
			return
		}

		if err := users.UpdateFCMToken(r.Context(), sess.UserID, sess.DeviceID, req.FCMToken); err != nil {
			writeError(w, http.StatusInternalServerError, "could not update fcm token")
			return
		}

		w.WriteHeader(http.StatusNoContent)
	}
}

// ---------------------------------------------------------------------------
// Media Attachments handler
// ---------------------------------------------------------------------------

type uploadURLResponse struct {
	UploadURL   string `json:"upload_url"`
	DownloadURL string `json:"download_url"`
}

func handleGenerateUploadURL(s3Store *attachments.S3Store, sessions *auth.SessionService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if _, err := authenticateRequest(r, sessions); err != nil {
			writeError(w, http.StatusUnauthorized, "invalid session")
			return
		}

		if s3Store == nil {
			writeError(w, http.StatusNotImplemented, "media attachments are not configured on this server")
			return
		}

		uploadURL, downloadURL, err := s3Store.GenerateUploadURL(r.Context())
		if err != nil {
			writeError(w, http.StatusInternalServerError, "failed to generate upload url")
			return
		}

		writeJSON(w, http.StatusOK, uploadURLResponse{
			UploadURL:   uploadURL,
			DownloadURL: downloadURL,
		})
	}
}

// ---------------------------------------------------------------------------
// Response helpers
// ---------------------------------------------------------------------------

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}
