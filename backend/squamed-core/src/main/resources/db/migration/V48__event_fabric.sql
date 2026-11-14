-- V48: Event fabric (00.09) — transactional outbox, dispatcher cursors, integration error queue.
-- Flow per chunk-00 item 8: caller transaction writes events.event_outbox (atomic with the state
-- change), the dispatcher polls it into Kafka + Redis pub/sub, and the realtime bridge forwards to
-- the per-user-filtered WebSocket gateway. FAILED (dead-letter) rows are replayable via the admin API.
-- Rollback: DROP SCHEMA events CASCADE;
--           DELETE FROM identity.role_permission WHERE permission_id IN (SELECT id FROM identity.permission WHERE domain='events' AND action='admin' AND resource IS NULL);
--           DELETE FROM identity.permission WHERE domain='events' AND action='admin' AND resource IS NULL;

CREATE SCHEMA IF NOT EXISTS events;

CREATE TABLE IF NOT EXISTS events.event_outbox (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id         UUID NOT NULL,
    event_type       VARCHAR(150) NOT NULL,
    envelope_version INT NOT NULL DEFAULT 1,
    occurred_at      TIMESTAMPTZ NOT NULL,
    source           VARCHAR(100) NOT NULL,
    entity_type      VARCHAR(100) NOT NULL,
    entity_id        UUID,
    hospital_id      UUID REFERENCES hospital_config.hospital(id),
    payload          JSONB NOT NULL DEFAULT '{}'::jsonb,
    recipient_ids    JSONB NOT NULL DEFAULT '[]'::jsonb,
    actor_type       VARCHAR(20) NOT NULL DEFAULT 'system',
    actor_id         UUID,
    correlation_id   VARCHAR(100),
    causation_id     UUID,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts         INT NOT NULL DEFAULT 0,
    max_attempts     INT NOT NULL DEFAULT 8,
    next_attempt_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error       TEXT,
    dispatched_at    TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_event_outbox_event_id UNIQUE (event_id),
    CONSTRAINT ck_event_outbox_status CHECK (status IN ('PENDING', 'DISPATCHING', 'SENT', 'FAILED')),
    CONSTRAINT ck_event_outbox_attempts CHECK (attempts >= 0 AND max_attempts >= 1)
);

CREATE INDEX IF NOT EXISTS idx_event_outbox_dispatch ON events.event_outbox (status, next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_event_outbox_type ON events.event_outbox (event_type, occurred_at);

CREATE TABLE IF NOT EXISTS events.event_cursor (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consumer         VARCHAR(100) NOT NULL,
    last_event_id    UUID,
    last_event_type  VARCHAR(150),
    last_occurred_at TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_event_cursor_consumer UNIQUE (consumer)
);

CREATE TABLE IF NOT EXISTS events.integration_error (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID REFERENCES hospital_config.hospital(id),
    integration       VARCHAR(50) NOT NULL,
    direction         VARCHAR(20) NOT NULL DEFAULT 'INBOUND',
    message_type      VARCHAR(50),
    payload           TEXT,
    error             TEXT NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    replay_count      INT NOT NULL DEFAULT 0,
    replayed_at       TIMESTAMPTZ,
    last_replay_error TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_integration_error_status CHECK (status IN ('OPEN', 'REPLAYED', 'RESOLVED'))
);

CREATE INDEX IF NOT EXISTS idx_integration_error_status ON events.integration_error (integration, status);

-- Event-fabric operations (outbox inspection, replay, DLQ, integration error queue): platform admins.
INSERT INTO identity.permission (domain, action, resource, description)
VALUES ('events', 'admin', NULL, 'Event fabric operations: outbox inspection, replay, DLQ, integration error queue')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM identity.role r
JOIN identity.permission p ON p.domain = 'events' AND p.action = 'admin' AND p.resource IS NULL
WHERE r.name = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;
