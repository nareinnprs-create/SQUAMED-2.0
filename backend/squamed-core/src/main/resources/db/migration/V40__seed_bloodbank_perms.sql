-- V40: Seed Blood Bank permissions
-- Module: bloodbank

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'bloodbank', 'read',  'bloodbank', 'View blood donors, inventory, crossmatch, reservations and transfusions'),
    (gen_random_uuid(), 'bloodbank', 'write', 'bloodbank', 'Manage donors, inventory, crossmatch, reservation, issue and transfusion')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.domain = 'bloodbank'
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'bloodbank' AND p.action IN ('read', 'write')
WHERE r.name IN ('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE', 'LAB_TECHNICIAN')
ON CONFLICT DO NOTHING;