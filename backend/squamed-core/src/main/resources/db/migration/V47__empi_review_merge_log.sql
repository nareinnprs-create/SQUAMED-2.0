-- V47: EMPI review queue + versioned reversible merge log (00.08).
-- Hospital-scoped per ADR-13 stage 1. Two-person merge rule: a merge intent is
-- submitted by a holder of patient:merge:patient and executed only after a
-- SECOND distinct user holding patient:merge:cosign approves the review row
-- (auditor co-sign: READ_ONLY_VIEWER holds the cosign permission).
-- patient_merge_log.snapshot (jsonb) records the duplicate's moved child row ids
-- so unmerge can re-point every row back — merge reversal restores identical
-- record state; the duplicate row is archived (merged_into, records_status
-- 'merged'), never deleted.
-- Rollback: ALTER TABLE patient.patient_record DROP COLUMN merged_into, DROP COLUMN merged_at;
--           DROP TABLE patient.patient_merge_log CASCADE;
--           DROP TABLE patient.empi_review CASCADE;
--           DELETE FROM identity.role_permission WHERE permission_id IN (SELECT id FROM identity.permission WHERE domain='patient' AND action='merge' AND resource='cosign');
--           DELETE FROM identity.permission WHERE domain='patient' AND action='merge' AND resource='cosign';

ALTER TABLE patient.patient_record
    ADD COLUMN IF NOT EXISTS merged_into UUID REFERENCES patient.patient_record(id),
    ADD COLUMN IF NOT EXISTS merged_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS patient.empi_review (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id          UUID NOT NULL REFERENCES hospital_config.hospital(id),
    target_patient_id    UUID NOT NULL REFERENCES patient.patient_record(id),
    duplicate_patient_id UUID NOT NULL REFERENCES patient.patient_record(id),
    score                INT NOT NULL DEFAULT 0,
    matched_on           TEXT NOT NULL DEFAULT '',
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submitted_by         UUID NOT NULL,
    decided_by           UUID,
    decided_at           TIMESTAMPTZ,
    merge_id             UUID,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_empi_review_not_self CHECK (target_patient_id <> duplicate_patient_id),
    CONSTRAINT ck_empi_review_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

-- One open review per patient pair: a partial unique index keeps history rows
-- (APPROVED/REJECTED) allowed while blocking duplicate open intents.
CREATE UNIQUE INDEX IF NOT EXISTS uq_empi_review_pending
    ON patient.empi_review (hospital_id, target_patient_id, duplicate_patient_id)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_empi_review_hospital ON patient.empi_review(hospital_id);
CREATE INDEX IF NOT EXISTS idx_empi_review_status ON patient.empi_review(hospital_id, status);

CREATE TABLE IF NOT EXISTS patient.patient_merge_log (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id          UUID NOT NULL REFERENCES hospital_config.hospital(id),
    target_patient_id    UUID NOT NULL REFERENCES patient.patient_record(id),
    duplicate_patient_id UUID NOT NULL REFERENCES patient.patient_record(id),
    merge_version        INT NOT NULL,
    status               VARCHAR(20) NOT NULL,
    snapshot             JSONB NOT NULL,
    merged_by            UUID NOT NULL,
    merged_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    unmerged_by          UUID,
    unmerged_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_merge_log_status CHECK (status IN ('MERGED', 'UNMERGED')),
    CONSTRAINT ck_merge_log_version CHECK (merge_version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_merge_log_hospital ON patient.patient_merge_log(hospital_id);
CREATE INDEX IF NOT EXISTS idx_merge_log_pair ON patient.patient_merge_log(hospital_id, target_patient_id, duplicate_patient_id);

-- Two-person co-sign permission: auditor role (READ_ONLY_VIEWER) + hospital/system admins.
INSERT INTO identity.permission (domain, action, resource, description)
VALUES ('patient', 'merge', 'cosign', 'Second-person approval for patient merge (auditor co-sign)')
ON CONFLICT (domain, action, resource) DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM identity.role r
JOIN identity.permission p ON p.domain = 'patient' AND p.action = 'merge' AND p.resource = 'cosign'
WHERE r.name IN ('SYSTEM_ADMIN', 'HOSPITAL_ADMIN', 'READ_ONLY_VIEWER')
ON CONFLICT DO NOTHING;
