-- V51: Consent engine v1 + outbound notification dispatch (00.12) - chunk items 11 + 12.
-- Item 11: purpose-scoped consent records (treatment, data sharing/ABDM, communications),
-- digital signature capture (eSign-ready), withdrawal, expiry.
-- Item 12: notification_request ledger for outbound channel sends (per-channel consent check,
-- DND hours config); in-app stays event-driven via the 00.09 bridge.

CREATE SCHEMA IF NOT EXISTS consents;

CREATE TABLE IF NOT EXISTS consents.consent (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID,
    patient_id     UUID NOT NULL,
    purpose        VARCHAR(30) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'granted',
    scope          TEXT,
    signature_data TEXT,
    signed_by_name VARCHAR(255),
    signed_at      TIMESTAMPTZ,
    expires_at     TIMESTAMPTZ,
    withdrawn_at   TIMESTAMPTZ,
    withdrawn_by   UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_consent_patient ON consents.consent (patient_id, purpose);

CREATE TABLE IF NOT EXISTS consents.consent_channel_optin (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID,
    consent_id  UUID NOT NULL,
    channel     VARCHAR(20) NOT NULL,
    opted_in    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_optin_consent FOREIGN KEY (consent_id) REFERENCES consents.consent (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_optin_consent_channel
    ON consents.consent_channel_optin (consent_id, channel);

CREATE TABLE IF NOT EXISTS notifications.notification_request (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID,
    patient_id        UUID,
    recipient_id      UUID,
    recipient_address VARCHAR(255),
    channel           VARCHAR(20) NOT NULL,
    type              VARCHAR(50) NOT NULL,
    title             VARCHAR(255) NOT NULL,
    body              TEXT,
    status            VARCHAR(30) NOT NULL,
    provider_ref      VARCHAR(255),
    error_message     TEXT,
    requested_by      UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_notification_request_hospital
    ON notifications.notification_request (hospital_id, created_at DESC);

-- Consent + outbound-send workforce (00.12): doctors and nurses capture consent and send.
INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES (gen_random_uuid(), 'consent',      'manage', 'consent',      'Capture, view and withdraw patient consent'),
       (gen_random_uuid(), 'notification', 'send',   'notification', 'Send outbound channel notifications')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'consent' AND p.action = 'manage' AND p.resource = 'consent'
WHERE r.name IN ('DOCTOR', 'NURSE')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'notification' AND p.action = 'send' AND p.resource = 'notification'
WHERE r.name IN ('DOCTOR', 'NURSE')
ON CONFLICT DO NOTHING;

-- The 00.09 bridge creates in-app rows for event recipients (clinicians); they need to read
-- their own inbox (until V51 the read permission was SYSTEM_ADMIN-only).
INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'notification' AND p.action = 'read' AND p.resource = 'notification'
WHERE r.name IN ('DOCTOR', 'NURSE')
ON CONFLICT DO NOTHING;
