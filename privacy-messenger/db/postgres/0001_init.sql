-- 0001_init.sql
-- Metadata store only. Message ciphertext never lands in Postgres —
-- see db/cassandra for that. Everything here is either an identifier,
-- a public key, or a hash — never plaintext secrets.

CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_key_hash TEXT NOT NULL UNIQUE,       -- SHA256 of the user's public identity key
    display_name    TEXT,                       -- optional, chosen by user
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS devices (
    id                   TEXT NOT NULL,     -- client-generated device id
    user_id              UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    identity_public_key  BYTEA NOT NULL,    -- public only; private key never leaves the device
    signed_pre_key       BYTEA NOT NULL,
    registration_id      INTEGER NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, id)
);

-- One-time prekeys are consumed on use (deleted after a peer fetches one),
-- so this table is expected to shrink; clients must periodically top it up.
CREATE TABLE IF NOT EXISTS one_time_prekeys (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id   TEXT NOT NULL,
    key_id      INTEGER NOT NULL,
    public_key  BYTEA NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, device_id, key_id)
);

CREATE TABLE IF NOT EXISTS sessions (
    token_hash  BYTEA PRIMARY KEY,          -- SHA-256 of the opaque session token
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id   TEXT NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS groups (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mls_group_id BYTEA NOT NULL UNIQUE,     -- opaque MLS group identifier
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS group_members (
    group_id  UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role      TEXT NOT NULL DEFAULT 'member', -- 'member' | 'admin'
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions (expires_at);
