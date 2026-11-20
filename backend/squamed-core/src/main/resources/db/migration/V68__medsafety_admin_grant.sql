-- V68: Grant SYSTEM_ADMIN the medication-safety permissions (V25's catch-all predates them,
-- so the platform admin could not run checks or record overrides).
-- Rollback note: delete the matching identity.role_permission rows to revert.

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'medsafety'
WHERE r.name = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;
