-- V45: Master data registries (00.06) — vendor, equipment, employee, price lists.
-- Hospital-scoped per ADR-13 stage 1: hospital_id NOT NULL, uniqueness per hospital.
-- The six V6 clinical catalogs (drug, lab_test, imaging_procedure, procedure_code,
-- diagnosis_code, charge_item) stay deliberately global reference sets (same class as
-- ai.model_config in V43); per-hospital pricing is carried by price_list below.
-- Rollback: DROP TABLE master_data.price_list_item, master_data.price_list, master_data.employee,
-- master_data.equipment, master_data.vendor CASCADE;

CREATE TABLE IF NOT EXISTS master_data.vendor (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL REFERENCES hospital_config.hospital(id),
    code           VARCHAR(50) NOT NULL,
    name           VARCHAR(255) NOT NULL,
    contact_person VARCHAR(255),
    phone          VARCHAR(50),
    email          VARCHAR(255),
    address        TEXT,
    tax_id         VARCHAR(100),
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_vendor_hospital_code UNIQUE (hospital_id, code)
);

CREATE TABLE IF NOT EXISTS master_data.equipment (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL REFERENCES hospital_config.hospital(id),
    code           VARCHAR(50) NOT NULL,
    name           VARCHAR(255) NOT NULL,
    category       VARCHAR(100),
    vendor_id      UUID REFERENCES master_data.vendor(id),
    serial_number  VARCHAR(100),
    location       VARCHAR(255),
    purchase_date  DATE,
    warranty_until DATE,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_equipment_hospital_code UNIQUE (hospital_id, code)
);

CREATE TABLE IF NOT EXISTS master_data.employee (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL REFERENCES hospital_config.hospital(id),
    code           VARCHAR(50) NOT NULL,
    first_name     VARCHAR(255) NOT NULL,
    last_name      VARCHAR(255) NOT NULL,
    gender         VARCHAR(30),
    date_of_birth  DATE,
    phone          VARCHAR(50),
    email          VARCHAR(255),
    designation    VARCHAR(100),
    department     VARCHAR(100),
    qualification  VARCHAR(255),
    license_number VARCHAR(100),
    license_expiry DATE,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_employee_hospital_code UNIQUE (hospital_id, code)
);

CREATE TABLE IF NOT EXISTS master_data.price_list (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL REFERENCES hospital_config.hospital(id),
    code           VARCHAR(50) NOT NULL,
    name           VARCHAR(255) NOT NULL,
    currency       VARCHAR(3) NOT NULL DEFAULT 'QAR',
    valid_from     DATE,
    valid_to       DATE,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_price_list_hospital_code UNIQUE (hospital_id, code)
);

CREATE TABLE IF NOT EXISTS master_data.price_list_item (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL REFERENCES hospital_config.hospital(id),
    price_list_id  UUID NOT NULL REFERENCES master_data.price_list(id) ON DELETE CASCADE,
    charge_item_id UUID NOT NULL REFERENCES master_data.charge_item(id),
    price_qar      NUMERIC(12,2) NOT NULL DEFAULT 0,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_price_list_item UNIQUE (price_list_id, charge_item_id)
);

CREATE INDEX IF NOT EXISTS idx_md_vendor_hospital ON master_data.vendor (hospital_id);
CREATE INDEX IF NOT EXISTS idx_md_equipment_hospital ON master_data.equipment (hospital_id);
CREATE INDEX IF NOT EXISTS idx_md_employee_hospital ON master_data.employee (hospital_id);
CREATE INDEX IF NOT EXISTS idx_md_price_list_hospital ON master_data.price_list (hospital_id);
