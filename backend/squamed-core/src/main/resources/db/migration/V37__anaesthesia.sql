-- V37: Anaesthesia schema
-- Module: anaesthesia (evaluation, record, intra-op monitoring, medication, recovery, PACU)

CREATE SCHEMA IF NOT EXISTS anaesthesia;

CREATE TABLE IF NOT EXISTS anaesthesia.anaesthesia_evaluation (
    id                                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id                            UUID NOT NULL,
    surgery_schedule_id                    UUID NOT NULL,
    patient_id                             UUID NOT NULL,
    asa_status                             VARCHAR(5) NOT NULL,
    airway_assessment                      TEXT,
    allergies                              TEXT,
    medical_conditions                     TEXT,
    medications                            TEXT,
    previous_anaesthesia_complications     TEXT,
    fasting_hours                          INT,
    lab_notes                              TEXT,
    risk_level                             VARCHAR(20),
    status                                 VARCHAR(20) NOT NULL DEFAULT 'draft',
    evaluated_by                           UUID,
    evaluated_at                           TIMESTAMPTZ,
    created_at                             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                             UUID,
    updated_by                             UUID,
    version                                BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_anaesthesia_eval_schedule ON anaesthesia.anaesthesia_evaluation (surgery_schedule_id);

CREATE TABLE IF NOT EXISTS anaesthesia.anaesthesia_record (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID NOT NULL,
    surgery_schedule_id UUID NOT NULL,
    patient_id        UUID NOT NULL,
    anaesthetist_id   UUID,
    airway_device     VARCHAR(30),
    intubation_grade  VARCHAR(20),
    status            VARCHAR(30) NOT NULL DEFAULT 'planned',
    started_at        TIMESTAMPTZ,
    ended_at          TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_anaesthesia_record_schedule ON anaesthesia.anaesthesia_record (surgery_schedule_id);

CREATE TABLE IF NOT EXISTS anaesthesia.intra_op_monitoring (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID NOT NULL,
    surgery_schedule_id UUID NOT NULL,
    anaesthesia_record_id UUID NOT NULL,
    patient_id        UUID NOT NULL,
    monitored_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    systolic          INT,
    diastolic         INT,
    heart_rate        INT NOT NULL,
    oxygen_saturation INT,
    etco2             INT,
    temperature       DOUBLE PRECISION,
    hourly_urine_ml   INT,
    anaesthesia_depth VARCHAR(30),
    notes             TEXT,
    recorded_by       UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_anaesthesia_monitoring_record ON anaesthesia.intra_op_monitoring (anaesthesia_record_id);

CREATE TABLE IF NOT EXISTS anaesthesia.anaesthesia_medication (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id          UUID NOT NULL,
    surgery_schedule_id  UUID NOT NULL,
    anaesthesia_record_id UUID NOT NULL,
    patient_id           UUID NOT NULL,
    medication_name      VARCHAR(255) NOT NULL,
    dose                 VARCHAR(50),
    route                VARCHAR(30),
    given_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    given_by             UUID,
    notes                TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_anaesthesia_med_record ON anaesthesia.anaesthesia_medication (anaesthesia_record_id);

CREATE TABLE IF NOT EXISTS anaesthesia.recovery_record (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id          UUID NOT NULL,
    surgery_schedule_id  UUID NOT NULL,
    patient_id           UUID NOT NULL,
    aldrete_score        INT NOT NULL,
    discharge_criteria_met BOOLEAN NOT NULL DEFAULT FALSE,
    recovery_started_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    recovery_ended_at    TIMESTAMPTZ,
    observations         TEXT,
    recorded_by          UUID,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_anaesthesia_recovery_schedule ON anaesthesia.recovery_record (surgery_schedule_id);

CREATE TABLE IF NOT EXISTS anaesthesia.pacu_record (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id          UUID NOT NULL,
    surgery_schedule_id  UUID NOT NULL,
    patient_id           UUID NOT NULL,
    nurse_assignment     VARCHAR(255),
    pain_score           INT,
    nausea_score         INT,
    bleeding_notes       TEXT,
    oxygen_saturation    INT,
    heart_rate           INT,
    systolic             INT,
    diastolic            INT,
    notes                TEXT,
    recorded_by          UUID,
    recorded_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_anaesthesia_pacu_schedule ON anaesthesia.pacu_record (surgery_schedule_id);