-- V28: AI Scribe sessions
-- Module: scribe

CREATE SCHEMA IF NOT EXISTS scribe;

CREATE TABLE IF NOT EXISTS scribe.scribe_session (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID,
    patient_id        UUID,
    encounter_id      UUID,
    patient_name      VARCHAR(255),
    chief_complaint   TEXT,
    source            VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    stage             VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    transcript        TEXT,
    segments          TEXT,
    speaker_map       TEXT,
    draft             TEXT,
    model_provider    VARCHAR(30),
    model_name        VARCHAR(160),
    duration_seconds  INTEGER NOT NULL DEFAULT 0,
    signed_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_scribe_session_encounter ON scribe.scribe_session (encounter_id);
CREATE INDEX IF NOT EXISTS idx_scribe_session_patient ON scribe.scribe_session (patient_id);
CREATE INDEX IF NOT EXISTS idx_scribe_session_created ON scribe.scribe_session (created_at);