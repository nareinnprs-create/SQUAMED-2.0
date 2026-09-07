-- V21: Grant communication read/message to clinical roles
-- Doctors and nurses can read + send channel messages (not manage channels)

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.name IN ('DOCTOR', 'NURSE')
  AND p.domain = 'communication'
  AND p.action = 'read'
  AND p.resource = 'message'
ON CONFLICT DO NOTHING;
