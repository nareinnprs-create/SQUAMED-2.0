-- V26: Seed missing permission codes referenced by controllers
-- clinical:read:vital (FHIR Observation), orders:read:order (FHIR ServiceRequest)

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    ('00000000-0000-0000-0000-00000000f108', 'clinical', 'read', 'vital', 'Read patient vital signs'),
    ('00000000-0000-0000-0000-00000000f109', 'orders', 'read', 'order', 'Read clinical orders')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.name = 'SYSTEM_ADMIN'
  AND ((p.domain = 'clinical' AND p.action = 'read' AND p.resource = 'vital')
    OR (p.domain = 'orders' AND p.action = 'read' AND p.resource = 'order'))
ON CONFLICT DO NOTHING;