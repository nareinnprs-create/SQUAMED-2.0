-- V113: entitlement service (chunk-01 section 01.10, internal half)
-- A plan is a named feature set; a hospital's active subscription decides which modules answer.
-- No subscription row means UNMANAGED: every feature is allowed, so existing hospitals are
-- unaffected until someone actually subscribes them. The external half of 01.10 (per-tenant DLT
-- header / WABA sender, eSign certificate per hospital entity) is not modelled here.
-- Rollback note: additive only - drop schema entitlement; delete the two permissions.

CREATE SCHEMA IF NOT EXISTS entitlement;

CREATE TABLE IF NOT EXISTS entitlement.plan (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code         VARCHAR(40) NOT NULL,
    name         VARCHAR(120) NOT NULL,
    description  VARCHAR(300),
    features     TEXT NOT NULL,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_entitlement_plan_code ON entitlement.plan (code);

CREATE TABLE IF NOT EXISTS entitlement.subscription (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL,
    plan_id      UUID NOT NULL REFERENCES entitlement.plan (id),
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    valid_from   DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to     DATE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_entitlement_subscription_hospital
    ON entitlement.subscription (hospital_id, status);

-- CORE is the operational hospital; FULL adds the intelligence and ecosystem modules.
INSERT INTO entitlement.plan (code, name, description, features)
VALUES
    ('CORE', 'Core hospital', 'Registration through billing, without the AI and analytics modules',
     'patients,appointments,queue,encounters,billing,laboratory,pharmacy,ipd,notifications,documents'),
    ('FULL', 'Full platform', 'Core plus AI, analytics, portal, kiosk and interop modules',
     'patients,appointments,queue,encounters,billing,laboratory,pharmacy,ipd,notifications,documents,ai,bi,scribe,portal,kiosk,fhir,twin,packs,telemedicine')
ON CONFLICT DO NOTHING;

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'entitlement', 'read', 'plan', 'Read plans and the hospital entitlement'),
    (gen_random_uuid(), 'entitlement', 'manage', 'plan', 'Subscribe a hospital to a plan')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'entitlement'
WHERE r.name IN ('SYSTEM_ADMIN', 'HOSPITAL_ADMIN')
ON CONFLICT DO NOTHING;
