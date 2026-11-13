-- V27: AI Gateway schema and seed permissions
-- Module: ai

CREATE SCHEMA IF NOT EXISTS ai;

CREATE TABLE IF NOT EXISTS ai.model_config (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID,
    name        VARCHAR(160) NOT NULL,
    provider    VARCHAR(30) NOT NULL,
    model_name  VARCHAR(160),
    base_url    VARCHAR(500),
    api_key     VARCHAR(500),
    temperature DOUBLE PRECISION NOT NULL DEFAULT 0.2,
    max_tokens  INTEGER NOT NULL DEFAULT 512,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    is_default  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ai_model_provider ON ai.model_config (provider);

CREATE TABLE IF NOT EXISTS ai.agent_config (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID,
    code          VARCHAR(100) NOT NULL,
    name          VARCHAR(160) NOT NULL,
    description   TEXT,
    system_prompt TEXT,
    model_id      UUID,
    temperature   DOUBLE PRECISION NOT NULL DEFAULT 0.2,
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0,
    UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS ai.tool_config (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tool_code   VARCHAR(60) NOT NULL,
    name        VARCHAR(160) NOT NULL,
    description TEXT,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (tool_code)
);

CREATE TABLE IF NOT EXISTS ai.interaction_log (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID,
    agent_id          UUID,
    agent_code        VARCHAR(100),
    model_name        VARCHAR(160),
    user_id           UUID,
    user_message      TEXT,
    assistant_message TEXT,
    intent            VARCHAR(50),
    latency_ms        BIGINT,
    tokens_used       INTEGER,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ai_interaction_user ON ai.interaction_log (user_id);
CREATE INDEX IF NOT EXISTS idx_ai_interaction_created ON ai.interaction_log (created_at);

INSERT INTO ai.model_config (id, name, provider, model_name, temperature, max_tokens, enabled, is_default, created_at, updated_at, version)
VALUES ('00000000-0000-0000-0000-00000000d001', 'SQUAMED Local Assistant', 'ECHO', 'squamed-rules-v1', 0.2, 512, TRUE, TRUE, NOW(), NOW(), 0)
ON CONFLICT DO NOTHING;

INSERT INTO ai.agent_config (id, code, name, description, system_prompt, model_id, temperature, enabled, created_at, updated_at, version)
VALUES ('00000000-0000-0000-0000-00000000d002', 'ops', 'SQUAMED Operations Assistant', 'Answers operational questions and routes commands using live hospital data.', 'You are SQUAMED Operations Assistant, a concise operator for the SQUAMED hospital OS.', '00000000-0000-0000-0000-00000000d001', 0.2, TRUE, NOW(), NOW(), 0)
ON CONFLICT DO NOTHING;

INSERT INTO ai.tool_config (id, tool_code, name, description, enabled, created_at, updated_at, version)
VALUES
    ('00000000-0000-0000-0000-00000000d003', 'navigate', 'Navigate', 'Open a SQUAMED module page', TRUE, NOW(), NOW(), 0),
    ('00000000-0000-0000-0000-00000000d004', 'count', 'Count', 'Answer how-many questions about patients, encounters, appointments and more', TRUE, NOW(), NOW(), 0),
    ('00000000-0000-0000-0000-00000000d005', 'search_patients', 'Search patients', 'Find patients by name or MRN', TRUE, NOW(), NOW(), 0),
    ('00000000-0000-0000-0000-00000000d006', 'analytics', 'Analytics', 'Explain operational analytics and revenue', TRUE, NOW(), NOW(), 0),
    ('00000000-0000-0000-0000-00000000d007', 'workflow', 'Workflow', 'Report pending workflow approvals', TRUE, NOW(), NOW(), 0)
ON CONFLICT DO NOTHING;

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    ('00000000-0000-0000-0000-00000000f110', 'ai', 'read',   'agent', 'Read AI agents, models and logs'),
    ('00000000-0000-0000-0000-00000000f111', 'ai', 'chat',   'agent', 'Chat with AI agents'),
    ('00000000-0000-0000-0000-00000000f112', 'ai', 'manage', 'agent', 'Create and manage AI models and agents')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.domain = 'ai'
ON CONFLICT DO NOTHING;