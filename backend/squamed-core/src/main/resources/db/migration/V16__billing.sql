CREATE SCHEMA IF NOT EXISTS billing;

-- V16: Billing & Payments schema
-- Module: squamed-billing

CREATE TABLE IF NOT EXISTS billing.tariff_item (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID,
    code            VARCHAR(50) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    category        VARCHAR(100),
    price_qar       NUMERIC(12,2) NOT NULL DEFAULT 0,
    insurance_applicable BOOLEAN NOT NULL DEFAULT TRUE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS billing.charge (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID,
    visit_id        UUID,
    encounter_id    UUID,
    patient_id      UUID NOT NULL,
    charge_code     VARCHAR(50),
    charge_name     VARCHAR(255) NOT NULL,
    category        VARCHAR(100),
    quantity        INTEGER NOT NULL DEFAULT 1,
    unit_price_qar  NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_qar       NUMERIC(12,2) NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'posted',
    insurance_claimable BOOLEAN NOT NULL DEFAULT TRUE,
    posted_by       UUID,
    posted_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reference_type  VARCHAR(30),
    reference_id    UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS billing.invoice (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID,
    visit_id        UUID,
    patient_id      UUID NOT NULL,
    invoice_number  VARCHAR(50) NOT NULL,
    subtotal_qar    NUMERIC(12,2) NOT NULL DEFAULT 0,
    discount_qar    NUMERIC(12,2) NOT NULL DEFAULT 0,
    tax_qar         NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_qar       NUMERIC(12,2) NOT NULL DEFAULT 0,
    amount_paid_qar NUMERIC(12,2) NOT NULL DEFAULT 0,
    amount_due_qar  NUMERIC(12,2) NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'unpaid',
    insurance_claim_status VARCHAR(30),
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS billing.invoice_line (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id      UUID NOT NULL REFERENCES billing.invoice(id) ON DELETE CASCADE,
    charge_id       UUID,
    description     VARCHAR(255) NOT NULL,
    quantity        INTEGER NOT NULL DEFAULT 1,
    unit_price_qar  NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_qar       NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS billing.payment (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id      UUID NOT NULL,
    patient_id      UUID NOT NULL,
    amount_qar      NUMERIC(12,2) NOT NULL,
    method          VARCHAR(30) NOT NULL,
    reference       VARCHAR(100),
    status          VARCHAR(20) NOT NULL DEFAULT 'completed',
    received_by     UUID,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_invoice_number ON billing.invoice (invoice_number);
CREATE INDEX IF NOT EXISTS idx_bill_charge_visit ON billing.charge (visit_id);
CREATE INDEX IF NOT EXISTS idx_bill_invoice_patient ON billing.invoice (patient_id);
CREATE INDEX IF NOT EXISTS idx_bill_payment_invoice ON billing.payment (invoice_id);