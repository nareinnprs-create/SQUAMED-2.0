-- 00.05 config engine: versioned, validated JSONB config documents ("configure, don't fork").
-- One document type per hospital (working-hours, holidays, shifts, workflow, queue, approval);
-- each publish inserts a new active row and supersedes the previous actives (append-only history).
-- Rollback: DROP TABLE hospital_config.config_document; published config history is not auto-reversible.

CREATE TABLE IF NOT EXISTS hospital_config.config_document (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID NOT NULL REFERENCES hospital_config.hospital(id),
    type          VARCHAR(50) NOT NULL,
    doc_version   INTEGER NOT NULL,
    content       JSONB NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_config_document_version UNIQUE (hospital_id, type, doc_version)
);

CREATE INDEX IF NOT EXISTS idx_config_document_lookup
    ON hospital_config.config_document (hospital_id, type, status);
