-- V85: AI gateway governance (chunk-05 section 05.01)
-- Per-capability kill switch + daily token caps, full request trace (capability, prompt version,
-- prompt/completion tokens, cost, redaction flag) and model pricing for the cost trace.
-- Rollback note: additive only - drop ai.gateway_policy and the added columns.

CREATE TABLE IF NOT EXISTS ai.gateway_policy (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id      UUID NOT NULL,
    capability       VARCHAR(30) NOT NULL,
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    daily_token_cap  INTEGER NOT NULL DEFAULT 200000,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, capability)
);

ALTER TABLE ai.interaction_log
    ADD COLUMN IF NOT EXISTS capability VARCHAR(30) NOT NULL DEFAULT 'chat',
    ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(120),
    ADD COLUMN IF NOT EXISTS prompt_tokens INTEGER,
    ADD COLUMN IF NOT EXISTS completion_tokens INTEGER,
    ADD COLUMN IF NOT EXISTS cost_usd NUMERIC(14,6) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS redacted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_ai_interaction_capability_day
    ON ai.interaction_log (hospital_id, capability, created_at);

ALTER TABLE ai.model_config
    ADD COLUMN IF NOT EXISTS cost_per_1k_tokens NUMERIC(10,6) NOT NULL DEFAULT 0;

-- Clinicians may use the assistant; every AI capability still runs with the caller's scopes.
INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'ai' AND p.action = 'chat'
WHERE r.name IN ('DOCTOR', 'NURSE')
ON CONFLICT DO NOTHING;
