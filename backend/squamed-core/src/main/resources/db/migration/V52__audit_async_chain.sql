-- V52: ADR-15 async audit chain linkage. Audit rows insert unchained (AuditRecorder) and the
-- AuditChainBatcher assigns chain_seq/previous_hash/row_hash in batches every 250 ms, so writes
-- no longer queue on the serialized chain-head append. This partial index keeps the batcher's
-- pending scan cheap.
-- Rollback: DROP INDEX audit.idx_audit_log_pending;

CREATE INDEX IF NOT EXISTS idx_audit_log_pending
    ON audit.audit_log (hospital_id, occurred_at, id)
    WHERE chain_seq IS NULL;
