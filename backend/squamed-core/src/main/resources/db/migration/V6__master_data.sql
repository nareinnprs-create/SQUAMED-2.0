-- V6: Master Data Management schema
-- Module: squamed-master-data

CREATE TABLE IF NOT EXISTS master_data.drug (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(50) NOT NULL UNIQUE,
    name           VARCHAR(255) NOT NULL,
    generic_name   VARCHAR(255),
    brand_name     VARCHAR(255),
    category       VARCHAR(100),
    form           VARCHAR(50),
    strength       VARCHAR(100),
    unit           VARCHAR(30),
    route          VARCHAR(50),
    schedule       VARCHAR(30),
    is_controlled  BOOLEAN NOT NULL DEFAULT FALSE,
    is_antibiotic  BOOLEAN NOT NULL DEFAULT FALSE,
    requires_prescription BOOLEAN NOT NULL DEFAULT TRUE,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS master_data.lab_test (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(50) NOT NULL UNIQUE,
    name           VARCHAR(255) NOT NULL,
    category       VARCHAR(100),
    specimen_type  VARCHAR(50),
    unit           VARCHAR(30),
    reference_range TEXT,
    is_urgent      BOOLEAN NOT NULL DEFAULT FALSE,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS master_data.imaging_procedure (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(50) NOT NULL UNIQUE,
    name           VARCHAR(255) NOT NULL,
    modality       VARCHAR(50),
    body_part      VARCHAR(100),
    preparation    TEXT,
    price_qar      NUMERIC(12,2),
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS master_data.procedure_code (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(50) NOT NULL UNIQUE,
    name           VARCHAR(255) NOT NULL,
    system         VARCHAR(50) NOT NULL,
    category       VARCHAR(100),
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS master_data.diagnosis_code (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(50) NOT NULL UNIQUE,
    name           VARCHAR(255) NOT NULL,
    system         VARCHAR(50) NOT NULL DEFAULT 'ICD-10',
    chapter        VARCHAR(100),
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS master_data.charge_item (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(50) NOT NULL UNIQUE,
    name           VARCHAR(255) NOT NULL,
    category       VARCHAR(100),
    price_qar      NUMERIC(12,2) NOT NULL DEFAULT 0,
    tax_rate       NUMERIC(5,2) NOT NULL DEFAULT 0,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_md_drug_name ON master_data.drug (name);
CREATE INDEX IF NOT EXISTS idx_md_lab_name ON master_data.lab_test (name);
CREATE INDEX IF NOT EXISTS idx_md_diag_code ON master_data.diagnosis_code (code);