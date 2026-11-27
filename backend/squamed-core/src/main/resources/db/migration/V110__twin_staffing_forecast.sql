-- V110: staffing + demand forecast on the capacity snapshot (chunk-07 section 07.03)
-- staff_on_duty is the count of today's shifts whose code covers the current window; the forecast is a
-- 7-day run-rate of admissions. Both are evidence for the capacity answer, not new sources of truth.
-- Rollback note: additive only - drop the two columns.

ALTER TABLE twin.capacity_snapshot
    ADD COLUMN IF NOT EXISTS staff_on_duty INTEGER NOT NULL DEFAULT 0;

ALTER TABLE twin.capacity_snapshot
    ADD COLUMN IF NOT EXISTS forecast_admissions_next_24h NUMERIC(6, 2) NOT NULL DEFAULT 0;
