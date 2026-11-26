-- V108: specialty packs installed by configuration alone (chunk-07 section 07.10)
-- A pack is a versioned bundle of CONFIG documents (existing config types) and ROLE grants; installing it
-- for a hospital writes configuration, never code. Pack CONTENTS still need clinical review - this is the
-- installer, plus a reference pack used to prove the mechanism.
-- Rollback note: additive only - drop schema pack; delete the pack permissions.

CREATE SCHEMA IF NOT EXISTS pack;

CREATE TABLE IF NOT EXISTS pack.specialty_pack (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code         VARCHAR(60) NOT NULL,
    name         VARCHAR(150) NOT NULL,
    specialty    VARCHAR(60) NOT NULL,
    description  VARCHAR(500),
    pack_version INTEGER NOT NULL DEFAULT 1,
    status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0,
    UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS pack.pack_component (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pack_id        UUID NOT NULL REFERENCES pack.specialty_pack (id) ON DELETE CASCADE,
    component_type VARCHAR(20) NOT NULL,
    component_key  VARCHAR(150) NOT NULL,
    payload        JSONB NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_pack_component_pack ON pack.pack_component (pack_id);

CREATE TABLE IF NOT EXISTS pack.pack_install (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL,
    pack_id      UUID NOT NULL,
    pack_version INTEGER NOT NULL,
    status       VARCHAR(20) NOT NULL,
    detail       VARCHAR(500),
    installed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, pack_id, pack_version)
);

CREATE INDEX IF NOT EXISTS idx_pack_install_hospital ON pack.pack_install (hospital_id, created_at DESC);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'pack', 'read',    'pack', 'Read specialty packs and install history'),
    (gen_random_uuid(), 'pack', 'manage',  'pack', 'Create and version specialty pack definitions'),
    (gen_random_uuid(), 'pack', 'install', 'pack', 'Install a specialty pack into a hospital by configuration')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'pack'
WHERE r.name IN ('SYSTEM_ADMIN', 'HOSPITAL_ADMIN')
ON CONFLICT DO NOTHING;
