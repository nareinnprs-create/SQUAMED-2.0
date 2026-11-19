-- V62: Analyzer intake (chunk-2 02.01) - analyzer message log + quarantine lane.
-- Every inbound analyzer message lands here: ACCEPTED or QUARANTINED, never dropped.
-- Rollback: DROP TABLE interop.analyzer_message_log;

CREATE TABLE IF NOT EXISTS interop.analyzer_message_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID,
    transport     VARCHAR(20) NOT NULL,               -- HL7V2 | ASTM
    source        VARCHAR(120),                       -- analyzer/channel id (e.g. mirth:cbc-analyzer)
    message_type  VARCHAR(50),                        -- HL7 MSH-9.1 or ASTM frame type
    control_id    VARCHAR(100),
    status        VARCHAR(20) NOT NULL,               -- ACCEPTED | QUARANTINED
    ack_code      VARCHAR(10),
    reason        VARCHAR(400),                       -- quarantine/import reason (null when accepted)
    raw_message   TEXT,
    parsed_json   TEXT,
    replayed_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_analyzer_message_status CHECK (status IN ('ACCEPTED', 'QUARANTINED'))
);

CREATE INDEX IF NOT EXISTS idx_analyzer_message_status ON interop.analyzer_message_log (status, created_at);
CREATE INDEX IF NOT EXISTS idx_analyzer_message_source ON interop.analyzer_message_log (source, created_at);
