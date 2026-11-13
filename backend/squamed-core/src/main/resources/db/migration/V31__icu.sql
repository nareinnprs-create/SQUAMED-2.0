-- V31: ICU / Critical Care schema
-- Module: icu (high-frequency vitals, fluid balance, ventilation, infusion, critical alerts)

CREATE SCHEMA IF NOT EXISTS icu;

CREATE TABLE IF NOT EXISTS icu.icu_vitals_entry (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID NOT NULL,
    patient_id        UUID NOT NULL,
    admission_id      UUID,
    systolic          INT,
    diastolic         INT,
    heart_rate        INT,
    respiratory_rate  INT,
    temperature       DOUBLE PRECISION,
    oxygen_saturation INT,
    map               INT,
    etco2             INT,
    gcs               INT,
    recorded_by       UUID,
    recorded_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_icu_vitals_patient ON icu.icu_vitals_entry (patient_id, recorded_at);

CREATE TABLE IF NOT EXISTS icu.icu_fluid_balance (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    patient_id      UUID NOT NULL,
    admission_id    UUID,
    input_ml        DOUBLE PRECISION,
    output_ml       DOUBLE PRECISION,
    input_type      VARCHAR(30),
    output_type     VARCHAR(30),
    balance_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    recorded_by     UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_icu_fluid_patient ON icu.icu_fluid_balance (patient_id, balance_at);

CREATE TABLE IF NOT EXISTS icu.icu_ventilation_record (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id         UUID NOT NULL,
    patient_id          UUID NOT NULL,
    admission_id        UUID,
    mode                VARCHAR(50) NOT NULL,
    fio2_percent        INT,
    peep                DOUBLE PRECISION,
    tidal_volume_ml     INT,
    respiratory_rate_set INT,
    oxygen_saturation   INT,
    status              VARCHAR(20) NOT NULL DEFAULT 'active',
    recorded_by         UUID,
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_icu_ventilation_patient ON icu.icu_ventilation_record (patient_id, recorded_at);

CREATE TABLE IF NOT EXISTS icu.icu_infusion_record (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id         UUID NOT NULL,
    patient_id          UUID NOT NULL,
    admission_id        UUID,
    drug_name           VARCHAR(255) NOT NULL,
    dose                VARCHAR(100),
    rate_ml_per_hour    DOUBLE PRECISION,
    route               VARCHAR(30),
    started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    stopped_at          TIMESTAMPTZ,
    status              VARCHAR(20) NOT NULL DEFAULT 'active',
    recorded_by         UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_icu_infusion_patient ON icu.icu_infusion_record (patient_id);

CREATE TABLE IF NOT EXISTS icu.icu_critical_alert (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    patient_id      UUID NOT NULL,
    admission_id    UUID,
    alert_type      VARCHAR(50) NOT NULL,
    severity        VARCHAR(20) NOT NULL DEFAULT 'critical',
    message         VARCHAR(500),
    payload         TEXT,
    triggered_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    acknowledged_by UUID,
    acknowledged_at TIMESTAMPTZ,
    status          VARCHAR(20) NOT NULL DEFAULT 'open',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_icu_alert_patient_status ON icu.icu_critical_alert (patient_id, status);