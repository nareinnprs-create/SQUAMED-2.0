-- V101: voice operating interface (chunk-07 section 07.02)
-- Voice commands are parsed into governed intents. Low-risk intents may be approved by voice;
-- high-risk clinical/financial categories are hard-excluded and require the approval UI.
-- Rollback note: additive only - drop ai.voice_command.

CREATE TABLE IF NOT EXISTS ai.voice_command (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id            UUID NOT NULL,
    user_id                UUID,
    transcript             VARCHAR(600) NOT NULL,
    locale                 VARCHAR(10) NOT NULL DEFAULT 'en',
    screen                 VARCHAR(80),
    patient_id             UUID,
    intent                 VARCHAR(40) NOT NULL,
    confidence             NUMERIC(5,2) NOT NULL DEFAULT 0,
    risk_class             VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    status                 VARCHAR(30) NOT NULL DEFAULT 'ESCALATED',
    voice_approval_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    response               VARCHAR(600),
    target                 VARCHAR(120),
    search_term            VARCHAR(200),
    escalated_reason       VARCHAR(300),
    action_draft_id        UUID,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by             UUID,
    updated_by             UUID,
    version                BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_voice_command_hospital ON ai.voice_command (hospital_id, created_at);
