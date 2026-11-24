-- V97: QMS + IPC + legal/risk (chunk-07 section 07.08)
-- Quality indicators, CAPA, audit program, risk register, infection surveillance, outbreaks,
-- hand-hygiene compliance and litigation holds (evidence preservation).
-- Rollback note: additive only - drop schema qms.

CREATE SCHEMA IF NOT EXISTS qms;

CREATE TABLE IF NOT EXISTS qms.indicator (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    code        VARCHAR(40) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    target      NUMERIC(12,2) NOT NULL DEFAULT 0,
    actual      NUMERIC(12,2) NOT NULL DEFAULT 0,
    period      VARCHAR(20) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ON_TRACK',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, code, period)
);

CREATE TABLE IF NOT EXISTS qms.capa (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    code        VARCHAR(40) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    root_cause  VARCHAR(600),
    action      VARCHAR(600) NOT NULL,
    due_on      DATE,
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    closed_at   TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, code)
);

CREATE TABLE IF NOT EXISTS qms.audit (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL,
    title        VARCHAR(200) NOT NULL,
    scheduled_on DATE NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    findings     VARCHAR(800),
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS qms.risk (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    title       VARCHAR(200) NOT NULL,
    likelihood  INTEGER NOT NULL,
    impact      INTEGER NOT NULL,
    score       INTEGER NOT NULL,
    owner       VARCHAR(120),
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS qms.infection_case (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID NOT NULL,
    patient_id    UUID NOT NULL,
    infection_type VARCHAR(30) NOT NULL,
    organism      VARCHAR(120),
    identified_on DATE NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    resolved_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS qms.outbreak (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    code        VARCHAR(40) NOT NULL,
    pathogen    VARCHAR(120) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    declared_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at   TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, code)
);

CREATE TABLE IF NOT EXISTS qms.hand_hygiene (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL,
    ward_id      UUID,
    period       VARCHAR(20) NOT NULL,
    opportunities INTEGER NOT NULL,
    compliant    INTEGER NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, ward_id, period)
);

CREATE TABLE IF NOT EXISTS qms.litigation_hold (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL,
    case_ref     VARCHAR(80) NOT NULL,
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

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'qms', 'read',  'qms', 'Read QMS indicators, CAPA, audits, risk, IPC and legal holds'),
    (gen_random_uuid(), 'qms', 'write', 'qms', 'Manage QMS, IPC surveillance and legal holds')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role (name, display_name, description, is_system)
VALUES ('QMS_OFFICER', 'QMS Officer', 'Quality indicators, CAPA, audits, IPC surveillance and risk', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'qms'
WHERE r.name IN ('SYSTEM_ADMIN', 'HOSPITAL_ADMIN', 'QMS_OFFICER')
ON CONFLICT DO NOTHING;
