-- keyspace and table for ciphertext blobs awaiting delivery confirmation.
-- This is intentionally separate from Postgres: it holds transient,
-- high-volume, write-heavy data that should never be joined against user
-- metadata, and TTL expiry here is enforced by Cassandra itself as a
-- second independent safety net on top of application-level deletion.

CREATE KEYSPACE IF NOT EXISTS messenger
  WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 3};

USE messenger;

CREATE TABLE IF NOT EXISTS pending_messages (
    recipient_user_id   text,
    recipient_device_id text,
    message_id          timeuuid,
    envelope_type       text,
    sealed_sender_ct     blob,
    message_ct           blob,
    created_at          timestamp,
    PRIMARY KEY ((recipient_user_id, recipient_device_id), message_id)
) WITH CLUSTERING ORDER BY (message_id ASC)
  AND default_time_to_live = 1209600; -- 14 days; app also deletes explicitly on confirmed delivery
