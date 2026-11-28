-- V114: telemedicine consults (chunk-06 section 06.02, internal half)
-- Encodes the India Telemedicine Guidelines 2020 rules the platform can enforce without a vendor:
-- the named RMP must be an active employee with a valid registration, and a first consult must be
-- video. The video SDK (exp:E9) and the payment link (exp:E4) are not modelled.
-- Rollback note: additive only - drop schema telemedicine; delete the two permissions.

CREATE SCHEMA IF NOT EXISTS telemedicine;

CREATE TABLE IF NOT EXISTS telemedicine.consult (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    patient_id      UUID NOT NULL,
    doctor_user_id  UUID NOT NULL,
    rmp_employee_id UUID NOT NULL,
    consult_type    VARCHAR(20) NOT NULL,
    mode            VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    scheduled_at    TIMESTAMPTZ NOT NULL,
    started_at      TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ,
    encounter_id    UUID,
    meeting_ref     VARCHAR(200),
    escape_reason   VARCHAR(300),
    notes           VARCHAR(1000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_telemedicine_consult_hospital
    ON telemedicine.consult (hospital_id, scheduled_at DESC);

CREATE INDEX IF NOT EXISTS idx_telemedicine_consult_patient
    ON telemedicine.consult (patient_id, scheduled_at DESC);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'telemedicine', 'read', 'consult', 'Read telemedicine consults'),
    (gen_random_uuid(), 'telemedicine', 'manage', 'consult', 'Schedule, start and complete telemedicine consults')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'telemedicine'
WHERE r.name IN ('SYSTEM_ADMIN', 'HOSPITAL_ADMIN', 'DOCTOR')
ON CONFLICT DO NOTHING;
