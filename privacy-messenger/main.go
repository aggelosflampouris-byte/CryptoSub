// Command server is the entry point for the message relay backend.
// It never handles plaintext: every store it wires up (Postgres, Redis,
// Cassandra) holds only ciphertext, hashes, or public key material.
package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gocql/gocql"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"

	"privacy-messenger/attachments"
	"privacy-messenger/auth"
	"privacy-messenger/config"
	"privacy-messenger/db/cassandra"
	"privacy-messenger/db/postgres"
	"privacy-messenger/gateway"
	"privacy-messenger/push"
	"privacy-messenger/queue"
	"privacy-messenger/relay"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))

	if err := run(logger); err != nil {
		logger.Error("server exited with error", "error", err)
		os.Exit(1)
	}
}

func run(logger *slog.Logger) error {
	cfg, err := config.Load()
	if err != nil {
		return err
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	// --- Postgres: users, devices, prekeys, sessions, OTP codes ---
	pgPool, err := pgxpool.New(ctx, cfg.PostgresDSN)
	if err != nil {
		return errors.New("connecting to postgres: " + err.Error())
	}
	defer pgPool.Close()

	sessionRepo := postgres.NewSessionRepository(pgPool)
	userRepo := postgres.NewUserRepository(pgPool)

	// --- Redis: fast offline-delivery queue ---
	redisClient := redis.NewClient(&redis.Options{
		Addr:     cfg.RedisAddr,
		Password: cfg.RedisPassword,
	})
	defer redisClient.Close()
	if err := redisClient.Ping(ctx).Err(); err != nil {
		return errors.New("connecting to redis: " + err.Error())
	}
	offlineQueue := queue.NewOfflineQueue(redisClient, cfg.OfflineMessageTTL)

	// --- Cassandra: durable ciphertext backstop ---
	cassandraSession, err := cassandra.NewSession(cfg.CassandraHosts, cfg.CassandraKeyspace)
	if err != nil {
		return errors.New("connecting to cassandra: " + err.Error())
	}
	defer cassandraSession.Close()
	messageStore := cassandra.NewMessageStore(cassandraSession, cfg.OfflineMessageTTL)

	// --- Auth services ---
	sessionService := auth.NewSessionService(sessionRepo, cfg.SessionTTL)
	registrationService := auth.NewRegistrationService(userRepo, sessionService)

	// --- ACK handler: confirms delivery and deletes from Cassandra ---
	ackHandler := func(ctx context.Context, userID, deviceID, messageID string) error {
		parsedID, err := gocql.ParseUUID(messageID)
		if err != nil {
			return fmt.Errorf("invalid message id in ack: %w", err)
		}
		return messageStore.Delete(ctx, userID, deviceID, parsedID)
	}

	// --- Relay + gateway wiring ---
	var router *relay.Router

	gatewayServer := gateway.NewServer(
		sessionAdapter{sessionService},
		func(ctx context.Context, envelope *relay.Envelope) error {
			messageID, err := messageStore.Save(ctx, envelope)
			if err != nil {
				return err
			}
			envelope.MessageID = messageID.String()
			return router.Route(ctx, envelope)
		},
		ackHandler,
		offlineQueue.Flush,
		logger,
	)

	fcmService := push.NewFCMService(userRepo, logger)
	router = relay.NewRouter(gatewayServer.Registry(), offlineQueue, fcmService)

	// --- S3 Attachment Store ---
	var s3Store *attachments.S3Store
	if cfg.MediaBucket != "" && cfg.MediaAccessKey != "" {
		var s3Err error
		s3Store, s3Err = attachments.NewS3Store(ctx, cfg.MediaRegion, cfg.MediaBucket, cfg.MediaAccessKey, cfg.MediaSecretKey)
		if s3Err != nil {
			logger.Warn("Failed to initialize S3Store, media uploads will fail", "error", s3Err)
		}
	}

	mux := http.NewServeMux()
	mux.Handle("/v1/ws", gatewayServer)
	registerHTTPRoutes(mux, registrationService, sessionService, userRepo, s3Store)

	httpServer := &http.Server{
		Addr:         cfg.ListenAddr,
		Handler:      mux,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
	}

	go func() {
		<-ctx.Done()
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		logger.Info("shutting down")
		_ = httpServer.Shutdown(shutdownCtx)
	}()

	logger.Info("server listening", "addr", cfg.ListenAddr)
	if err := httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		return err
	}
	return nil
}

// sessionAdapter adapts auth.SessionService to gateway.SessionValidator's
// return type (gateway.Session, not auth.Session), keeping the two packages
// decoupled from each other's concrete types.
type sessionAdapter struct {
	svc *auth.SessionService
}

func (a sessionAdapter) Validate(ctx context.Context, token string) (*gateway.Session, error) {
	sess, err := a.svc.Validate(ctx, token)
	if err != nil {
		return nil, err
	}
	return &gateway.Session{UserID: sess.UserID, DeviceID: sess.DeviceID}, nil
}
