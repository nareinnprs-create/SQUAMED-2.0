CREATE SCHEMA IF NOT EXISTS pharmacy;

-- V15: Pharmacy & Medication Management schema
-- Module: squamed-pharmacy

CREATE TABLE IF NOT EXISTS pharmacy.pharmacy_stock (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    drug_id         UUID,
    drug_code       VARCHAR(50),
    drug_name       VARCHAR(255) NOT NULL,
    batch_number    VARCHAR(100),
    expiry_date     DATE,
    quantity_on_hand INTEGER NOT NULL DEFAULT 0,
    reorder_level   INTEGER NOT NULL DEFAULT 10,
    unit_price      NUMERIC(12,2) NOT NULL DEFAULT 0,
    location        VARCHAR(100),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pharmacy.dispensation (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID,
    prescription_id UUID,
    patient_id      UUID NOT NULL,
    encounter_id    UUID,
    drug_id         UUID,
    drug_name       VARCHAR(255) NOT NULL,
    quantity        INTEGER NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'pending',
    dispensed_by    UUID,
    dispensed_at    TIMESTAMPTZ,
    verified_by     UUID,
    verified_at     TIMESTAMPTZ,
    unit_price      NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_price     NUMERIC(12,2) NOT NULL DEFAULT 0,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_pharm_stock_name ON pharmacy.pharmacy_stock (drug_name);
CREATE INDEX IF NOT EXISTS idx_pharm_disp_patient ON pharmacy.dispensation (patient_id);
CREATE INDEX IF NOT EXISTS idx_pharm_disp_status ON pharmacy.dispensation (status);