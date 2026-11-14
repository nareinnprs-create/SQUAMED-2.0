-- V50: Task engine (00.11) — subject links, SLA escalation columns, task workforce grants.
-- Chunk item 9: create -> assign -> execute -> monitor -> escalate -> complete; subject
-- attachments (patient, encounter placeholder, invoice placeholder, department); SLA +
-- escalation rules from config.

ALTER TABLE tasks.task
    ADD COLUMN IF NOT EXISTS escalated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS escalation_count INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS tasks.task_link (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID,
    task_id      UUID NOT NULL,
    subject_type VARCHAR(30) NOT NULL,
    subject_id   UUID NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_task_link_task FOREIGN KEY (task_id) REFERENCES tasks.task (id) ON DELETE CASCADE
);

-- A subject is attached to a task at most once.
CREATE UNIQUE INDEX IF NOT EXISTS uq_task_link_task_subject
    ON tasks.task_link (task_id, subject_type, subject_id);

-- Reverse lookup: "all tasks attached to this patient/invoice/department".
CREATE INDEX IF NOT EXISTS idx_task_link_subject
    ON tasks.task_link (subject_type, subject_id);

-- The task workforce: doctors and nurses read/act on their inbox (00.11).
INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'task' AND p.action = 'manage' AND p.resource = 'task'
WHERE r.name IN ('DOCTOR', 'NURSE')
ON CONFLICT DO NOTHING;
