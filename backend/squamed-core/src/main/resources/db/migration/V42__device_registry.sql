-- 00.03 hardening: device registry (chunk-00 item 4 "device sessions").
-- Devices are server-side identities derived from the login User-Agent fingerprint;
-- sessions bind to a device so auditors and users can see and revoke every device.
-- Rollback: DROP TABLE IF EXISTS identity.device CASCADE; ALTER TABLE identity.session DROP COLUMN IF EXISTS device_id;

CREATE TABLE IF NOT EXISTS identity.device (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES identity.user(id) ON DELETE CASCADE,
    fingerprint   VARCHAR(64) NOT NULL,
    label         VARCHAR(255),
    hospital_id   UUID,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    created_by    UUID,
    updated_by    UUID,
    revoked_at    TIMESTAMPTZ,
    CONSTRAINT uq_identity_device_user_fingerprint UNIQUE (user_id, fingerprint)
);

CREATE INDEX IF NOT EXISTS idx_identity_device_user ON identity.device(user_id);

ALTER TABLE identity.session
    ADD COLUMN device_id UUID REFERENCES identity.device(id);

CREATE INDEX IF NOT EXISTS idx_identity_session_device ON identity.session(device_id);
