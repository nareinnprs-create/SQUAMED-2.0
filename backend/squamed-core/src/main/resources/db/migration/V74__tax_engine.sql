-- V74: Tax engine (chunk-03 section 03.03)
-- India GST split: healthcare services are exempt; pharmacy goods are 5% (12% for devices),
-- HSN/SAC codes per line, GSTIN + e-invoice threshold per hospital entity, tax lines per
-- invoice, and supervisor-gated credit notes with a mandatory reason.
-- Rollback note: additive only - drop the new tables/columns, delete the
-- billing:approve:creditnote permission row and its grants to revert.

CREATE TABLE IF NOT EXISTS billing.hospital_tax_profile (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id            UUID NOT NULL UNIQUE,
    gstin                  VARCHAR(20),
    state_code             VARCHAR(4),
    is_gst_registered      BOOLEAN NOT NULL DEFAULT TRUE,
    e_invoice_threshold_qar NUMERIC(12, 2),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by             UUID,
    updated_by             UUID,
    version                BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE billing.charge
    ADD COLUMN IF NOT EXISTS tax_category VARCHAR(20) NOT NULL DEFAULT 'EXEMPT_SERVICE',
    ADD COLUMN IF NOT EXISTS hsn_code VARCHAR(12);

ALTER TABLE billing.invoice
    ADD COLUMN IF NOT EXISTS e_invoice_required BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS billing.tax_line (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id    UUID NOT NULL REFERENCES billing.invoice(id),
    hsn_code      VARCHAR(12),
    tax_category  VARCHAR(20) NOT NULL,
    taxable_qar   NUMERIC(12, 2) NOT NULL,
    rate_pct      NUMERIC(5, 2) NOT NULL,
    tax_qar       NUMERIC(12, 2) NOT NULL,
    is_exempt     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_tax_line_invoice ON billing.tax_line (invoice_id);

CREATE TABLE IF NOT EXISTS billing.credit_note (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL,
    invoice_id   UUID NOT NULL REFERENCES billing.invoice(id),
    note_number  VARCHAR(50) NOT NULL,
    amount_qar   NUMERIC(12, 2) NOT NULL CHECK (amount_qar > 0),
    reason       VARCHAR(400) NOT NULL,
    status       VARCHAR(10) NOT NULL DEFAULT 'issued' CHECK (status IN ('issued', 'refunded')),
    issued_by    UUID,
    issued_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES (gen_random_uuid(), 'billing', 'approve', 'creditnote', 'Issue credit notes and refunds')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'billing' AND p.action = 'approve' AND p.resource = 'creditnote'
WHERE r.name IN ('BILLING_SUPERVISOR', 'SYSTEM_ADMIN')
ON CONFLICT DO NOTHING;
