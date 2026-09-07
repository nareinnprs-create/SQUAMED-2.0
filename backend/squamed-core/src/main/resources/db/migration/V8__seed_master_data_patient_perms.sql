-- V8: Seed master data + patient permissions
-- Module: master-data, patient (identity)

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'master-data', 'read',   'catalog',     'Read master data catalogs'),
    (gen_random_uuid(), 'master-data', 'write',  'catalog',     'Manage master data catalogs'),
    (gen_random_uuid(), 'patient',     'read',   'patient',     'Read patient records'),
    (gen_random_uuid(), 'patient',     'write',  'patient',     'Create and update patients'),
    (gen_random_uuid(), 'patient',     'merge',  'patient',     'Merge patient records')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.domain IN ('master-data', 'patient')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'patient' AND p.action IN ('read', 'write')
WHERE r.name = 'DOCTOR'
ON CONFLICT DO NOTHING;