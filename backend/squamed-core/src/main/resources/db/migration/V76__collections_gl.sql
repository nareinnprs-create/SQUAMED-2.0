-- V76: Collections + GL export (chunk-03 section 03.06)
-- Shift cash-up with variance detection (task on breach), payment reconciliation statements,
-- supervisor-gated refunds, and a summarized GL journal export per day (not a full GL -
-- Tally/ZOHO Books consume the summary; full GL is chunk 6+).
-- Rollback note: additive only - drop the three new tables to revert. No new permission rows
-- (cash-up uses billing:collect:payment, refunds billing:approve:creditnote, recon/export
-- billing:read:invoice).

CREATE TABLE IF NOT EXISTS billing.shift_cashup (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID NOT NULL,
    cashier_id        UUID NOT NULL,
    opened_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at         TIMESTAMPTZ,
    opening_float_qar NUMERIC(12, 2) NOT NULL DEFAULT 0,
    cash_collected_qar NUMERIC(12, 2) NOT NULL DEFAULT 0,
    counted_cash_qar  NUMERIC(12, 2),
    variance_qar      NUMERIC(12, 2),
    status            VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    note              VARCHAR(400),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_shift_cashup_cashier ON billing.shift_cashup (hospital_id, cashier_id, status);

CREATE TABLE IF NOT EXISTS billing.recon_statement (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id          UUID NOT NULL,
    method               VARCHAR(20) NOT NULL,
    period_start         TIMESTAMPTZ NOT NULL,
    period_end           TIMESTAMPTZ NOT NULL,
    statement_total_qar  NUMERIC(12, 2) NOT NULL,
    recorded_total_qar   NUMERIC(12, 2) NOT NULL,
    variance_qar         NUMERIC(12, 2) NOT NULL,
    status               VARCHAR(10) NOT NULL CHECK (status IN ('MATCHED', 'VARIANCE')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS billing.refund (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    payment_id  UUID NOT NULL REFERENCES billing.payment(id),
    invoice_id  UUID NOT NULL REFERENCES billing.invoice(id),
    amount_qar  NUMERIC(12, 2) NOT NULL CHECK (amount_qar > 0),
    reason      VARCHAR(400) NOT NULL,
    status      VARCHAR(10) NOT NULL DEFAULT 'issued' CHECK (status IN ('issued')),
    issued_by   UUID,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0
);
