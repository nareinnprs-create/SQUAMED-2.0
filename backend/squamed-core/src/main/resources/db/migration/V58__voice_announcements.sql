-- V58: Voice announcement engine (01.07) - display zones (audio zone mapping per
-- display/counter) + announcement rules (repeat count, priority doubling, language order).
-- No new permission codes: zones/rules/enqueue ride queue:read:queue / queue:manage:queue.
-- Rollback: DROP TABLE queue.announcement_rule; DROP TABLE queue.display_zone;

CREATE TABLE IF NOT EXISTS queue.display_zone (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id      UUID NOT NULL,
    name             VARCHAR(150) NOT NULL,
    service_point_id VARCHAR(50),                  -- bound counter (queue_entry.service_point_id domain)
    volume           INT NOT NULL DEFAULT 80 CHECK (volume BETWEEN 0 AND 100),
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_display_zone_hospital
    ON queue.display_zone (hospital_id, service_point_id);

CREATE TABLE IF NOT EXISTS queue.announcement_rule (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID NOT NULL,
    queue_type    VARCHAR(30) NOT NULL DEFAULT '*',   -- exact queue match; '*' = any queue (fallback)
    repeat_count  INT NOT NULL DEFAULT 2 CHECK (repeat_count BETWEEN 1 AND 10),
    languages     VARCHAR(100) NOT NULL DEFAULT 'en,hi', -- comma-ordered TTS languages (regional = pilot decision)
    zone_id       UUID,                                -- null = announce in every zone
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_announcement_rule_hospital
    ON queue.announcement_rule (hospital_id, queue_type);
