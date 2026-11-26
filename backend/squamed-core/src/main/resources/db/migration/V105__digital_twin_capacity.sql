-- V105: digital twin + capacity intelligence (chunk-07 section 07.03)
-- Materialized operational snapshots over governed state (no new database). The 06.06 ClickHouse
-- marts are not available yet; the twin reads the operational tables directly and records why.
-- Rollback note: additive only - drop schema twin; delete the twin permissions.

CREATE SCHEMA IF NOT EXISTS twin;

CREATE TABLE IF NOT EXISTS twin.capacity_snapshot (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id              UUID NOT NULL,
    generated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    beds_total               INTEGER NOT NULL DEFAULT 0,
    beds_available           INTEGER NOT NULL DEFAULT 0,
    beds_occupied            INTEGER NOT NULL DEFAULT 0,
    ot_rooms_total           INTEGER NOT NULL DEFAULT 0,
    ot_rooms_free            INTEGER NOT NULL DEFAULT 0,
    admissions_active        INTEGER NOT NULL DEFAULT 0,
    expected_discharges_today INTEGER NOT NULL DEFAULT 0,
    diagnostics_pending      INTEGER NOT NULL DEFAULT 0,
    equipment_down           INTEGER NOT NULL DEFAULT 0,
    open_work_orders         INTEGER NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    version                  BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_capacity_snapshot_hospital
    ON twin.capacity_snapshot (hospital_id, generated_at DESC);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'twin', 'read',  'twin', 'Read capacity snapshots and answers'),
    (gen_random_uuid(), 'twin', 'write', 'twin', 'Generate capacity snapshots and reconcile them')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'twin'
WHERE r.name IN ('SYSTEM_ADMIN', 'HOSPITAL_ADMIN')
ON CONFLICT DO NOTHING;
