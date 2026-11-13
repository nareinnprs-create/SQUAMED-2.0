-- V13: Seed permissions for scheduling, queue, reception, encounter, clinical
-- Module: mixed

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'scheduling',   'read',   'appointment', 'Read appointments'),
    (gen_random_uuid(), 'scheduling',   'write',  'appointment', 'Create and manage appointments'),
    (gen_random_uuid(), 'scheduling',   'confirm','appointment', 'Confirm appointments'),
    (gen_random_uuid(), 'reception',    'read',   'visit',       'Read visit registrations'),
    (gen_random_uuid(), 'reception',    'write',  'visit',       'Register and manage visits'),
    (gen_random_uuid(), 'queue',        'manage', 'queue',       'Manage queues and call tokens'),
    (gen_random_uuid(), 'queue',        'read',   'queue',       'Read queue boards and entries'),
    (gen_random_uuid(), 'encounter',    'read',   'encounter',   'Read encounters'),
    (gen_random_uuid(), 'encounter',    'write',  'encounter',   'Create and update encounters'),
    (gen_random_uuid(), 'clinical',     'write',  'note',        'Write clinical notes'),
    (gen_random_uuid(), 'clinical',     'read',   'note',        'Read clinical notes'),
    (gen_random_uuid(), 'clinical',     'write',  'vital',       'Record vital signs'),
    (gen_random_uuid(), 'clinical',     'write',  'diagnosis',   'Add diagnoses'),
    (gen_random_uuid(), 'clinical',     'write',  'prescription','Write prescriptions'),
    (gen_random_uuid(), 'clinical',     'manage', 'referral',    'Manage referrals')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.domain IN ('scheduling', 'reception', 'queue', 'encounter', 'clinical')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain IN ('scheduling', 'reception', 'queue')
WHERE r.name = 'FRONT_DESK'
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain IN ('encounter', 'clinical')
WHERE r.name = 'DOCTOR'
ON CONFLICT DO NOTHING;