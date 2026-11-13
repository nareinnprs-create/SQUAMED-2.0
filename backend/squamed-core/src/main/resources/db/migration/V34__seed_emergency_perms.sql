-- V34: Seed Emergency permissions
-- Module: emergency

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'emergency', 'read',  'emergency', 'View emergency registrations, triage and trauma workflow'),
    (gen_random_uuid(), 'emergency', 'write', 'emergency', 'Register, triage and manage emergency patients')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.domain = 'emergency'
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'emergency' AND p.action IN ('read', 'write')
WHERE r.name IN ('DOCTOR', 'NURSE')
ON CONFLICT DO NOTHING;