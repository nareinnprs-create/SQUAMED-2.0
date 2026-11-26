-- V106: BI marts metadata + role command centres (chunk-06 section 06.06)
-- ClickHouse holds the event mart and KPI snapshots; Postgres keeps the sync cursor.
-- Rollback note: additive only - drop schema bi; delete the bi permissions and role grants.

CREATE SCHEMA IF NOT EXISTS bi;

CREATE TABLE IF NOT EXISTS bi.mart_sync (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL,
    last_event_at  TIMESTAMPTZ,
    last_synced_at TIMESTAMPTZ,
    events_synced  BIGINT NOT NULL DEFAULT 0,
    status         VARCHAR(20) NOT NULL DEFAULT 'NEVER',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id)
);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'bi', 'read',  'bi', 'Read KPI dictionary, command centres and exports'),
    (gen_random_uuid(), 'bi', 'write', 'bi', 'Run BI event/KPI sync')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role (name, display_name, description, is_system)
VALUES
    ('CEO', 'Chief Executive Officer', 'Read-only executive command centre', TRUE),
    ('COO', 'Chief Operating Officer', 'Read-only operations command centre', TRUE),
    ('CFO', 'Chief Financial Officer', 'Read-only finance command centre', TRUE),
    ('CMO', 'Chief Medical Officer', 'Read-only clinical command centre', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'bi'
WHERE r.name IN ('SYSTEM_ADMIN', 'HOSPITAL_ADMIN')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'bi' AND p.action = 'read'
WHERE r.name IN ('CEO', 'COO', 'CFO', 'CMO')
ON CONFLICT DO NOTHING;
