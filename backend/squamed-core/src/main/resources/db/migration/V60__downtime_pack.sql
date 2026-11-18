-- V60: Downtime pack v1 (01.09) - downtime_log windows for CS-09 paper mode.
-- One row per downtime window: open when the system goes down, closed on recovery; the
-- printed pack and the token/check-in re-entry tool reference the window for the drill trail.
-- Rollback: DROP TABLE reception.downtime_log;

CREATE TABLE IF NOT EXISTS reception.downtime_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    reason      VARCHAR(200) NOT NULL,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at    TIMESTAMPTZ,
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_downtime_log_hospital
    ON reception.downtime_log (hospital_id, started_at DESC);

-- No new permission codes: window + re-entry writes ride queue:manage:queue, reads ride
-- queue:read:queue (RECEPTIONIST holds both since V54; downtime is front-office ops).
