-- V90: multi-hospital group (chunk-06 section 06.01)
-- Group entity + hospital membership, group-level config defaults (hospital overrides stay in
-- hospital_config.config_document), consent-gated cross-hospital record sharing, group referrals.
-- Rollback note: additive only - drop schema grp and the hospital.group_id column.

CREATE SCHEMA IF NOT EXISTS grp;

CREATE TABLE IF NOT EXISTS grp.hospital_group (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50) NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE hospital_config.hospital
    ADD COLUMN IF NOT EXISTS group_id UUID;

CREATE INDEX IF NOT EXISTS idx_hospital_group ON hospital_config.hospital (group_id);

CREATE TABLE IF NOT EXISTS grp.group_config_document (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id       UUID NOT NULL,
    type           VARCHAR(50) NOT NULL,
    doc_version    INTEGER NOT NULL,
    content        JSONB NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    UNIQUE (group_id, type, doc_version)
);

CREATE TABLE IF NOT EXISTS grp.cross_hospital_share (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID NOT NULL,
    patient_id        UUID NOT NULL,
    from_hospital_id  UUID NOT NULL,
    to_hospital_id    UUID NOT NULL,
    consent_id        UUID NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    revoked_at        TIMESTAMPTZ,
    revoked_by        UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0,
    UNIQUE (group_id, patient_id, to_hospital_id)
);

CREATE INDEX IF NOT EXISTS idx_cross_share_patient ON grp.cross_hospital_share (group_id, patient_id, status);

CREATE TABLE IF NOT EXISTS grp.referral (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID NOT NULL,
    patient_id        UUID NOT NULL,
    from_hospital_id  UUID NOT NULL,
    to_hospital_id    UUID NOT NULL,
    reason            VARCHAR(400) NOT NULL,
    notes             VARCHAR(600),
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_by        UUID,
    decided_by        UUID,
    decided_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_group_referral_inbox ON grp.referral (to_hospital_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_group_referral_sent ON grp.referral (from_hospital_id, status, created_at DESC);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'group', 'read',    'group',    'Read group hospitals, directory and inherited config'),
    (gen_random_uuid(), 'group', 'manage',  'group',    'Create groups and attach hospitals'),
    (gen_random_uuid(), 'group', 'share',   'record',   'Share a patient record across group hospitals with a consent artifact'),
    (gen_random_uuid(), 'group', 'create',  'referral', 'Create cross-hospital referrals'),
    (gen_random_uuid(), 'group', 'decide',  'referral', 'Accept/complete/cancel cross-hospital referrals')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role (name, display_name, description, is_system)
VALUES ('GROUP_ADMIN', 'Group Administrator', 'Manages a multi-hospital group, sharing and referrals', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'group'
WHERE r.name IN ('SYSTEM_ADMIN', 'GROUP_ADMIN')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role (name, display_name, description, is_system)
VALUES ('GROUP_AUDITOR', 'Group Auditor', 'Cross-hospital read access with consent checks', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'group' AND p.action = 'read'
WHERE r.name = 'GROUP_AUDITOR'
ON CONFLICT DO NOTHING;
