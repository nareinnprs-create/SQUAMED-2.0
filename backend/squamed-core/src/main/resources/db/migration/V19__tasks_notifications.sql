-- V19: Tasks + Notifications schemas and seed permissions
-- Module: tasks, notifications

CREATE TABLE IF NOT EXISTS tasks.task (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID,
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    assignee_id  UUID,
    assigner_id  UUID,
    status       VARCHAR(20) NOT NULL DEFAULT 'pending',
    priority     VARCHAR(20) NOT NULL DEFAULT 'normal',
    due_at       TIMESTAMPTZ,
    related_type VARCHAR(50),
    related_id   UUID,
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_task_assignee_status ON tasks.task (assignee_id, status);

CREATE TABLE IF NOT EXISTS notifications.notification (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID,
    recipient_id UUID NOT NULL,
    channel      VARCHAR(30) NOT NULL DEFAULT 'in_app',
    type         VARCHAR(50) NOT NULL,
    title        VARCHAR(255) NOT NULL,
    body         TEXT,
    payload      TEXT,
    read_at      TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_notification_recipient_read_at ON notifications.notification (recipient_id, read_at);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    ('00000000-0000-0000-0000-00000000f001', 'task',         'manage', 'task',         'Manage tasks and assignments'),
    ('00000000-0000-0000-0000-00000000f002', 'notification', 'read',   'notification', 'Read notifications')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.domain IN ('task', 'notification')
ON CONFLICT DO NOTHING;
