-- Cassandra schema for the Privacy Messenger backend.
-- This file is executed automatically on first startup.

CREATE KEYSPACE IF NOT EXISTS messenger
    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS messenger.pending_messages (
    recipient_user_id  text,
    recipient_device_id text,
    message_id         timeuuid,
    envelope_type      text,
    sealed_sender_ct   blob,
    message_ct         blob,
    created_at         timestamp,
    PRIMARY KEY ((recipient_user_id, recipient_device_id), message_id)
) WITH CLUSTERING ORDER BY (message_id ASC)
  AND default_time_to_live = 2592000;   -- 30-day TTL safety net
