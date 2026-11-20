-- V72: Charge capture ledger (chunk-03 section 03.01)
-- The billing charge row becomes the unified ledger entry: event-driven charges carry the
-- source event id (idempotent consumption - a replayed event never double-charges) and
-- manual entries carry a mandatory reason. Pricing for event-driven lines comes from the
-- active tariff row matching the performed code (rate cards are 03.02 scope).
-- Rollback note: additive only - drop the three added columns/index. No permission rows added.

ALTER TABLE billing.charge
    ADD COLUMN IF NOT EXISTS source_event_id UUID,
    ADD COLUMN IF NOT EXISTS entry_mode VARCHAR(10) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS entry_reason VARCHAR(400);

-- One ledger row per source event: the consumer's idempotency key.
CREATE UNIQUE INDEX IF NOT EXISTS uq_charge_source_event ON billing.charge (source_event_id)
    WHERE source_event_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_charge_hospital_posted ON billing.charge (hospital_id, posted_at);
