-- 01.12 dual-review hardening (chunk-01 FINAL §7): structured allergy severity codes,
-- note amendment chain, soft diagnosis removal.
-- Rollback: ALTER TABLE patient.allergy DROP CONSTRAINT ck_allergy_severity;
--           ALTER TABLE clinical.diagnosis DROP COLUMN is_active;
--           ALTER TABLE clinical.clinical_note DROP COLUMN amends_note_id;

-- SAFETY L50: severity becomes a closed code set the Rx gate consults. Legacy free strings
-- normalize; unknown values fail safe to 'warn' (typed reason required, never a silent pass).
UPDATE patient.allergy
SET severity = CASE lower(trim(severity))
    WHEN 'block'             THEN 'block'
    WHEN 'anaphylaxis'       THEN 'block'
    WHEN 'critical'          THEN 'block'
    WHEN 'high'              THEN 'block'
    WHEN 'life-threatening'  THEN 'block'
    WHEN 'life_threatening'  THEN 'block'
    WHEN 'mild'              THEN 'mild'
    WHEN 'low'               THEN 'mild'
    WHEN 'minor'             THEN 'mild'
    WHEN 'none'              THEN 'none'
    ELSE 'warn'
END;

ALTER TABLE patient.allergy
    ADD CONSTRAINT ck_allergy_severity CHECK (severity IN ('block', 'warn', 'mild', 'none'));

-- SAFETY L51: amendments are new note rows linked to the original; the legal record is never overwritten.
ALTER TABLE clinical.clinical_note
    ADD COLUMN IF NOT EXISTS amends_note_id UUID REFERENCES clinical.clinical_note(id);

-- Dual review: diagnosis removal was an unguarded hard delete; removals now deactivate the row.
ALTER TABLE clinical.diagnosis
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
