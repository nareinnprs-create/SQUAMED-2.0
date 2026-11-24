-- V95: resilience + retention + sandbox (chunk-06 section 06.08)
-- Degraded-mode state + offline write whitelist, store-and-forward queue + replay ledger,
-- retention policies / legal holds / erasure requests, and the sandbox hospital flag.
-- Rollback note: additive only - drop schema resilience, admin tables and the hospital.sandbox column.

CREATE SCHEMA IF NOT EXISTS resilience;

ALTER TABLE hospital_config.hospital
    ADD COLUMN IF NOT EXISTS sandbox BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS resilience.degraded_state (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL UNIQUE,
    active       BOOLEAN NOT NULL DEFAULT FALSE,
    reason       VARCHAR(300),
    activated_by UUID,
    activated_at TIMESTAMPTZ,
    released_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS resilience.queued_write (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    module          VARCHAR(40) NOT NULL,
    operation       VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    append_only     BOOLEAN NOT NULL DEFAULT FALSE,
    occurred_at     TIMESTAMPTZ NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    resolution      VARCHAR(40),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_queued_write_status ON resilience.queued_write (hospital_id, status);

CREATE TABLE IF NOT EXISTS resilience.replayed_write (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    module          VARCHAR(40) NOT NULL,
    operation       VARCHAR(40) NOT NULL,
    applied_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, idempotency_key)
);

CREATE SCHEMA IF NOT EXISTS governance;

CREATE TABLE IF NOT EXISTS governance.retention_policy (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type    VARCHAR(60) NOT NULL UNIQUE,
    retention_days INTEGER NOT NULL,
    legal_basis    VARCHAR(200) NOT NULL,
    clinical       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS governance.legal_hold (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_type VARCHAR(60) NOT NULL,
    subject_id   UUID NOT NULL,
    reason       VARCHAR(400) NOT NULL,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    released_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_legal_hold_subject ON governance.legal_hold (subject_type, subject_id, active);

CREATE TABLE IF NOT EXISTS governance.erasure_request (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_type VARCHAR(60) NOT NULL,
    subject_id   UUID NOT NULL,
    reason       VARCHAR(400) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    resolution   VARCHAR(400),
    requested_by UUID,
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'resilience', 'read',  'degraded', 'Read degraded-mode state, queue and replay results'),
    (gen_random_uuid(), 'resilience', 'write', 'degraded', 'Activate/deactivate degraded mode and replay queued writes'),
    (gen_random_uuid(), 'governance', 'read',  'retention', 'Read retention policies, legal holds and erasure requests'),
    (gen_random_uuid(), 'governance', 'write', 'retention', 'Manage retention, legal holds, erasure and sandbox seeding')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain IN ('resilience', 'governance')
WHERE r.name = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;
