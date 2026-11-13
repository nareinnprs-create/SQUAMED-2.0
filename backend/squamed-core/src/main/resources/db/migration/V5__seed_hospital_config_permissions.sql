-- V5: Seed hospital-config permissions and grant SYSTEM_ADMIN access
-- Module: squamed-hospital-config

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'hospital-config', 'read',  'hospital',   'Read hospital configuration'),
    (gen_random_uuid(), 'hospital-config', 'write', 'hospital',   'Create and update hospitals'),
    (gen_random_uuid(), 'hospital-config', 'read',  'department', 'Read departments'),
    (gen_random_uuid(), 'hospital-config', 'write', 'department', 'Create and update departments'),
    (gen_random_uuid(), 'hospital-config', 'read',  'specialty',  'Read specialties'),
    (gen_random_uuid(), 'hospital-config', 'write', 'specialty',  'Create and update specialties'),
    (gen_random_uuid(), 'hospital-config', 'read',  'ward',       'Read wards'),
    (gen_random_uuid(), 'hospital-config', 'write', 'ward',       'Create and update wards'),
    (gen_random_uuid(), 'hospital-config', 'read',  'bed',        'Read beds'),
    (gen_random_uuid(), 'hospital-config', 'write', 'bed',        'Create and update beds'),
    (gen_random_uuid(), 'hospital-config', 'read',  'service',    'Read hospital services'),
    (gen_random_uuid(), 'hospital-config', 'write', 'service',    'Create and update hospital services')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.domain = 'hospital-config'
ON CONFLICT DO NOTHING;