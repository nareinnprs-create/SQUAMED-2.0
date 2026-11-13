-- V20: Communication schema and seed permissions
-- Module: communication

CREATE SCHEMA IF NOT EXISTS communication;

CREATE TABLE IF NOT EXISTS communication.channel (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID,
    name         VARCHAR(160) NOT NULL,
    type         VARCHAR(30) NOT NULL DEFAULT 'direct',
    description  TEXT,
    owner_id     UUID,
    patient_id   UUID,
    encounter_id UUID,
    is_archived  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS communication.channel_member (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_id            UUID NOT NULL,
    user_id               UUID NOT NULL,
    role                  VARCHAR(20) NOT NULL DEFAULT 'member',
    last_read_at          TIMESTAMPTZ,
    muted_until           TIMESTAMPTZ,
    notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    version               BIGINT NOT NULL DEFAULT 0,
    UNIQUE (channel_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_channel_member_user ON communication.channel_member (user_id);

CREATE TABLE IF NOT EXISTS communication.message (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_id       UUID NOT NULL,
    sender_id        UUID NOT NULL,
    body             TEXT NOT NULL,
    message_type     VARCHAR(20) NOT NULL DEFAULT 'text',
    attachment_count INTEGER NOT NULL DEFAULT 0,
    edited_at        TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_channel_message_channel_created ON communication.message (channel_id, created_at);
CREATE INDEX IF NOT EXISTS idx_channel_message_sender ON communication.message (sender_id);

CREATE TABLE IF NOT EXISTS communication.message_attachment (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id         UUID NOT NULL,
    file_name          VARCHAR(255),
    content_type       VARCHAR(120),
    size_bytes         BIGINT,
    storage_object_key VARCHAR(255),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_channel_attachment_message ON communication.message_attachment (message_id);

CREATE TABLE IF NOT EXISTS communication.message_read (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL,
    user_id    UUID NOT NULL,
    read_at    TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version    BIGINT NOT NULL DEFAULT 0,
    UNIQUE (message_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_channel_read_user ON communication.message_read (user_id);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    ('00000000-0000-0000-0000-00000000f003', 'communication', 'manage', 'channel', 'Create and manage communication channels'),
    ('00000000-0000-0000-0000-00000000f004', 'communication', 'read',   'message', 'Read and send channel messages')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.domain = 'communication'
ON CONFLICT DO NOTHING;