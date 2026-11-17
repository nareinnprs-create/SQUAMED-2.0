-- 01.08 kiosk-lite: self-service check-in kiosk identity (new system role, no new permission codes).
-- KIOSK resolves patients by ABHA / ABHA address / mobile, stamps appointment arrival,
-- issues queue tokens and lists departments for wayfinding.

INSERT INTO identity.role (name, display_name, description, is_system)
VALUES ('KIOSK', 'Kiosk', 'Self-service OPD kiosk: check-in by ABHA / mobile / QR and department lookup', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p
  ON (p.domain = 'patient' AND p.action = 'read' AND p.resource = 'patient')
  OR (p.domain = 'queue')
  OR (p.domain = 'hospital-config' AND p.action = 'read' AND p.resource = 'department')
WHERE r.name = 'KIOSK'
ON CONFLICT DO NOTHING;
