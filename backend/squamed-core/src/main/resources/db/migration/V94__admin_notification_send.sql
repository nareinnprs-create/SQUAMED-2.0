-- V94: SYSTEM_ADMIN notification send (found by the 06.03 portal PIT)
-- V51 granted notification:send to DOCTOR/NURSE only; the platform admin could not send any
-- outbound notification (403). Rollback note: delete this single role_permission row.

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'notification' AND p.action = 'send' AND p.resource = 'notification'
WHERE r.name = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;
