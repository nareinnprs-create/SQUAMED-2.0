-- V100: full financials (chunk-07 section 07.04)
-- General ledger, AP/AR control postings, bank reconciliation, cost centres, budgets,
-- fixed-asset depreciation, GST return packs and month close.
-- Rollback note: additive only - drop schema finance; delete the finance permissions and
-- FINANCE_MANAGER role grants.

CREATE SCHEMA IF NOT EXISTS finance;

CREATE TABLE IF NOT EXISTS finance.gl_account (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    code        VARCHAR(20) NOT NULL,
    name        VARCHAR(160) NOT NULL,
    type        VARCHAR(20) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, code)
);

CREATE TABLE IF NOT EXISTS finance.cost_centre (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    code        VARCHAR(40) NOT NULL,
    name        VARCHAR(160) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, code)
);

CREATE TABLE IF NOT EXISTS finance.journal_entry (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    entry_no    VARCHAR(40) NOT NULL,
    entry_date  DATE NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id   UUID NOT NULL,
    description VARCHAR(400) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    posted_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    posted_by   UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, source_type, source_id)
);

CREATE INDEX IF NOT EXISTS idx_journal_entry_period ON finance.journal_entry (hospital_id, entry_date);

CREATE TABLE IF NOT EXISTS finance.journal_line (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL,
    entry_id     UUID NOT NULL REFERENCES finance.journal_entry(id) ON DELETE CASCADE,
    account_code VARCHAR(20) NOT NULL,
    debit_qar    NUMERIC(14,2) NOT NULL DEFAULT 0,
    credit_qar   NUMERIC(14,2) NOT NULL DEFAULT 0,
    cost_centre  VARCHAR(40),
    description  VARCHAR(300),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_journal_line_entry ON finance.journal_line (entry_id);
CREATE INDEX IF NOT EXISTS idx_journal_line_account ON finance.journal_line (hospital_id, account_code);

CREATE TABLE IF NOT EXISTS finance.ap_invoice (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID NOT NULL,
    vendor_name   VARCHAR(200) NOT NULL,
    number        VARCHAR(60) NOT NULL,
    invoice_date  DATE NOT NULL,
    due_date      DATE,
    amount_qar    NUMERIC(14,2) NOT NULL,
    paid_qar      NUMERIC(14,2) NOT NULL DEFAULT 0,
    status        VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    cost_centre   VARCHAR(40),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, number)
);

CREATE TABLE IF NOT EXISTS finance.ap_payment (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID NOT NULL,
    ap_invoice_id UUID NOT NULL REFERENCES finance.ap_invoice(id) ON DELETE CASCADE,
    amount_qar    NUMERIC(14,2) NOT NULL,
    paid_on       DATE NOT NULL,
    method        VARCHAR(30) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS finance.bank_account (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    code            VARCHAR(40) NOT NULL,
    name            VARCHAR(160) NOT NULL,
    opening_balance NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, code)
);

CREATE TABLE IF NOT EXISTS finance.bank_statement_line (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID NOT NULL,
    bank_account_id   UUID NOT NULL REFERENCES finance.bank_account(id) ON DELETE CASCADE,
    statement_date    DATE NOT NULL,
    amount_qar        NUMERIC(14,2) NOT NULL,
    reference         VARCHAR(120),
    matched_entry_id  UUID REFERENCES finance.journal_entry(id),
    status            VARCHAR(20) NOT NULL DEFAULT 'UNMATCHED',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_bank_statement_open ON finance.bank_statement_line (hospital_id, status);

CREATE TABLE IF NOT EXISTS finance.fixed_asset (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id             UUID NOT NULL,
    code                    VARCHAR(40) NOT NULL,
    name                    VARCHAR(160) NOT NULL,
    category                VARCHAR(60) NOT NULL DEFAULT 'equipment',
    cost_qar                NUMERIC(14,2) NOT NULL,
    useful_life_months      INTEGER NOT NULL,
    acquired_on             DATE NOT NULL,
    accumulated_depreciation NUMERIC(14,2) NOT NULL DEFAULT 0,
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,
    version                 BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, code)
);

CREATE TABLE IF NOT EXISTS finance.depreciation_run (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    period      VARCHAR(7) NOT NULL,
    total_qar   NUMERIC(14,2) NOT NULL,
    posted_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    posted_by   UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, period)
);

CREATE TABLE IF NOT EXISTS finance.budget (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    account_code VARCHAR(20) NOT NULL,
    period      VARCHAR(7) NOT NULL,
    amount_qar  NUMERIC(14,2) NOT NULL,
    cost_centre VARCHAR(40) NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, account_code, period, cost_centre)
);

CREATE TABLE IF NOT EXISTS finance.period_close (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID NOT NULL,
    period            VARCHAR(7) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'CLOSED',
    operational_total NUMERIC(14,2) NOT NULL,
    gl_total          NUMERIC(14,2) NOT NULL,
    variance_qar      NUMERIC(14,2) NOT NULL,
    trial_balance     JSONB,
    closed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_by         UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, period)
);

CREATE TABLE IF NOT EXISTS finance.gst_return (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID NOT NULL,
    period        VARCHAR(7) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    taxable_value NUMERIC(14,2) NOT NULL DEFAULT 0,
    cgst_qar      NUMERIC(14,2) NOT NULL DEFAULT 0,
    sgst_qar      NUMERIC(14,2) NOT NULL DEFAULT 0,
    igst_qar      NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_tax_qar NUMERIC(14,2) NOT NULL DEFAULT 0,
    payload       JSONB,
    generated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    filed_at      TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, period)
);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'finance', 'read',  'finance', 'Read GL, AP/AR, bank reconciliation, budgets and reports'),
    (gen_random_uuid(), 'finance', 'write', 'finance', 'Post journals, manage AP/AR, assets and GST packs'),
    (gen_random_uuid(), 'finance', 'close', 'finance', 'Close an accounting period')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role (name, display_name, description, is_system)
VALUES ('FINANCE_MANAGER', 'Finance Manager', 'General ledger, AP/AR, bank reconciliation, budgets and period close', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'finance'
WHERE r.name IN ('SYSTEM_ADMIN', 'HOSPITAL_ADMIN', 'FINANCE_MANAGER')
ON CONFLICT DO NOTHING;
