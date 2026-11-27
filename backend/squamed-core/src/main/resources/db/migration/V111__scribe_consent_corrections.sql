-- V111: scribe consent gate + draft-correction capture (chunk-05 section 05.05)
-- A recording needs a granted treatment consent for the patient, and clinician edits of the AI draft are
-- kept as eval data (original vs edited, chars changed) rather than overwritten silently.
-- Rollback note: additive only - drop table scribe.draft_correction and the two session columns.

ALTER TABLE scribe.scribe_session
    ADD COLUMN IF NOT EXISTS consent_id UUID;

ALTER TABLE scribe.scribe_session
    ADD COLUMN IF NOT EXISTS recording_started_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS scribe.draft_correction (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID,
    session_id    UUID NOT NULL,
    original_draft TEXT,
    edited_draft  TEXT NOT NULL,
    chars_changed INTEGER NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_draft_correction_session ON scribe.draft_correction (session_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_draft_correction_hospital ON scribe.draft_correction (hospital_id, created_at DESC);
