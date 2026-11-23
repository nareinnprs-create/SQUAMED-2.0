-- V79: Assessments + care plans + risk scores (chunk-04 section 04.03)
-- Configurable assessment forms (template + captured fields), care-plan templates with
-- problem -> intervention -> goal applied to a patient, and configurable risk scales that
-- derive the level from the score (falls / pressure ulcer).
-- Rollback note: additive only - drop the three template/scale tables and the added columns.

CREATE TABLE IF NOT EXISTS nursing.assessment_template (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    code        VARCHAR(40) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    fields_json TEXT NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, code)
);

CREATE TABLE IF NOT EXISTS nursing.careplan_template (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID NOT NULL,
    code          VARCHAR(40) NOT NULL,
    name          VARCHAR(200) NOT NULL,
    problem       VARCHAR(400) NOT NULL,
    goal          VARCHAR(400),
    interventions VARCHAR(800),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, code)
);

CREATE TABLE IF NOT EXISTS nursing.risk_scale (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL,
    code         VARCHAR(40) NOT NULL,
    name         VARCHAR(200) NOT NULL,
    min_score    INTEGER NOT NULL DEFAULT 0,
    low_max      INTEGER NOT NULL,
    moderate_max INTEGER NOT NULL,
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, code)
);

ALTER TABLE nursing.nursing_assessment
    ADD COLUMN IF NOT EXISTS template_code VARCHAR(40),
    ADD COLUMN IF NOT EXISTS fields_json TEXT;

ALTER TABLE nursing.nursing_careplan
    ADD COLUMN IF NOT EXISTS problem VARCHAR(400),
    ADD COLUMN IF NOT EXISTS template_code VARCHAR(40);

ALTER TABLE nursing.risk_assessment
    ADD COLUMN IF NOT EXISTS scale_code VARCHAR(40);
