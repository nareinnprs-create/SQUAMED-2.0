-- V23: Workflow engine schema and seed permissions
-- Module: workflow

CREATE SCHEMA IF NOT EXISTS workflow;

CREATE TABLE IF NOT EXISTS workflow.workflow_definition (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID,
    code            VARCHAR(100) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    trigger_type    VARCHAR(50) NOT NULL DEFAULT 'MANUAL',
    definition_json TEXT NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    version         BIGINT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    UNIQUE (code, version)
);

CREATE INDEX IF NOT EXISTS idx_workflow_definition_status ON workflow.workflow_definition (status);

CREATE TABLE IF NOT EXISTS workflow.workflow_instance (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id      UUID,
    definition_id    UUID NOT NULL,
    definition_code  VARCHAR(100),
    status           VARCHAR(30) NOT NULL DEFAULT 'RUNNING',
    trigger_payload  TEXT,
    current_step_key VARCHAR(100),
    started_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at         TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_workflow_instance_definition ON workflow.workflow_instance (definition_id);
CREATE INDEX IF NOT EXISTS idx_workflow_instance_status ON workflow.workflow_instance (status);

CREATE TABLE IF NOT EXISTS workflow.workflow_step_instance (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id    UUID NOT NULL,
    step_key       VARCHAR(100) NOT NULL,
    step_type      VARCHAR(30) NOT NULL,
    status         VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    step_config    TEXT,
    result         TEXT,
    sequence_order INTEGER NOT NULL,
    started_at     TIMESTAMPTZ,
    ended_at       TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_workflow_step_instance ON workflow.workflow_step_instance (instance_id);
CREATE INDEX IF NOT EXISTS idx_workflow_step_instance_status ON workflow.workflow_step_instance (status);

CREATE TABLE IF NOT EXISTS workflow.workflow_approval (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    step_instance_id UUID NOT NULL,
    approver_id      UUID NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    comments         TEXT,
    decided_by       UUID,
    decided_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0
);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    ('00000000-0000-0000-0000-00000000f103', 'workflow', 'read',    'workflow', 'Read workflow definitions and instances'),
    ('00000000-0000-0000-0000-00000000f104', 'workflow', 'manage',  'workflow', 'Create, update and activate workflow definitions'),
    ('00000000-0000-0000-0000-00000000f105', 'workflow', 'approve', 'workflow', 'Decide workflow approvals')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.domain = 'workflow'
ON CONFLICT DO NOTHING;