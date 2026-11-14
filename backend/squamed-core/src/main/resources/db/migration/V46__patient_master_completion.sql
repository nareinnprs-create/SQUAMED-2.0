-- V46: Patient master completion (00.07) — guardian/dependent relationships + unknown-patient flow.
-- Hospital-scoped per ADR-13 stage 1: hospital_id NOT NULL, both endpoints must belong to the
-- same hospital (service-enforced). Unknown patients store sentinel DOB 1900-01-01 and the name
-- "Unknown" until identified (records_status pending -> active); is_unknown marks the state.
-- ABHA identifiers are rows in patient.patient_identifier (id_type 'ABHA' / 'ABHA_ADDRESS')
-- with format validation in PatientService; live ABDM sandbox linkage stays external (E1).
-- Rollback: ALTER TABLE patient.patient_record DROP COLUMN is_unknown;
--           DROP TABLE patient.patient_relationship CASCADE;

ALTER TABLE patient.patient_record
    ADD COLUMN IF NOT EXISTS is_unknown BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS patient.patient_relationship (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id          UUID NOT NULL REFERENCES hospital_config.hospital(id),
    patient_id           UUID NOT NULL REFERENCES patient.patient_record(id) ON DELETE CASCADE,
    related_patient_id   UUID NOT NULL REFERENCES patient.patient_record(id) ON DELETE CASCADE,
    relationship_type    VARCHAR(50) NOT NULL,
    is_primary_guardian  BOOLEAN NOT NULL DEFAULT FALSE,
    notes                VARCHAR(500),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_relationship_not_self CHECK (patient_id <> related_patient_id),
    CONSTRAINT uq_patient_relationship UNIQUE (patient_id, related_patient_id, relationship_type)
);

CREATE INDEX IF NOT EXISTS idx_patient_relationship_patient ON patient.patient_relationship(patient_id);
CREATE INDEX IF NOT EXISTS idx_patient_relationship_related ON patient.patient_relationship(related_patient_id);
CREATE INDEX IF NOT EXISTS idx_patient_relationship_hospital ON patient.patient_relationship(hospital_id);
