-- V55: Reception workspace (01.03) - visitor/attendant passes and consult-fee receipts.
-- Both tables are per-hospital; sequences (VPS-/RCP-) are hospital-scoped (see 01.02 lesson).
-- Rollback: DROP TABLE reception.consult_receipt; DROP TABLE reception.visit_pass;

CREATE TABLE IF NOT EXISTS reception.visit_pass (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL,
    visit_id       UUID NOT NULL,
    patient_id     UUID NOT NULL,
    pass_number    VARCHAR(20) NOT NULL,
    visitor_name   VARCHAR(120) NOT NULL,
    relation       VARCHAR(30) NOT NULL DEFAULT 'attendant', -- attendant | visitor
    phone          VARCHAR(30),
    id_reference   VARCHAR(60),
    status         VARCHAR(20) NOT NULL DEFAULT 'active',    -- active | returned
    issued_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    returned_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_visit_pass_status CHECK (status IN ('active', 'returned')),
    CONSTRAINT ck_visit_pass_relation CHECK (relation IN ('attendant', 'visitor'))
);

CREATE INDEX IF NOT EXISTS idx_visit_pass_lookup
    ON reception.visit_pass (hospital_id, visit_id, status);

CREATE TABLE IF NOT EXISTS reception.consult_receipt (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL,
    visit_id       UUID NOT NULL,
    patient_id     UUID NOT NULL,
    receipt_number VARCHAR(20) NOT NULL,
    amount_qar     NUMERIC(12,2) NOT NULL,
    mode           VARCHAR(20) NOT NULL DEFAULT 'cash',       -- cash | card | upi
    status         VARCHAR(20) NOT NULL DEFAULT 'issued',     -- issued | voided
    issued_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    voided_at      TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_consult_receipt_status CHECK (status IN ('issued', 'voided')),
    CONSTRAINT ck_consult_receipt_mode CHECK (mode IN ('cash', 'card', 'upi')),
    CONSTRAINT ck_consult_receipt_amount CHECK (amount_qar > 0)
);

CREATE INDEX IF NOT EXISTS idx_consult_receipt_lookup
    ON reception.consult_receipt (hospital_id, visit_id, status);

-- No new permission codes: pass/receipt writes ride reception:write:visit, reads ride
-- reception:read:visit (RECEPTIONIST holds both since V3; re-landed V54).
