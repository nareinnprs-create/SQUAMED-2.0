-- V25: Grant SYSTEM_ADMIN every permission (Phase 1 all-access admin role)
-- Ensures the built-in admin can exercise every module incl. FHIR/HL7 read gates.

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.name = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;