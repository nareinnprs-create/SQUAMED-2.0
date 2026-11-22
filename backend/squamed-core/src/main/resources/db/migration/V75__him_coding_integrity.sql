-- V75: HIM coding + revenue integrity (chunk-03 section 03.05)
-- Coding worklist on completed encounters, coder queries that open a task for the clinician,
-- coding audit sampling, and integrity findings (unbilled charges, duplicates, uncoded
-- completed encounters) with triage + dedupe so daily scans never duplicate a finding.
-- Rollback note: additive only - drop schemas him and integrity cascade, delete the
-- him/integrity permission rows and their grants to revert.

CREATE SCHEMA IF NOT EXISTS him;
CREATE SCHEMA IF NOT EXISTS integrity;

CREATE TABLE IF NOT EXISTS him.coding_assignment (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL,
    encounter_id UUID NOT NULL UNIQUE,
    icd_codes    VARCHAR(300) NOT NULL,
    snomed_codes VARCHAR(300),
    coder_id     UUID,
    coded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_coding_assignment_hospital ON him.coding_assignment (hospital_id, coded_at);

CREATE TABLE IF NOT EXISTS him.coder_query (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL,
    encounter_id UUID NOT NULL,
    question     TEXT NOT NULL,
    status       VARCHAR(12) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ANSWERED', 'CLOSED')),
    task_id      UUID,
    raised_by    UUID,
    raised_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    answer       TEXT,
    answered_by  UUID,
    answered_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_coder_query_hospital_status ON him.coder_query (hospital_id, status);

CREATE TABLE IF NOT EXISTS integrity.finding (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL,
    rule_code    VARCHAR(40) NOT NULL,
    severity     VARCHAR(10) NOT NULL DEFAULT 'warning' CHECK (severity IN ('warning', 'high')),
    subject_type VARCHAR(30) NOT NULL,
    subject_id   UUID,
    detail       VARCHAR(400),
    status       VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'IGNORED')),
    dedupe_key   VARCHAR(160) NOT NULL UNIQUE,
    detected_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_by  UUID,
    resolved_at  TIMESTAMPTZ,
    resolution   VARCHAR(400),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_integrity_finding_hospital_status ON integrity.finding (hospital_id, status);

-- Roles/permissions: medical coder codes and queries; billing supervisor triages integrity.
INSERT INTO identity.role (name, display_name, description, is_system)
VALUES ('MEDICAL_CODER', 'Medical Coder', 'ICD-10 coding worklist and clinician queries', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'him', 'read',   'coding',    'Read the coding worklist and assignments'),
    (gen_random_uuid(), 'him', 'code',   'encounter', 'Assign ICD-10/SNOMED codes to encounters'),
    (gen_random_uuid(), 'him', 'query',  'clinician', 'Raise coder queries to clinicians'),
    (gen_random_uuid(), 'him', 'answer', 'query',     'Answer coder queries'),
    (gen_random_uuid(), 'him', 'audit',  'coding',    'Sample coding assignments for audit'),
    (gen_random_uuid(), 'integrity', 'scan',   'billing', 'Run revenue integrity scans'),
    (gen_random_uuid(), 'integrity', 'triage', 'finding', 'Triage integrity findings')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'him' AND p.action IN ('read', 'code', 'query', 'answer', 'audit')
WHERE r.name = 'MEDICAL_CODER'
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'him' AND p.action = 'answer'
WHERE r.name = 'DOCTOR'
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'integrity'
WHERE r.name IN ('BILLING_SUPERVISOR', 'SYSTEM_ADMIN')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'him'
WHERE r.name = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;
