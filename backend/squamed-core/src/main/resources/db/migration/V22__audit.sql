-- V22: Audit schema and seed permissions
-- Module: audit

CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE IF NOT EXISTS audit.audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID,
    actor_user_id   UUID,
    actor_email     VARCHAR(255),
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(100) NOT NULL,
    entity_id       VARCHAR(100),
    resource        VARCHAR(255),
    ip_address      VARCHAR(64),
    user_agent      VARCHAR(400),
    method          VARCHAR(10),
    path            VARCHAR(255),
    request_payload TEXT,
    response_status INTEGER,
    response_payload TEXT,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_audit_log_entity ON audit.audit_log (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_actor ON audit.audit_log (actor_user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_occurred_at ON audit.audit_log (occurred_at);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    ('00000000-0000-0000-0000-00000000f101', 'audit', 'read',   'audit', 'Read audit logs'),
    ('00000000-0000-0000-0000-00000000f102', 'audit', 'export', 'audit', 'Export audit logs')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.domain = 'audit'
ON CONFLICT DO NOTHING;