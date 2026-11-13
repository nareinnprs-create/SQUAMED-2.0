-- V36: Seed Surgery permissions and Surgeon role
-- Module: surgery

INSERT INTO identity.role (name, display_name, description, is_system)
VALUES ('SURGEON', 'Surgeon', 'Surgical scheduling, procedures and intra-op documentation', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'surgery', 'read',  'surgery', 'View surgical schedules, teams, checklists and intra/post-op records'),
    (gen_random_uuid(), 'surgery', 'write', 'surgery', 'Schedule surgery, allocate OT, manage teams, instruments and implants')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.domain = 'surgery'
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'surgery' AND p.action IN ('read', 'write')
WHERE r.name IN ('HOSPITAL_ADMIN', 'DOCTOR', 'SURGEON', 'NURSE')
ON CONFLICT DO NOTHING;