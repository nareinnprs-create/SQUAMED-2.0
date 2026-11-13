-- V30: Nursing schema
-- Module: nursing (assessment, careplan, note, handover, vitals, eMAR, risk assessment)

CREATE SCHEMA IF NOT EXISTS nursing;

CREATE TABLE IF NOT EXISTS nursing.nursing_assessment (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    patient_id      UUID NOT NULL,
    admission_id    UUID,
    assessment_type VARCHAR(40) NOT NULL,
    findings        TEXT,
    risk_level      VARCHAR(20),
    assessed_by     UUID,
    assessed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_nursing_assessment_patient ON nursing.nursing_assessment (patient_id);

CREATE TABLE IF NOT EXISTS nursing.nursing_careplan (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    patient_id      UUID NOT NULL,
    admission_id    UUID,
    title           VARCHAR(255) NOT NULL,
    goal            TEXT,
    interventions   TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'active',
    created_by      UUID,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_nursing_careplan_patient ON nursing.nursing_careplan (patient_id);

CREATE TABLE IF NOT EXISTS nursing.nursing_note (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    patient_id      UUID NOT NULL,
    admission_id    UUID,
    note_type       VARCHAR(30) NOT NULL DEFAULT 'progress',
    content         TEXT NOT NULL,
    authored_by     UUID,
    authored_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_nursing_note_patient ON nursing.nursing_note (patient_id);

CREATE TABLE IF NOT EXISTS nursing.nursing_handover (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    ward_id         UUID,
    shift           VARCHAR(20) NOT NULL,
    situation       TEXT,
    background      TEXT,
    assessment      TEXT,
    recommendation  TEXT,
    authored_by     UUID,
    authored_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_nursing_handover_ward_shift ON nursing.nursing_handover (ward_id, shift);

CREATE TABLE IF NOT EXISTS nursing.vitals_entry (
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
    gcs               INT,
    recorded_by       UUID,
    recorded_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_nursing_vitals_patient ON nursing.vitals_entry (patient_id, recorded_at);

CREATE TABLE IF NOT EXISTS nursing.medication_administration_record (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    patient_id      UUID NOT NULL,
    admission_id    UUID,
    medication_name VARCHAR(255) NOT NULL,
    dose            VARCHAR(100),
    route           VARCHAR(30),
    scheduled_at    TIMESTAMPTZ NOT NULL,
    administered_at TIMESTAMPTZ,
    administered_by UUID,
    omitted_reason  TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'scheduled',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_nursing_mar_patient ON nursing.medication_administration_record (patient_id, scheduled_at);

CREATE TABLE IF NOT EXISTS nursing.risk_assessment (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    patient_id      UUID NOT NULL,
    admission_id    UUID,
    risk_type       VARCHAR(40) NOT NULL,
    score           INT,
    level           VARCHAR(20),
    assessed_by     UUID,
    assessed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_nursing_risk_patient ON nursing.risk_assessment (patient_id);