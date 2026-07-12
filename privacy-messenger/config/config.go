// Package config loads all runtime configuration from environment variables.
// Nothing here is hardcoded: missing required values fail startup immediately
// rather than silently falling back to an insecure default.
package config

import (
	"fmt"
	"os"
	"strconv"
	"time"
)

type Config struct {
	// Server
	ListenAddr string

	// Postgres (metadata: users, devices, groups — never message content)
	PostgresDSN string

	// Redis (offline ciphertext queue, short TTL)
	RedisAddr     string
	RedisPassword string

	// Cassandra / message store (ciphertext blobs)
	CassandraHosts    []string
	CassandraKeyspace string

	// Object storage for encrypted media blobs
	MediaBucket    string
	MediaRegion    string
	MediaAccessKey string
	MediaSecretKey string

	// Push notifications
	FCMCredentialsPath string
	APNsKeyPath        string
	APNsKeyID          string
	APNsTeamID         string

	// SMS OTP provider (e.g. Twilio) — credentials only, no vendor lock-in here
	SMSProviderAPIKey string
	SMSProviderSecret string

	// How long an offline message may sit in the queue before it's dropped
	OfflineMessageTTL time.Duration

	// How long an auth session token remains valid
	SessionTTL time.Duration
}

// Load reads configuration from environment variables. It returns an error
// (rather than a zero-value default) for any required secret that's missing,
// so the server refuses to start in an insecure or broken state.
func Load() (*Config, error) {
	cfg := &Config{
		ListenAddr:        getEnvOrDefault("LISTEN_ADDR", ":8443"),
		CassandraKeyspace: getEnvOrDefault("CASSANDRA_KEYSPACE", "messenger"),
		MediaRegion:       getEnvOrDefault("MEDIA_REGION", "auto"),
	}

	var err error
	if cfg.PostgresDSN, err = requireEnv("POSTGRES_DSN"); err != nil {
		return nil, err
	}
	if cfg.RedisAddr, err = requireEnv("REDIS_ADDR"); err != nil {
		return nil, err
	}
	cfg.RedisPassword = os.Getenv("REDIS_PASSWORD") // optional in dev, required in prod via infra config

	cassandraHosts := os.Getenv("CASSANDRA_HOSTS")
	if cassandraHosts == "" {
		return nil, fmt.Errorf("CASSANDRA_HOSTS is required (comma-separated list)")
	}
	cfg.CassandraHosts = splitCSV(cassandraHosts)

	cfg.MediaBucket = os.Getenv("MEDIA_BUCKET")
	cfg.MediaAccessKey = os.Getenv("MEDIA_ACCESS_KEY")
	cfg.MediaSecretKey = os.Getenv("MEDIA_SECRET_KEY")

	if cfg.SMSProviderAPIKey, err = requireEnv("SMS_PROVIDER_API_KEY"); err != nil {
		return nil, err
	}
	if cfg.SMSProviderSecret, err = requireEnv("SMS_PROVIDER_SECRET"); err != nil {
		return nil, err
	}

	// Push credentials are optional at boot (server can run without them in dev),
	// but are required for a real deployment — validated at the call site instead
	// of here, so local development isn't blocked.
	cfg.FCMCredentialsPath = os.Getenv("FCM_CREDENTIALS_PATH")
	cfg.APNsKeyPath = os.Getenv("APNS_KEY_PATH")
	cfg.APNsKeyID = os.Getenv("APNS_KEY_ID")
	cfg.APNsTeamID = os.Getenv("APNS_TEAM_ID")

	ttlSeconds, err := getEnvIntOrDefault("OFFLINE_MESSAGE_TTL_SECONDS", 60*60*24*14) // 14 days default
	if err != nil {
		return nil, err
	}
	cfg.OfflineMessageTTL = time.Duration(ttlSeconds) * time.Second

	sessionSeconds, err := getEnvIntOrDefault("SESSION_TTL_SECONDS", 60*60*24*30) // 30 days default
	if err != nil {
		return nil, err
	}
	cfg.SessionTTL = time.Duration(sessionSeconds) * time.Second

	return cfg, nil
}

func requireEnv(key string) (string, error) {
	v := os.Getenv(key)
	if v == "" {
		return "", fmt.Errorf("required environment variable %s is not set", key)
	}
	return v, nil
}

func getEnvOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func getEnvIntOrDefault(key string, fallback int) (int, error) {
	v := os.Getenv(key)
	if v == "" {
		return fallback, nil
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return 0, fmt.Errorf("environment variable %s must be an integer: %w", key, err)
	}
	return n, nil
}

func splitCSV(s string) []string {
	var out []string
	start := 0
	for i := 0; i <= len(s); i++ {
		if i == len(s) || s[i] == ',' {
			if i > start {
				out = append(out, s[start:i])
			}
			start = i + 1
		}
	}
	return out
}
