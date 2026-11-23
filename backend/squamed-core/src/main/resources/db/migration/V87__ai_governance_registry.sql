-- V87: AI governance registry (chunk-05 section 05.07)
-- Agent scopes (the permissions an agent may act with), eval results attached to releases,
-- the incident register, and the cost aggregation indexes behind the tenant dashboard.
-- Rollback note: additive only - drop ai.eval_run / ai.incident and the agent scopes column.

ALTER TABLE ai.agent_config
    ADD COLUMN IF NOT EXISTS scopes VARCHAR(500);

CREATE TABLE IF NOT EXISTS ai.eval_run (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID NOT NULL,
    capability    VARCHAR(30) NOT NULL,
    subject       VARCHAR(160) NOT NULL,
    suite_name    VARCHAR(160) NOT NULL,
    cases_passed  INTEGER NOT NULL,
    cases_total   INTEGER NOT NULL,
    score         NUMERIC(5,2) NOT NULL,
    verdict       VARCHAR(20) NOT NULL,
    notes         VARCHAR(400),
    recorded_by   UUID,
    recorded_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ai_eval_run_subject ON ai.eval_run (hospital_id, subject, recorded_at DESC);

CREATE TABLE IF NOT EXISTS ai.incident (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID NOT NULL,
    capability    VARCHAR(30) NOT NULL,
    severity      VARCHAR(20) NOT NULL,
    summary       VARCHAR(300) NOT NULL,
    detail        TEXT,
    status        VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    reported_by   UUID,
    reported_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at     TIMESTAMPTZ,
    resolution    VARCHAR(400),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ai_incident_hospital_status ON ai.incident (hospital_id, status, reported_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_interaction_hospital_model_day
    ON ai.interaction_log (hospital_id, model_name, created_at);
