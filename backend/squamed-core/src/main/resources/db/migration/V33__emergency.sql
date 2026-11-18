-- V33: Emergency and Trauma schema
-- Module: emergency (registration, triage, trauma workflow)
-- Rollback: DROP TABLE emergency.trauma_workflow; DROP TABLE emergency.triage_assessment; DROP TABLE emergency.emergency_registration; DROP SCHEMA emergency;

CREATE SCHEMA IF NOT EXISTS emergency;

CREATE TABLE IF NOT EXISTS emergency.emergency_registration (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id           UUID NOT NULL,
    patient_id            UUID NOT NULL,
    arrival_mode          VARCHAR(30) NOT NULL DEFAULT 'walk_in',
    arrival_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    presenting_complaint  TEXT,
    status                VARCHAR(30) NOT NULL DEFAULT 'registered',
    triage_esi            INT,
    priority              VARCHAR(20) NOT NULL DEFAULT 'routine',
    registered_by         UUID,
    admitted_admission_id UUID,
    discharged_at         TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    version               BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_emergency_reg_status ON emergency.emergency_registration (status);
CREATE INDEX IF NOT EXISTS idx_emergency_reg_patient ON emergency.emergency_registration (patient_id);

CREATE TABLE IF NOT EXISTS emergency.triage_assessment (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    registration_id UUID NOT NULL,
    patient_id      UUID NOT NULL,
    esi_level       INT NOT NULL,
    triage_category VARCHAR(50),
    pain_scale      INT,
    vital_triggers  BOOLEAN NOT NULL DEFAULT FALSE,
    acuity_notes    TEXT,
    assessed_by     UUID,
    assessed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_emergency_triage_reg ON emergency.triage_assessment (registration_id);

CREATE TABLE IF NOT EXISTS emergency.trauma_workflow (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    registration_id UUID NOT NULL,
    patient_id      UUID NOT NULL,
    survey_phase    VARCHAR(20) NOT NULL DEFAULT 'primary',
    airway          TEXT,
    breathing       TEXT,
    circulation     TEXT,
    disability      TEXT,
    exposure        TEXT,
    survey_notes    TEXT,
    activated_by    UUID,
    activated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_by    UUID,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_emergency_trauma_reg ON emergency.trauma_workflow (registration_id);