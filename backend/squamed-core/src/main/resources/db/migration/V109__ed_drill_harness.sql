-- V109: ED mass-casualty drill harness (chunk-07 section 07.05)
-- A drill is a scripted exercise over the real ED endpoints (declare -> register casualties -> stand down)
-- with the timings kept as evidence. No synthetic patient data beyond the drill's own ED encounters.
-- Rollback note: additive only - drop table ops.ed_drill.

CREATE TABLE IF NOT EXISTS ops.ed_drill (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id        UUID NOT NULL,
    name               VARCHAR(150) NOT NULL,
    planned_casualties INTEGER NOT NULL,
    created_casualties INTEGER NOT NULL DEFAULT 0,
    triage_distribution JSONB,
    status             VARCHAR(20) NOT NULL,
    detail             VARCHAR(500),
    declared_at        TIMESTAMPTZ,
    stood_down_at      TIMESTAMPTZ,
    elapsed_ms         BIGINT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ed_drill_hospital ON ops.ed_drill (hospital_id, created_at DESC);
