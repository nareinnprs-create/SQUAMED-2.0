-- V96: full ED + trauma (chunk-07 section 07.05)
-- Configurable triage scales (ESI/ATS), resus bays, trauma-team activation and mass-casualty mode.
-- Rollback note: additive only - drop the ed tables and the added encounter columns.

CREATE TABLE IF NOT EXISTS ops.ed_triage_scale (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    code        VARCHAR(20) NOT NULL,
    name        VARCHAR(80) NOT NULL,
    max_level   INTEGER NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, code)
);

CREATE TABLE IF NOT EXISTS ops.ed_bay (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    code        VARCHAR(30) NOT NULL,
    name        VARCHAR(80) NOT NULL,
    type        VARCHAR(20) NOT NULL DEFAULT 'acute',
    status      VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, code)
);

CREATE TABLE IF NOT EXISTS ops.mass_casualty_event (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    note        VARCHAR(400),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    declared_by UUID,
    declared_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    stood_down_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE ops.ed_encounter
    ADD COLUMN IF NOT EXISTS triage_scale_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS bay_id UUID,
    ADD COLUMN IF NOT EXISTS trauma_activated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS trauma_activated_at TIMESTAMPTZ;
