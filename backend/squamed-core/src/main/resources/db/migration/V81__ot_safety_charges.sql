-- V81: OT safety + surgical charges (chunk-04 section 04.06)
-- WHO Surgical Safety Checklist staged (sign-in / time-out / sign-out) and enforced on the
-- schedule state machine, consent gate before starting, intra-op consumables emitting charge
-- events into the chunk-3 ledger, and an SSI follow-up flag to the IPC hand-off (task).
-- Rollback note: additive only - drop surgery.surgical_consumable, drop the added columns.

ALTER TABLE surgery.surgery_schedule
    ADD COLUMN IF NOT EXISTS consent_id UUID;

ALTER TABLE surgery.pre_op_checklist
    ADD COLUMN IF NOT EXISTS stage VARCHAR(10);

CREATE TABLE IF NOT EXISTS surgery.surgical_consumable (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id          UUID NOT NULL,
    surgery_schedule_id  UUID NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    quantity             INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price_qar       NUMERIC(12, 2),
    charge_event_id      UUID,
    recorded_by          UUID,
    recorded_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_surgical_consumable_schedule ON surgery.surgical_consumable (surgery_schedule_id);
