-- V71: Chunk-2 exit finding - doctors must be able to order and read labs (the exit
-- criterion is "doctor orders CBC"), and pathologists must be able to read the orders
-- they verify. Collect/verify stay with the lab roles (V63 boundary unchanged).
-- Rollback note: delete the matching identity.role_permission rows to revert.

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'laboratory' AND p.action IN ('read', 'write') AND p.resource = 'lab'
WHERE r.name = 'DOCTOR'
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'laboratory' AND p.action = 'read' AND p.resource = 'lab'
WHERE r.name = 'PATHOLOGIST'
ON CONFLICT DO NOTHING;
