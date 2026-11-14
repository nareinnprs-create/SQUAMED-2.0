-- V49: Audit hash chain + before/after capture + device identity + WORM export cursor (00.10).
-- ADR-9: append-only audit table hash-chained per tenant (prev_hash in each row), nightly WORM
-- export, 10-year retention. Chain heads live in audit.chain_head (zero UUID = system chain for
-- rows without a hospital); each audit row carries chain_seq + previous_hash + row_hash.
-- Rollback: ALTER TABLE audit.audit_log DROP COLUMN chain_seq, previous_hash, row_hash,
--           actor_session_id, device_id, before_state, after_state;
--           DROP TABLE audit.export_cursor; DROP TABLE audit.chain_head;
--           (permission seed removed below)

ALTER TABLE audit.audit_log
    ADD COLUMN chain_seq        BIGINT,
    ADD COLUMN previous_hash    VARCHAR(64),
    ADD COLUMN row_hash         VARCHAR(64),
    ADD COLUMN actor_session_id UUID,
    ADD COLUMN device_id        UUID,
    ADD COLUMN before_state     TEXT,
    ADD COLUMN after_state      TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_audit_log_chain_seq
    ON audit.audit_log (hospital_id, chain_seq) WHERE hospital_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS audit.chain_head (
    hospital_id UUID PRIMARY KEY, -- zero UUID '00000000-...-000000000000' = system chain (rows with NULL hospital_id)
    last_seq    BIGINT NOT NULL DEFAULT 0,
    last_hash   VARCHAR(64),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS audit.export_cursor (
    hospital_id UUID PRIMARY KEY, -- zero UUID = system chain
    last_seq    BIGINT NOT NULL DEFAULT 0,
    object_key  VARCHAR(400),
    exported_at TIMESTAMPTZ
);

-- Backfill: pre-existing rows predate the chain; they stay unchained (chain_seq/row_hash NULL)
-- and every tenant's chain starts at the first new row. Verify() reports them as a legacy gap,
-- which the nightly job notes once and does not count as a tamper breach.

-- Auditor role: viewer UI read path (permission matrix "View audit: auditor ? all").
INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'audit' AND p.action = 'read' AND p.resource = 'audit'
WHERE r.name = 'READ_ONLY_VIEWER'
ON CONFLICT DO NOTHING;
