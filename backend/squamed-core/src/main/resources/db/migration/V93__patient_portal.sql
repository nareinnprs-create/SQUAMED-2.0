-- V93: patient portal v2 (chunk-06 section 06.03)
-- Patient account linked to a user (ABHA link stored; ABDM OTP login is external E1), push
-- subscription registry for the notification centre, and the portal's own-data permissions.
-- Rollback note: additive only - drop schema portal.

CREATE SCHEMA IF NOT EXISTS portal;

CREATE TABLE IF NOT EXISTS portal.patient_account (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL UNIQUE,
    patient_id   UUID NOT NULL,
    hospital_id  UUID NOT NULL,
    abha_number  VARCHAR(40),
    abha_address VARCHAR(120),
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS portal.push_subscription (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL,
    endpoint   VARCHAR(600) NOT NULL UNIQUE,
    p256dh     VARCHAR(300) NOT NULL,
    auth       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version    BIGINT NOT NULL DEFAULT 0
);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'portal', 'read',   'self',    'Read own patient portal data'),
    (gen_random_uuid(), 'portal', 'book',   'self',    'Book own appointments from the portal'),
    (gen_random_uuid(), 'portal', 'pay',    'self',    'Pay own bills from the portal'),
    (gen_random_uuid(), 'portal', 'manage', 'account', 'Link patient portal accounts')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role (name, display_name, description, is_system)
VALUES ('PATIENT', 'Patient', 'Patient portal: own records, booking, bills and notifications', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'portal' AND p.action IN ('read', 'book', 'pay')
WHERE r.name = 'PATIENT'
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'portal'
WHERE r.name IN ('SYSTEM_ADMIN', 'HOSPITAL_ADMIN')
ON CONFLICT DO NOTHING;
