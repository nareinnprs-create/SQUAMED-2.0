-- V38: Seed Anaesthesia permissions and Anaesthesiologist role
-- Module: anaesthesia

INSERT INTO identity.role (name, display_name, description, is_system)
VALUES ('ANAESTHESIOLOGIST', 'Anaesthesiologist', 'Pre-anaesthesia evaluation, intra-operative anaesthesia and recovery', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'anaesthesia', 'read',  'anaesthesia', 'View anaesthesia evaluations, records, monitoring and recovery'),
    (gen_random_uuid(), 'anaesthesia', 'write', 'anaesthesia', 'Perform evaluations, anaesthesia records, monitoring, medication and PACU care')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.domain = 'anaesthesia'
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'anaesthesia' AND p.action IN ('read', 'write')
WHERE r.name IN ('HOSPITAL_ADMIN', 'DOCTOR', 'SURGEON', 'ANAESTHESIOLOGIST', 'NURSE')
ON CONFLICT DO NOTHING;