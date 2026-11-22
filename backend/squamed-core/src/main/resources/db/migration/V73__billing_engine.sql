-- V73: Billing engine (chunk-03 section 03.02)
-- Rate cards per payer class (repricing at bill time, base price kept on the line), packages
-- with version pinning (a version applies at order/capture time, never re-resolved at bill
-- time), estimates, deposits/advances applied to invoices, and role-gated discounts with a
-- mandatory reason.
-- Rollback note: additive only - drop the new tables/columns, delete the billing:approve:discount
-- permission row and the BILLING_SUPERVISOR role grants to revert.

-- Payer-class rate cards: one active price per (hospital, payer class, code).
CREATE TABLE IF NOT EXISTS billing.rate_card (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL,
    payer_class  VARCHAR(20) NOT NULL CHECK (payer_class IN ('general', 'cghs', 'esic', 'tpa', 'corporate', 'pmjay')),
    code         VARCHAR(50) NOT NULL,
    price_qar    NUMERIC(12, 2) NOT NULL CHECK (price_qar >= 0),
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rate_card_active
    ON billing.rate_card (hospital_id, payer_class, code) WHERE is_active;

ALTER TABLE billing.invoice
    ADD COLUMN IF NOT EXISTS payer_class VARCHAR(20) NOT NULL DEFAULT 'general',
    ADD COLUMN IF NOT EXISTS discount_reason VARCHAR(400);

ALTER TABLE billing.invoice_line
    ADD COLUMN IF NOT EXISTS base_price_qar NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS rate_card_id UUID REFERENCES billing.rate_card(id);

-- Packages: header + immutable versions; charges pin the version they were captured against.
CREATE TABLE IF NOT EXISTS billing.billing_package (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID,
    code        VARCHAR(50) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_billing_package_code ON billing.billing_package (code);

CREATE TABLE IF NOT EXISTS billing.package_version (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    package_id     UUID NOT NULL REFERENCES billing.billing_package(id),
    package_version INTEGER NOT NULL CHECK (package_version > 0),
    price_qar      NUMERIC(12, 2) NOT NULL CHECK (price_qar >= 0),
    effective_from DATE,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    UNIQUE (package_id, package_version)
);

ALTER TABLE billing.charge
    ADD COLUMN IF NOT EXISTS package_version_id UUID REFERENCES billing.package_version(id);

-- Estimates: quoted before service (items snapshot, total, expiry).
CREATE TABLE IF NOT EXISTS billing.estimate (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    patient_id      UUID NOT NULL,
    encounter_id    UUID,
    estimate_number VARCHAR(50) NOT NULL,
    items_json      TEXT NOT NULL,
    total_qar       NUMERIC(12, 2) NOT NULL,
    status          VARCHAR(10) NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'converted', 'expired')),
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

-- Deposits/advances: held money applied to one invoice, then marked applied.
CREATE TABLE IF NOT EXISTS billing.deposit (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id        UUID NOT NULL,
    patient_id         UUID NOT NULL,
    amount_qar         NUMERIC(12, 2) NOT NULL CHECK (amount_qar > 0),
    method             VARCHAR(20) NOT NULL,
    reference          VARCHAR(100),
    status             VARCHAR(10) NOT NULL DEFAULT 'held' CHECK (status IN ('held', 'applied', 'refunded')),
    received_by        UUID,
    received_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    applied_invoice_id UUID,
    applied_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_deposit_patient ON billing.deposit (hospital_id, patient_id, status);

-- Billing supervisor role (contract PERMISSIONS: discounts > threshold, credit notes, refunds).
INSERT INTO identity.role (name, display_name, description, is_system)
VALUES ('BILLING_SUPERVISOR', 'Billing Supervisor', 'Billing supervisor: discounts, credit notes, refunds', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES (gen_random_uuid(), 'billing', 'approve', 'discount', 'Approve discounts above the threshold')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'billing'
WHERE r.name IN ('BILLING_SUPERVISOR', 'SYSTEM_ADMIN')
ON CONFLICT DO NOTHING;
