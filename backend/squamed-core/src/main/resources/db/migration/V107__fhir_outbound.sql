-- V107: outbound FHIR export ledger (chunk-06 section 06.07)
-- One row per resource pushed to a peer HAPI server, so every outbound share is auditable and
-- document access (DiagnosticReport) can be shown as WITHHELD rather than silently dropped.
-- Rollback note: additive only - drop table interop.fhir_export; delete the integration:export:fhir permission.

CREATE TABLE IF NOT EXISTS interop.fhir_export (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL,
    patient_id     UUID NOT NULL,
    target_base    VARCHAR(500) NOT NULL,
    resource_type  VARCHAR(50) NOT NULL,
    resource_id    UUID NOT NULL,
    status         VARCHAR(20) NOT NULL,
    http_status    INTEGER,
    detail         VARCHAR(500),
    exported_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_fhir_export_hospital
    ON interop.fhir_export (hospital_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_fhir_export_patient
    ON interop.fhir_export (patient_id, created_at DESC);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'integration', 'export', 'fhir', 'Export governed records to a peer FHIR server')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'integration' AND p.action = 'export'
WHERE r.name IN ('SYSTEM_ADMIN', 'HOSPITAL_ADMIN')
ON CONFLICT DO NOTHING;
