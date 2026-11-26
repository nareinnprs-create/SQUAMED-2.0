-- V104: migration studio + benchmarking + ESG (chunk-07 section 07.10, non-pack slice)
-- Specialty-pack library content review remains external (22 packs, human-paced).
-- Rollback note: additive only - drop schema migration, benchmark, esg; delete the permissions.

CREATE SCHEMA IF NOT EXISTS migration;
CREATE SCHEMA IF NOT EXISTS benchmark;
CREATE SCHEMA IF NOT EXISTS esg;

CREATE TABLE IF NOT EXISTS migration.job (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID NOT NULL,
    source_type   VARCHAR(20) NOT NULL,
    entity_type   VARCHAR(30) NOT NULL DEFAULT 'PATIENT',
    status        VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    total_rows    INTEGER NOT NULL DEFAULT 0,
    valid_rows    INTEGER NOT NULL DEFAULT 0,
    error_rows    INTEGER NOT NULL DEFAULT 0,
    imported_at   TIMESTAMPTZ,
    rolled_back_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_migration_job_hospital ON migration.job (hospital_id, created_at);

CREATE TABLE IF NOT EXISTS migration.staged_row (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID NOT NULL,
    job_id        UUID NOT NULL REFERENCES migration.job(id) ON DELETE CASCADE,
    row_no        INTEGER NOT NULL,
    payload       JSONB NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'VALID',
    error_message VARCHAR(400),
    entity_id     UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0,
    UNIQUE (job_id, row_no)
);

CREATE INDEX IF NOT EXISTS idx_staged_row_job ON migration.staged_row (job_id);

CREATE TABLE IF NOT EXISTS benchmark.participation (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id)
);

CREATE TABLE IF NOT EXISTS esg.metric (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    period      VARCHAR(7) NOT NULL,
    metric_type VARCHAR(20) NOT NULL,
    value       NUMERIC(14,2) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, period, metric_type)
);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'migration', 'read',  'migration', 'Read migration jobs, staged rows and reconciliation'),
    (gen_random_uuid(), 'migration', 'write', 'migration', 'Run and roll back migration jobs'),
    (gen_random_uuid(), 'benchmark', 'read',  'benchmark', 'Read anonymized peer benchmarks'),
    (gen_random_uuid(), 'benchmark', 'write', 'benchmark', 'Manage benchmark participation'),
    (gen_random_uuid(), 'esg', 'read',  'esg', 'Read ESG metrics and summaries'),
    (gen_random_uuid(), 'esg', 'write', 'esg', 'Record ESG metrics')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain IN ('migration', 'benchmark', 'esg')
WHERE r.name IN ('SYSTEM_ADMIN', 'HOSPITAL_ADMIN')
ON CONFLICT DO NOTHING;
