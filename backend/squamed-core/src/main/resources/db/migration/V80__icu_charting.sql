-- V80: ICU charting (chunk-04 section 04.05)
-- Severity scores (SOFA components + total, informational), plus read-model support indexes for
-- the hourly flowsheet and fluid-balance aggregation. Alerts already exist; ThresholdAlertRaised
-- is emitted on alert creation (routing hook).
-- Rollback note: additive only - drop icu.severity_score and the two indexes.

CREATE TABLE IF NOT EXISTS icu.severity_score (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL,
    patient_id     UUID NOT NULL,
    admission_id   UUID,
    score_type     VARCHAR(10) NOT NULL DEFAULT 'SOFA' CHECK (score_type IN ('SOFA', 'GCS')),
    respiration    INTEGER NOT NULL DEFAULT 0,
    coagulation    INTEGER NOT NULL DEFAULT 0,
    liver          INTEGER NOT NULL DEFAULT 0,
    cardiovascular INTEGER NOT NULL DEFAULT 0,
    cns            INTEGER NOT NULL DEFAULT 0,
    renal          INTEGER NOT NULL DEFAULT 0,
    total          INTEGER NOT NULL,
    inputs_json    TEXT,
    recorded_by    UUID,
    recorded_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_icu_vitals_patient_time ON icu.icu_vitals_entry (patient_id, recorded_at);
CREATE INDEX IF NOT EXISTS idx_icu_fluid_patient_time ON icu.icu_fluid_balance (patient_id, balance_at);
CREATE INDEX IF NOT EXISTS idx_icu_severity_patient_time ON icu.severity_score (patient_id, recorded_at);
