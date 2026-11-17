-- V57: Patient 360 v1 (01.05) - patient documents (MinIO/S3 object storage; rows hold metadata only).
-- No new permission codes: documents ride patient:read:patient / patient:write:patient.
-- Rollback: DROP TABLE patient.document;

CREATE TABLE IF NOT EXISTS patient.document (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID NOT NULL,
    patient_id    UUID NOT NULL,
    name          VARCHAR(255) NOT NULL,
    category      VARCHAR(40) NOT NULL DEFAULT 'other',       -- report | prescription | imaging | referral | discharge | id | other
    content_type  VARCHAR(120) NOT NULL,
    size_bytes    BIGINT NOT NULL,
    storage_key   VARCHAR(500) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_patient_document_patient
    ON patient.document (patient_id, created_at DESC);
