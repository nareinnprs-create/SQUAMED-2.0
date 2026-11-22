-- V78: eMAR + nursing core (chunk-04 section 04.02)
-- Scheduled doses generated from an active prescription, barcode-verified administration
-- (patient + drug hard block), PRN doses with a reason, omission codes with clinician
-- notification, and high-alert double-check.
-- Rollback note: additive only - drop the added columns/index; no permission rows added
-- (nursing perms already exist).

ALTER TABLE nursing.medication_administration_record
    ADD COLUMN IF NOT EXISTS prescription_id UUID,
    ADD COLUMN IF NOT EXISTS drug_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_prn BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS high_alert BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS omission_code VARCHAR(40),
    ADD COLUMN IF NOT EXISTS second_checker_id UUID,
    ADD COLUMN IF NOT EXISTS barcode_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS prn_reason VARCHAR(400);

CREATE INDEX IF NOT EXISTS idx_mar_patient_scheduled ON nursing.medication_administration_record (patient_id, scheduled_at);
