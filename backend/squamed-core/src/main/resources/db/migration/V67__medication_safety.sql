-- V67: Medication safety engine (chunk-02 section 02.07)
-- Dual-source interaction rules (DATASET + RULE, provenance + confidence on every rule;
-- BLOCK fires only at >= confidence threshold), dose rules (peds weight-based cap, adult
-- cap, renal eGFR flag), allergen class expansion (ingredient-level allergy mapping),
-- safety overrides (mandatory reason, audited, KPI-tracked). Drug master gains ATC code
-- and ingredient tokens (CSV).
-- Rollback note: additive only — drop schema medication cascade, drop the added
-- master_data.drug columns, delete identity.permission rows (domain 'medsafety') and
-- their role_permission grants to revert.

ALTER TABLE master_data.drug
    ADD COLUMN IF NOT EXISTS atc_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS ingredients TEXT;

CREATE SCHEMA IF NOT EXISTS medication;

CREATE TABLE IF NOT EXISTS medication.interaction_rule (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    drug_a_code    VARCHAR(50) NOT NULL,
    drug_b_code    VARCHAR(50) NOT NULL,
    severity       VARCHAR(10) NOT NULL CHECK (severity IN ('BLOCK','WARN','INFO')),
    confidence     DOUBLE PRECISION NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    source         VARCHAR(20) NOT NULL CHECK (source IN ('DATASET','RULE')),
    description    VARCHAR(500),
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_inter_rule_a ON medication.interaction_rule (drug_a_code, drug_b_code);
CREATE INDEX IF NOT EXISTS idx_inter_rule_b ON medication.interaction_rule (drug_b_code, drug_a_code);

CREATE TABLE IF NOT EXISTS medication.dose_rule (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    drug_code              VARCHAR(50) NOT NULL,
    max_daily_mg_per_kg    DOUBLE PRECISION,
    adult_max_daily_mg     DOUBLE PRECISION,
    renal_flag             BOOLEAN NOT NULL DEFAULT FALSE,
    renal_egfr_threshold   DOUBLE PRECISION,
    note                   VARCHAR(400),
    is_active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by             UUID,
    updated_by             UUID,
    version                BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_dose_rule_code ON medication.dose_rule (drug_code);

-- Class expansion: allergen token -> ingredient tokens it must be checked against
-- (e.g. penicillin -> amoxicillin, ampicillin, ...).
CREATE TABLE IF NOT EXISTS medication.allergen_class_map (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    allergen_token    VARCHAR(100) NOT NULL,
    ingredient_token  VARCHAR(100) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_allergen_map_token ON medication.allergen_class_map (allergen_token);

CREATE TABLE IF NOT EXISTS medication.safety_override (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL,
    patient_id     UUID NOT NULL,
    drug_code      VARCHAR(50),
    finding_type   VARCHAR(20) NOT NULL CHECK (finding_type IN ('INTERACTION','ALLERGY','DUPLICATE','MAX_DOSE','RENAL')),
    severity       VARCHAR(10) NOT NULL CHECK (severity IN ('BLOCK','WARN','INFO')),
    reason         VARCHAR(400) NOT NULL,
    overridden_by  UUID,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_safety_override_kpi ON medication.safety_override (hospital_id, occurred_at);

-- Chunk-02 permissions: clinicians/pharmacists run checks; overrides need a typed reason
-- and are audited (00-MASTER-PLAN §5 rule 4).
INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'medsafety', 'check',    'medication', 'Run medication safety checks'),
    (gen_random_uuid(), 'medsafety', 'override', 'medication', 'Record audited medication safety overrides')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'medsafety'
WHERE r.name IN ('DOCTOR', 'PHARMACIST')
ON CONFLICT DO NOTHING;
