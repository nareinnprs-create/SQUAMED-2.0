-- V86: command-bar action drafts (chunk-05 section 05.03)
-- AI never executes: it proposes a draft; a human approves through the governed platform API.
-- Rollback note: additive only - drop ai.action_draft.

CREATE TABLE IF NOT EXISTS ai.action_draft (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    proposed_by     UUID NOT NULL,
    action_type     VARCHAR(40) NOT NULL,
    summary         VARCHAR(400) NOT NULL,
    payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(20) NOT NULL DEFAULT 'PROPOSED',
    expires_at      TIMESTAMPTZ NOT NULL,
    decided_at      TIMESTAMPTZ,
    decided_by      UUID,
    decision_reason VARCHAR(400),
    result_ref      VARCHAR(120),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ai_action_draft_hospital_status ON ai.action_draft (hospital_id, status, expires_at);
