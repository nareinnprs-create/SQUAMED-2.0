-- V102: allied health + contact centre + patient experience (chunk-07 section 07.07)
-- Therapy referrals/sessions, contact-centre interactions, international patient desk and
-- patient-experience feedback with a governed follow-up task.
-- Rollback note: additive only - drop schema allied, drop schema cx; delete the allied/cx
-- permissions and role grants.

CREATE SCHEMA IF NOT EXISTS allied;
CREATE SCHEMA IF NOT EXISTS cx;

CREATE TABLE IF NOT EXISTS allied.therapy_referral (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id      UUID NOT NULL,
    patient_id       UUID NOT NULL,
    discipline       VARCHAR(20) NOT NULL,
    reason           VARCHAR(600) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'REFERRED',
    therapist_id     UUID,
    scheduled_at     TIMESTAMPTZ,
    appointment_id   UUID,
    care_plan_id     UUID,
    sessions_planned INTEGER NOT NULL DEFAULT 4,
    outcome          VARCHAR(600),
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_therapy_referral_patient ON allied.therapy_referral (hospital_id, patient_id);

CREATE TABLE IF NOT EXISTS allied.therapy_session (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id      UUID NOT NULL,
    referral_id      UUID NOT NULL REFERENCES allied.therapy_referral(id) ON DELETE CASCADE,
    session_at       TIMESTAMPTZ NOT NULL,
    duration_minutes INTEGER NOT NULL,
    notes            TEXT,
    outcome          VARCHAR(300),
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    signed_by        UUID,
    signed_at        TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_therapy_session_referral ON allied.therapy_session (referral_id);

CREATE TABLE IF NOT EXISTS cx.contact_interaction (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id        UUID NOT NULL,
    patient_id         UUID,
    channel            VARCHAR(20) NOT NULL,
    direction          VARCHAR(20) NOT NULL,
    subject            VARCHAR(300) NOT NULL,
    agent_id           UUID,
    status             VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolution         VARCHAR(600),
    ai_suggested_reply VARCHAR(600),
    closed_at          TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_contact_interaction_open ON cx.contact_interaction (hospital_id, status);

CREATE TABLE IF NOT EXISTS cx.intl_case (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id        UUID NOT NULL,
    patient_id         UUID NOT NULL,
    country            VARCHAR(80) NOT NULL,
    language           VARCHAR(40) NOT NULL,
    interpreter_needed BOOLEAN NOT NULL DEFAULT FALSE,
    coordinator_id     UUID,
    status             VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    notes              VARCHAR(600),
    closed_at          TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS cx.feedback (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID NOT NULL,
    patient_id        UUID NOT NULL,
    score             INTEGER NOT NULL,
    category          VARCHAR(60) NOT NULL,
    comment           VARCHAR(1000),
    status            VARCHAR(20) NOT NULL DEFAULT 'NEW',
    followup_task_id  UUID,
    resolution        VARCHAR(600),
    resolved_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_feedback_hospital ON cx.feedback (hospital_id, status);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'allied', 'read',  'therapy', 'Read therapy referrals and sessions'),
    (gen_random_uuid(), 'allied', 'write', 'therapy', 'Manage therapy referrals and sessions'),
    (gen_random_uuid(), 'cx', 'read',  'cx', 'Read contact-centre interactions, international cases and feedback'),
    (gen_random_uuid(), 'cx', 'write', 'cx', 'Manage contact-centre interactions, international cases and feedback')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role (name, display_name, description, is_system)
VALUES
    ('THERAPIST', 'Therapist', 'Allied health referrals and therapy sessions', TRUE),
    ('CONTACT_CENTRE_AGENT', 'Contact Centre Agent', 'Contact-centre interactions and international patient desk', TRUE),
    ('PATIENT_EXPERIENCE_OFFICER', 'Patient Experience Officer', 'Patient feedback and experience follow-ups', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain IN ('allied', 'cx')
WHERE r.name IN ('SYSTEM_ADMIN', 'HOSPITAL_ADMIN')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'allied'
WHERE r.name = 'THERAPIST'
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'cx'
WHERE r.name IN ('CONTACT_CENTRE_AGENT', 'PATIENT_EXPERIENCE_OFFICER')
ON CONFLICT DO NOTHING;
