-- V17: Seed permissions for laboratory, pharmacy, billing
-- Module: mixed

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'laboratory', 'read',    'lab',          'Read lab orders and results'),
    (gen_random_uuid(), 'laboratory', 'write',   'lab',          'Create and manage lab orders'),
    (gen_random_uuid(), 'laboratory', 'collect', 'lab',          'Collect specimens'),
    (gen_random_uuid(), 'laboratory', 'verify',  'lab',          'Verify and release lab results'),
    (gen_random_uuid(), 'pharmacy',   'read',    'stock',        'Read pharmacy stock'),
    (gen_random_uuid(), 'pharmacy',   'write',   'stock',        'Manage pharmacy stock'),
    (gen_random_uuid(), 'pharmacy',   'dispense','prescription', 'Dispense prescriptions'),
    (gen_random_uuid(), 'billing',    'read',    'invoice',      'Read charges and invoices'),
    (gen_random_uuid(), 'billing',    'write',   'invoice',      'Post charges, issue invoices'),
    (gen_random_uuid(), 'billing',    'collect', 'payment',      'Collect payments')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.domain IN ('laboratory', 'pharmacy', 'billing')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'laboratory'
WHERE r.name = 'LAB_TECHNICIAN'
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'pharmacy'
WHERE r.name = 'PHARMACIST'
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'billing'
WHERE r.name = 'BILLING_STAFF'
ON CONFLICT DO NOTHING;