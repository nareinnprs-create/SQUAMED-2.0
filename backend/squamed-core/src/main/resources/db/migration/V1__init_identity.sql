-- V1: Initial schema — Identity domain foundation
-- Module: squamed-identity

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS patient;
CREATE SCHEMA IF NOT EXISTS hospital_config;
CREATE SCHEMA IF NOT EXISTS master_data;
CREATE SCHEMA IF NOT EXISTS clinical;
CREATE SCHEMA IF NOT EXISTS appointment;
CREATE SCHEMA IF NOT EXISTS reception;
CREATE SCHEMA IF NOT EXISTS queue;
CREATE SCHEMA IF NOT EXISTS orders;
CREATE SCHEMA IF NOT EXISTS laboratory;
CREATE SCHEMA IF NOT EXISTS pharmacy;
CREATE SCHEMA IF NOT EXISTS billing;
CREATE SCHEMA IF NOT EXISTS tasks;
CREATE SCHEMA IF NOT EXISTS communication;
CREATE SCHEMA IF NOT EXISTS notifications;
CREATE SCHEMA IF NOT EXISTS workflow;
CREATE SCHEMA IF NOT EXISTS audit;
CREATE SCHEMA IF NOT EXISTS ai;

CREATE TABLE IF NOT EXISTS identity.user (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    phone           VARCHAR(20),
    password_hash   VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    mfa_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_secret      VARCHAR(255),
    last_login_at   TIMESTAMPTZ,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID
);

CREATE TABLE IF NOT EXISTS identity.user_profile (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES identity.user(id) ON DELETE CASCADE,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    display_name    VARCHAR(200),
    avatar_url      VARCHAR(500),
    language_preference VARCHAR(20) NOT NULL DEFAULT 'en',
    timezone        VARCHAR(50) NOT NULL DEFAULT 'UTC',
    theme_preference VARCHAR(20) NOT NULL DEFAULT 'clinical',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS identity.role (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL UNIQUE,
    display_name    VARCHAR(150) NOT NULL,
    description     TEXT,
    is_system       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS identity.permission (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    domain          VARCHAR(100) NOT NULL,
    action          VARCHAR(50) NOT NULL,
    resource        VARCHAR(150),
    description     TEXT,
    UNIQUE (domain, action, resource)
);

CREATE TABLE IF NOT EXISTS identity.role_permission (
    role_id         UUID NOT NULL REFERENCES identity.role(id) ON DELETE CASCADE,
    permission_id   UUID NOT NULL REFERENCES identity.permission(id) ON DELETE CASCADE,
    constraints     JSONB,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS identity.user_role (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES identity.user(id) ON DELETE CASCADE,
    role_id         UUID NOT NULL REFERENCES identity.role(id),
    hospital_id     UUID,
    department_id   UUID,
    scope           JSONB,
    valid_from      TIMESTAMPTZ,
    valid_until     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS identity.session (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES identity.user(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL,
    refresh_token_hash VARCHAR(255) NOT NULL,
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    hospital_id     UUID,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_identity_user_email ON identity.user(email);
CREATE INDEX IF NOT EXISTS idx_identity_session_user ON identity.session(user_id);
CREATE INDEX IF NOT EXISTS idx_identity_user_role_user ON identity.user_role(user_id);
CREATE INDEX IF NOT EXISTS idx_identity_role_permission_role ON identity.role_permission(role_id);

-- Seed system roles
INSERT INTO identity.role (name, display_name, description, is_system)
VALUES
    ('SYSTEM_ADMIN', 'System Administrator', 'Full platform access across all hospitals', TRUE),
    ('HOSPITAL_ADMIN', 'Hospital Administrator', 'Full access within a hospital', TRUE),
    ('DEPARTMENT_ADMIN', 'Department Administrator', 'Administrative access within a department', TRUE),
    ('DOCTOR', 'Doctor', 'Clinical care, documentation, orders and prescriptions', TRUE),
    ('NURSE', 'Nurse', 'Nursing care, vitals, medication administration', TRUE),
    ('PHARMACIST', 'Pharmacist', 'Prescription verification and dispensing', TRUE),
    ('LAB_TECHNICIAN', 'Laboratory Technician', 'Sample processing and result entry', TRUE),
    ('RADIOLOGIST', 'Radiologist', 'Imaging interpretation and reporting', TRUE),
    ('RECEPTIONIST', 'Receptionist', 'Patient registration and front office', TRUE),
    ('BILLING_STAFF', 'Billing Staff', 'Billing, payments and estimates', TRUE),
    ('CASHIER', 'Cashier', 'Payment collection', TRUE),
    ('PATHOLOGIST', 'Pathologist', 'Result validation and laboratory oversight', TRUE),
    ('READ_ONLY_VIEWER', 'Read-Only Viewer', 'View-only access for audit and review', TRUE),
    ('AI_AGENT', 'AI Agent', 'Scoped AI agent access', TRUE)
ON CONFLICT (name) DO NOTHING;