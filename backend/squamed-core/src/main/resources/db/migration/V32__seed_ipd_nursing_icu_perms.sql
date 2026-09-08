-- V32: Seed IPD + Nursing + ICU permissions
-- Module: ipd, nursing, icu

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'ipd',     'read',  'admission', 'View admissions, transfers and discharge summaries'),
    (gen_random_uuid(), 'ipd',     'write', 'admission', 'Admit, transfer and discharge inpatients'),
    (gen_random_uuid(), 'nursing', 'read',  'nursing',   'View nursing assessments, care plans, notes, vitals and eMAR'),
    (gen_random_uuid(), 'nursing', 'write', 'nursing',   'Record nursing documentation, vitals and medication administration'),
    (gen_random_uuid(), 'icu',     'read',  'icu',       'View ICU vitals, fluid balance, ventilation and infusions'),
    (gen_random_uuid(), 'icu',     'write', 'icu',       'Record ICU monitoring data and manage critical alerts')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.domain IN ('ipd', 'nursing', 'icu')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p
  ON (p.domain = 'icu' AND p.action IN ('read', 'write'))
  OR (p.domain = 'nursing' AND p.action IN ('read', 'write'))
  OR (p.domain = 'ipd' AND p.action = 'read')
WHERE r.name = 'DOCTOR'
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p
  ON p.domain IN ('ipd', 'nursing', 'icu') AND p.action IN ('read', 'write')
WHERE r.name = 'NURSE'
ON CONFLICT DO NOTHING;