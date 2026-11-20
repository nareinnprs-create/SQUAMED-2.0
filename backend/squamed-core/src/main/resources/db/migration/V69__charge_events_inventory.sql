-- V69: Charge events + inventory linkage + eMAR groundwork (chunk-02 section 02.08)
-- Reorder proposals: min-level stock alerts become human-approved purchase-order drafts
-- (full procurement is chunk 6). Nothing is auto-ordered — a pharmacist approves or rejects.
-- eMAR groundwork: the dispensation snapshots the batch expiry so chunk-4 administration
-- can verify what was dispensed against a live prescription (no expiry editing in place).
-- Rollback note: additive only — drop pharmacy.reorder_proposal, drop the added
-- pharmacy.dispensation.expiry_date column. No permission rows added (reuses pharmacy:*).

CREATE TABLE IF NOT EXISTS pharmacy.reorder_proposal (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id        UUID NOT NULL,
    drug_id            UUID REFERENCES master_data.drug(id),
    drug_name          VARCHAR(200) NOT NULL,
    stock_id           UUID REFERENCES pharmacy.pharmacy_stock(id),
    requested_quantity INTEGER NOT NULL CHECK (requested_quantity > 0),
    status             VARCHAR(10) NOT NULL DEFAULT 'PROPOSED' CHECK (status IN ('PROPOSED', 'APPROVED', 'REJECTED')),
    note               VARCHAR(400),
    proposed_by        UUID,
    decided_by         UUID,
    decided_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_reorder_proposal_hospital_status ON pharmacy.reorder_proposal (hospital_id, status);
CREATE INDEX IF NOT EXISTS idx_reorder_proposal_stock ON pharmacy.reorder_proposal (stock_id);

-- One open proposal per stock row: concurrent generates cannot double-propose the same batch.
CREATE UNIQUE INDEX IF NOT EXISTS uq_reorder_proposal_open_stock
    ON pharmacy.reorder_proposal (stock_id) WHERE status = 'PROPOSED';

ALTER TABLE pharmacy.dispensation
    ADD COLUMN IF NOT EXISTS expiry_date DATE;
