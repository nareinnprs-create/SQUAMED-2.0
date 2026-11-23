-- V88: intelligence rail attention items (chunk-05 section 05.04)
-- Items are derived from governed platform state (overdue discharge, delayed OT, pending AI
-- approvals, overdue tasks); dedupe_key stops the same item re-raising after a dismissal.
-- Rollback note: additive only - drop ai.attention_item.

CREATE TABLE IF NOT EXISTS ai.attention_item (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id      UUID NOT NULL,
    item_type        VARCHAR(40) NOT NULL,
    severity         VARCHAR(20) NOT NULL,
    title            VARCHAR(300) NOT NULL,
    detail           VARCHAR(500),
    dedupe_key       VARCHAR(200) NOT NULL,
    source_type      VARCHAR(40) NOT NULL,
    source_id        UUID,
    status           VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    raised_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    cleared_at       TIMESTAMPTZ,
    dismissal_reason VARCHAR(300),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, dedupe_key)
);

CREATE INDEX IF NOT EXISTS idx_ai_attention_hospital_status ON ai.attention_item (hospital_id, status, severity);
