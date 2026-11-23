-- V82: Grant SYSTEM_ADMIN the consent-management permission (V51 granted it only to
-- DOCTOR and NURSE; V25's catch-all predates the permission, so the platform admin could
-- not capture or withdraw consents - found by the 04.06 surgical consent gate PIT).
-- Rollback note: delete the matching identity.role_permission row to revert.

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'consent' AND p.action = 'manage' AND p.resource = 'consent'
WHERE r.name = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;
