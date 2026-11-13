-- V12: Encounter & OPD Clinical schema
-- Module: squamed-encounter / squamed-clinical-opd

CREATE TABLE IF NOT EXISTS clinical.encounter (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID,
    visit_id        UUID,
    patient_id      UUID NOT NULL,
    doctor_id       UUID,
    department_id   UUID,
    encounter_type  VARCHAR(30) NOT NULL DEFAULT 'outpatient',
    status          VARCHAR(20) NOT NULL DEFAULT 'in-progress',
    reason          TEXT,
    chief_complaint TEXT,
    started_at      TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS clinical.vital_sign (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id    UUID,
    patient_id      UUID NOT NULL,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    recorded_by     UUID,
    blood_pressure_systolic INTEGER,
    blood_pressure_diastolic INTEGER,
    heart_rate      INTEGER,
    respiratory_rate INTEGER,
    temperature_c   NUMERIC(4,1),
    spo2_percent    INTEGER,
    height_cm       NUMERIC(5,1),
    weight_kg       NUMERIC(5,1),
    bmi             NUMERIC(4,1),
    pain_score      INTEGER,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS clinical.clinical_note (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id    UUID NOT NULL REFERENCES clinical.encounter(id) ON DELETE CASCADE,
    patient_id      UUID NOT NULL,
    note_type       VARCHAR(30) NOT NULL DEFAULT 'progress',
    title           VARCHAR(200),
    subjective      TEXT,
    objective       TEXT,
    assessment      TEXT,
    plan            TEXT,
    is_signed       BOOLEAN NOT NULL DEFAULT FALSE,
    signed_at       TIMESTAMPTZ,
    signed_by       UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS clinical.order_entry (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID,
    encounter_id    UUID,
    patient_id      UUID NOT NULL,
    order_type      VARCHAR(30) NOT NULL,
    order_code      VARCHAR(50),
    order_name      VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    priority        VARCHAR(20) NOT NULL DEFAULT 'routine',
    instructions    TEXT,
    ordered_by      UUID,
    ordered_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS clinical.diagnosis (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id    UUID,
    patient_id      UUID NOT NULL,
    code            VARCHAR(50) NOT NULL,
    system          VARCHAR(20) NOT NULL DEFAULT 'ICD-10',
    name            VARCHAR(255) NOT NULL,
    type            VARCHAR(20) NOT NULL DEFAULT 'working',
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS clinical.prescription (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id    UUID,
    patient_id      UUID NOT NULL,
    drug_id         UUID,
    drug_name       VARCHAR(255) NOT NULL,
    strength        VARCHAR(100),
    form            VARCHAR(50),
    dose            VARCHAR(100),
    frequency       VARCHAR(100),
    route           VARCHAR(50),
    duration        VARCHAR(50),
    instructions    TEXT,
    quantity        INTEGER,
    is_prn          BOOLEAN NOT NULL DEFAULT FALSE,
    status          VARCHAR(20) NOT NULL DEFAULT 'prescribed',
    prescribed_by   UUID,
    prescribed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_clin_enc_patient ON clinical.encounter (patient_id);
CREATE INDEX IF NOT EXISTS idx_clin_vitals_patient ON clinical.vital_sign (patient_id);
CREATE INDEX IF NOT EXISTS idx_clin_notes_encounter ON clinical.clinical_note (encounter_id);
CREATE INDEX IF NOT EXISTS idx_clin_orders_encounter ON clinical.order_entry (encounter_id);
CREATE INDEX IF NOT EXISTS idx_clin_prescription_encounter ON clinical.prescription (encounter_id);