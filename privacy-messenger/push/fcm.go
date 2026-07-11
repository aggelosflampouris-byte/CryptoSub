package push

import (
	"context"
	"log/slog"
	"privacy-messenger/db/postgres"
)

// FCMService implements relay.PushService to send wake-up pings via
// Firebase Cloud Messaging.
//
// NOTE: This is currently a stub. To fully implement, integrate the
// firebase.google.com/go/v4 SDK or call the FCM HTTP v1 API directly
// using a Service Account credential.
type FCMService struct {
	users  *postgres.UserRepository
	logger *slog.Logger
}

func NewFCMService(users *postgres.UserRepository, logger *slog.Logger) *FCMService {
	return &FCMService{
		users:  users,
		logger: logger,
	}
}

func (s *FCMService) PingDevice(ctx context.Context, userID, deviceID string) error {
	// 1. Fetch the FCM token for this device from Postgres.
	// Since we don't have a FetchFCMToken method yet, we'd add one to users.go:
	// token, err := s.users.GetFCMToken(ctx, userID, deviceID)
	// if err != nil { return err }

	s.logger.Info("FCM Wake-Up Ping triggered", 
		"user_id", userID, 
		"device_id", deviceID,
		"action", "ping")

	// 2. Send the HTTP request to FCM:
	// POST https://fcm.googleapis.com/v1/projects/YOUR_PROJECT_ID/messages:send
	// {
	//   "message": {
	//     "token": token,
	//     "data": { "action": "ping" }
	//   }
	// }
	//
	// Note: Do NOT include `notification: { title, body }` as that would cause
	// the OS to display a system tray notification even if the app is killed.
	// We only want a silent data push to wake the app so it can connect to the
	// WebSocket, download the ciphertext, decrypt it locally, and THEN issue
	// a local notification with the plaintext.

	return nil
}
