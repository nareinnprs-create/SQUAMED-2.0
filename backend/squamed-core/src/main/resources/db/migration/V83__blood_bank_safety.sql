-- V83: Blood bank safety (chunk-04 section 04.07)
-- Donor deferral rules + donation interval, screening status per unit (ttI/NAT hand-off), and the
-- bedside two-identifier check recorded on issue. Traceability is assembled from the existing
-- donor -> unit -> grouping -> crossmatch -> reservation -> issue -> transfusion chain.
-- Rollback note: additive only - drop the added columns.

ALTER TABLE bloodbank.blood_donor
    ADD COLUMN IF NOT EXISTS deferral_reason VARCHAR(400),
    ADD COLUMN IF NOT EXISTS deferred_until TIMESTAMPTZ;

ALTER TABLE bloodbank.blood_unit
    ADD COLUMN IF NOT EXISTS screening_status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS tti_result VARCHAR(20),
    ADD COLUMN IF NOT EXISTS nat_result VARCHAR(20);

ALTER TABLE bloodbank.blood_issue
    ADD COLUMN IF NOT EXISTS patient_barcode_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS second_identifier_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_blood_unit_screening ON bloodbank.blood_unit (hospital_id, screening_status);
