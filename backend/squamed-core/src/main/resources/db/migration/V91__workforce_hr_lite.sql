-- V91: workforce / HR-lite (chunk-06 section 06.04)
-- Rosters + shifts, leave, attendance, credential-expiry registry, nurse-patient ratio source.
-- Payroll stays external (export only). Rollback note: additive only - drop schema hr.

CREATE SCHEMA IF NOT EXISTS hr;

CREATE TABLE IF NOT EXISTS hr.roster (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL,
    ward_id      UUID,
    week_start   DATE NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_by UUID,
    published_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, ward_id, week_start)
);

CREATE TABLE IF NOT EXISTS hr.shift (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    roster_id   UUID NOT NULL,
    ward_id     UUID,
    employee_id UUID NOT NULL,
    shift_date  DATE NOT NULL,
    shift_code  VARCHAR(20) NOT NULL,
    role        VARCHAR(40) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (roster_id, employee_id, shift_date, shift_code)
);

CREATE INDEX IF NOT EXISTS idx_hr_shift_day ON hr.shift (hospital_id, shift_date, role);

CREATE TABLE IF NOT EXISTS hr.leave_request (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    leave_type  VARCHAR(30) NOT NULL,
    from_date   DATE NOT NULL,
    to_date     DATE NOT NULL,
    reason      VARCHAR(400),
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decided_by  UUID,
    decided_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_hr_leave_hospital ON hr.leave_request (hospital_id, status, from_date);

CREATE TABLE IF NOT EXISTS hr.attendance (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL,
    employee_id  UUID NOT NULL,
    work_date    DATE NOT NULL,
    check_in_at  TIMESTAMPTZ,
    check_out_at TIMESTAMPTZ,
    status       VARCHAR(20) NOT NULL DEFAULT 'PRESENT',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, employee_id, work_date)
);

CREATE TABLE IF NOT EXISTS hr.credential (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id      UUID NOT NULL,
    employee_id      UUID NOT NULL,
    credential_type  VARCHAR(60) NOT NULL,
    identifier       VARCHAR(120),
    expires_on       DATE NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    renewal_task_id  UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_hr_credential_expiry ON hr.credential (hospital_id, status, expires_on);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'hr', 'read',    'hr',    'Read rosters, leave, attendance, credentials and reports'),
    (gen_random_uuid(), 'hr', 'write',   'hr',    'Manage rosters, shifts, attendance and credentials'),
    (gen_random_uuid(), 'hr', 'approve', 'leave', 'Approve or reject leave requests')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role (name, display_name, description, is_system)
VALUES ('HR_ADMIN', 'HR Administrator', 'Workforce rostering, leave, attendance and credential tracking', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'hr'
WHERE r.name IN ('SYSTEM_ADMIN', 'HR_ADMIN')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'hr' AND p.action = 'read'
WHERE r.name IN ('NURSE', 'DOCTOR')
ON CONFLICT DO NOTHING;
