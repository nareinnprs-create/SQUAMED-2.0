-- V3: Seed core permissions and grant SYSTEM_ADMIN full access
-- Module: squamed-identity

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'identity',   'read',   'user',        'Read users'),
    (gen_random_uuid(), 'identity',   'write',  'user',        'Create and update users'),
    (gen_random_uuid(), 'identity',   'manage', 'role',        'Manage roles and permissions'),
    (gen_random_uuid(), 'identity',   'read',   'role',        'Read roles and permissions'),
    (gen_random_uuid(), 'patient',    'read',   'patient',     'Read patient records'),
    (gen_random_uuid(), 'patient',    'write',  'patient',     'Create and update patients'),
    (gen_random_uuid(), 'patient',    'merge',  'patient',     'Merge patient records'),
    (gen_random_uuid(), 'appointment','read',   'appointment', 'Read appointments'),
    (gen_random_uuid(), 'appointment','write',  'appointment', 'Create and update appointments'),
    (gen_random_uuid(), 'queue',      'manage', 'queue',       'Manage queues and call tokens'),
    (gen_random_uuid(), 'clinical',   'read',   'encounter',   'Read encounters'),
    (gen_random_uuid(), 'clinical',   'write',  'encounter',   'Document encounters and notes'),
    (gen_random_uuid(), 'clinical',   'manage', 'referral',    'Manage referrals'),
    (gen_random_uuid(), 'orders',     'write',  'order',       'Create and manage orders'),
    (gen_random_uuid(), 'laboratory', 'write',  'result',      'Enter and validate lab results'),
    (gen_random_uuid(), 'laboratory', 'release','result',      'Release lab results'),
    (gen_random_uuid(), 'pharmacy',   'write',  'prescription','Verify and dispense prescriptions'),
    (gen_random_uuid(), 'billing',    'read',   'invoice',     'View invoices'),
    (gen_random_uuid(), 'billing',    'write',  'invoice',     'Create invoices'),
    (gen_random_uuid(), 'billing',    'collect','payment',     'Collect payments'),
    (gen_random_uuid(), 'task',       'manage', 'task',        'Manage tasks and assignments'),
    (gen_random_uuid(), 'report',     'read',   'report',      'View reports and analytics'),
    (gen_random_uuid(), 'master-data','manage', 'catalog',     'Manage master data catalogs')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.name = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'patient'
WHERE r.name = 'DOCTOR' AND p.action IN ('read', 'write')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'clinical'
WHERE r.name = 'DOCTOR' AND p.action IN ('read', 'write', 'manage')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'appointment'
WHERE r.name = 'DOCTOR' AND p.action IN ('read', 'write')
ON CONFLICT DO NOTHING;