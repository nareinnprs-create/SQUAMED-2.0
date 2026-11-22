-- V77: Admission + bed management (chunk-04 section 04.01)
-- Bed board states (cleaning), isolation + gender rules at allocation, expected-discharge
-- planning, a discharge checklist (pharmacy returns + final bill + signed summary) that gates
-- discharge, and a housekeeping task when the bed is released.
-- Rollback note: additive only - drop ipd.discharge_checklist, drop the added columns, delete
-- the new event-catalog rows are code-side (no DB rows).

ALTER TABLE hospital_config.bed
    ADD COLUMN IF NOT EXISTS isolation_type VARCHAR(10) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS gender_policy VARCHAR(6) NOT NULL DEFAULT 'ANY';

ALTER TABLE ipd.admission
    ADD COLUMN IF NOT EXISTS expected_discharge_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS isolation_type VARCHAR(10) NOT NULL DEFAULT 'NONE';

ALTER TABLE ipd.discharge_summary
    ADD COLUMN IF NOT EXISTS status VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS icd_codes VARCHAR(300),
    ADD COLUMN IF NOT EXISTS signed_by UUID,
    ADD COLUMN IF NOT EXISTS signed_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS ipd.discharge_checklist (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admission_id         UUID NOT NULL UNIQUE,
    pharmacy_return_done BOOLEAN NOT NULL DEFAULT FALSE,
    pharmacy_return_at   TIMESTAMPTZ,
    final_bill_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    completed_by         UUID,
    completed_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_bed_hospital_status ON hospital_config.bed (hospital_id, status);
