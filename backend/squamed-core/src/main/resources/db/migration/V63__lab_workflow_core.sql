-- V63: Lab workflow core (chunk-02 section 02.02)
-- Barcode labeling, accession ids, chain-of-custody, delta checks, reject-and-recollect,
-- role separation (lab-tech collects/enters, pathologist validates per chunk-02 permissions).
-- Rollback note: additive only — drop laboratory.specimen_custody, drop the added
-- lab_order/lab_test_request columns, re-grant laboratory:verify:lab to LAB_TECHNICIAN
-- (row was removed, not edited) to revert.

ALTER TABLE laboratory.lab_order
    ADD COLUMN IF NOT EXISTS barcode_id VARCHAR(50),
    ADD COLUMN IF NOT EXISTS accession_id VARCHAR(50),
    ADD COLUMN IF NOT EXISTS rejected_by UUID,
    ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS recollect_of UUID REFERENCES laboratory.lab_order(id);

ALTER TABLE laboratory.lab_test_request
    ADD COLUMN IF NOT EXISTS prior_value TEXT,
    ADD COLUMN IF NOT EXISTS delta_percent DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS entered_via VARCHAR(20) NOT NULL DEFAULT 'MANUAL';

CREATE TABLE IF NOT EXISTS laboratory.specimen_custody (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_order_id UUID NOT NULL REFERENCES laboratory.lab_order(id) ON DELETE CASCADE,
    event        VARCHAR(30) NOT NULL,
    actor_id     UUID,
    note         VARCHAR(400),
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_specimen_custody_order ON laboratory.specimen_custody (lab_order_id);
CREATE INDEX IF NOT EXISTS idx_lab_order_worklist ON laboratory.lab_order (status, priority, ordered_at);

-- Chunk-02 permissions: phlebotomist/lab-tech collect/accession/enter (no validate);
-- pathologist validates. LAB_TECHNICIAN currently holds all laboratory permissions (V17).
INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'laboratory' AND p.action = 'verify' AND p.resource = 'lab'
WHERE r.name = 'PATHOLOGIST'
ON CONFLICT DO NOTHING;

DELETE FROM identity.role_permission rp
USING identity.role r, identity.permission p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.name = 'LAB_TECHNICIAN'
  AND p.domain = 'laboratory'
  AND p.action = 'verify'
  AND p.resource = 'lab';
