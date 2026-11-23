-- V84: IPD billing activation + dietary-lite + discharge med-rec (chunk-04 section 04.08)
-- Room charges by bed-hour, interim bills from the posted ledger, diet orders (allergy-aware)
-- with a per-meal kitchen manifest, and a medication-reconciliation review leg on the discharge
-- checklist (the summary cannot be signed before it).
-- Rollback note: additive only - drop ipd.diet_order and the added checklist column note.

CREATE TABLE IF NOT EXISTS ipd.diet_order (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID NOT NULL,
    admission_id  UUID NOT NULL,
    patient_id    UUID NOT NULL,
    diet_type     VARCHAR(60) NOT NULL,
    restrictions  VARCHAR(400),
    notes         VARCHAR(400),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    ordered_by    UUID,
    ordered_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_diet_order_hospital_active ON ipd.diet_order (hospital_id, is_active);
CREATE INDEX IF NOT EXISTS idx_diet_order_admission ON ipd.diet_order (admission_id);

-- 04.08: the summary cannot be signed before the medication-reconciliation review leg.
ALTER TABLE ipd.discharge_checklist
    ADD COLUMN IF NOT EXISTS med_rec_done BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS med_rec_at TIMESTAMPTZ;
