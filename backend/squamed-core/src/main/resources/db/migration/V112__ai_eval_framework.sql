-- V112: AI eval framework (chunk-05 section 05.02)
-- Datasets hold the cases; ai.eval_run (V87) already stores the aggregate result of a run.
-- The gate thresholds themselves are code constants (AiEvalFramework.GATES), not rows, so a
-- hospital cannot loosen the gate that CI enforces.
-- Rollback note: additive only - drop ai.eval_case / ai.eval_dataset and the four eval_run columns.

ALTER TABLE ai.eval_run
    ADD COLUMN IF NOT EXISTS dataset_id UUID;

ALTER TABLE ai.eval_run
    ADD COLUMN IF NOT EXISTS pass_at_k NUMERIC(5, 2);

ALTER TABLE ai.eval_run
    ADD COLUMN IF NOT EXISTS k INTEGER;

ALTER TABLE ai.eval_run
    ADD COLUMN IF NOT EXISTS detail TEXT;

CREATE TABLE IF NOT EXISTS ai.eval_dataset (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    code            VARCHAR(60) NOT NULL,
    capability      VARCHAR(30) NOT NULL,
    dataset_version INTEGER NOT NULL DEFAULT 1,
    description     VARCHAR(300),
    case_count      INTEGER NOT NULL DEFAULT 0,
    source          VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_eval_dataset_code_version
    ON ai.eval_dataset (hospital_id, code, dataset_version);

CREATE TABLE IF NOT EXISTS ai.eval_case (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id       UUID NOT NULL REFERENCES ai.eval_dataset (id) ON DELETE CASCADE,
    ordinal          INTEGER NOT NULL,
    input_text       TEXT NOT NULL,
    expected         TEXT NOT NULL,
    forbidden_token  VARCHAR(200),
    related_type     VARCHAR(60),
    critical         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ai_eval_case_dataset ON ai.eval_case (dataset_id, ordinal);
