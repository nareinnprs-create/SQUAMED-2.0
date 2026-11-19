-- V64: Pharmacy stock governance (chunk-02 section 02.06)
-- Stock ledger (trigger-enforced balance), formulary, indents, controlled register,
-- FEFO/near-expiry dispense support (stock_id/batch snapshot on dispensation),
-- audited substitution columns, wastage. Reuses V17 pharmacy permissions.
-- Rollback note: additive only — drop pharmacy.controlled_register, pharmacy.indent,
-- pharmacy.formulary_entry, pharmacy.stock_ledger + trg_stock_ledger_balance_guard +
-- fn_ledger_balance_guard, drop the added pharmacy.dispensation columns to revert.

ALTER TABLE pharmacy.dispensation
    ADD COLUMN IF NOT EXISTS stock_id UUID,
    ADD COLUMN IF NOT EXISTS batch_number VARCHAR(100),
    ADD COLUMN IF NOT EXISTS substituted_drug_id UUID,
    ADD COLUMN IF NOT EXISTS substituted_drug_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS substitution_reason VARCHAR(400);

-- Every quantity mutation appends a row; balance_after must equal the stock row's
-- quantity_on_hand (enforced by trigger), so the running sum reconstructs on-hand.
CREATE TABLE IF NOT EXISTS pharmacy.stock_ledger (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL,
    stock_id       UUID NOT NULL REFERENCES pharmacy.pharmacy_stock(id) ON DELETE CASCADE,
    movement       VARCHAR(20) NOT NULL, -- INTAKE | DISPENSE | RESTOCK | ADJUST | WASTAGE
    quantity_delta INTEGER NOT NULL,
    balance_after  INTEGER NOT NULL,
    reference_type VARCHAR(30),
    reference_id   UUID,
    actor_id       UUID,
    note           VARCHAR(400),
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_stock_ledger_stock ON pharmacy.stock_ledger (stock_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_stock_ledger_hospital ON pharmacy.stock_ledger (hospital_id);

-- Formulary: per-hospital approved-drug list; drug must exist and be active (master_data.drug).
CREATE TABLE IF NOT EXISTS pharmacy.formulary_entry (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    drug_id     UUID NOT NULL REFERENCES master_data.drug(id),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    notes       VARCHAR(400),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_formulary_hospital_drug UNIQUE (hospital_id, drug_id)
);

CREATE INDEX IF NOT EXISTS idx_formulary_hospital ON pharmacy.formulary_entry (hospital_id);

-- Indents: store requisitions; receive adds stock via the ledger.
CREATE TABLE IF NOT EXISTS pharmacy.indent (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id        UUID NOT NULL,
    drug_id            UUID,
    drug_name          VARCHAR(255) NOT NULL,
    quantity_requested INTEGER NOT NULL,
    quantity_received  INTEGER NOT NULL DEFAULT 0,
    status             VARCHAR(20) NOT NULL DEFAULT 'requested', -- requested | received | cancelled
    note               VARCHAR(400),
    requested_by       UUID,
    received_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_indent_hospital ON pharmacy.indent (hospital_id, status);

-- Controlled-drug register: one row per controlled dispense with second authorizer (CS-04 pharmacist leg).
CREATE TABLE IF NOT EXISTS pharmacy.controlled_register (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id          UUID NOT NULL,
    dispensation_id      UUID REFERENCES pharmacy.dispensation(id),
    drug_id              UUID,
    drug_name            VARCHAR(255) NOT NULL,
    batch_number         VARCHAR(100),
    quantity             INTEGER NOT NULL,
    dispensed_by         UUID,
    second_authorizer_id UUID NOT NULL,
    occurred_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_controlled_register_hospital ON pharmacy.controlled_register (hospital_id);

-- Trigger-enforced balance: a ledger row may only be inserted when the referenced
-- stock row's quantity_on_hand already equals the row's balance_after.
CREATE OR REPLACE FUNCTION pharmacy.fn_ledger_balance_guard() RETURNS trigger AS $$
DECLARE
    actual INTEGER;
BEGIN
    SELECT quantity_on_hand INTO actual FROM pharmacy.pharmacy_stock WHERE id = NEW.stock_id;
    IF actual IS NULL THEN
        RAISE EXCEPTION 'LEDGER_STOCK_NOT_FOUND';
    END IF;
    IF actual <> NEW.balance_after THEN
        RAISE EXCEPTION 'LEDGER_BALANCE_MISMATCH';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_stock_ledger_balance_guard ON pharmacy.stock_ledger;
CREATE TRIGGER trg_stock_ledger_balance_guard
BEFORE INSERT ON pharmacy.stock_ledger
FOR EACH ROW EXECUTE FUNCTION pharmacy.fn_ledger_balance_guard();
