-- V66: Lab reports + delivery (chunk-02 section 02.04)
-- Branded PDF report versions stored in object storage, age/sex reference ranges,
-- versioned result amendments, delivery counter/app stamp, TAT analytics is read-evaluated.
-- Rollback note: additive only — drop laboratory.lab_result_amendment, laboratory.lab_report,
-- laboratory.lab_reference_range to revert.

CREATE TABLE IF NOT EXISTS laboratory.lab_reference_range (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL,
    test_code      VARCHAR(50) NOT NULL,
    sex            VARCHAR(10) NOT NULL CHECK (sex IN ('M', 'F', 'ANY')),
    age_min_years  INTEGER NOT NULL DEFAULT 0,
    age_max_years  INTEGER NOT NULL DEFAULT 200,
    range_text     VARCHAR(100) NOT NULL,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS laboratory.lab_report (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL,
    lab_order_id   UUID NOT NULL REFERENCES laboratory.lab_order(id) ON DELETE CASCADE,
    report_version INTEGER NOT NULL,
    storage_key    VARCHAR(400) NOT NULL,
    published_by   UUID,
    published_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered_via  VARCHAR(20) CHECK (delivered_via IN ('COUNTER', 'APP')),
    delivered_to   UUID,
    delivered_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS laboratory.lab_result_amendment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id         UUID NOT NULL,
    lab_order_id        UUID NOT NULL REFERENCES laboratory.lab_order(id) ON DELETE CASCADE,
    lab_test_request_id UUID NOT NULL REFERENCES laboratory.lab_test_request(id) ON DELETE CASCADE,
    old_value           TEXT,
    new_value           TEXT NOT NULL,
    reason              VARCHAR(400) NOT NULL,
    amended_by          UUID,
    amended_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_lab_reference_range_test ON laboratory.lab_reference_range (test_code, is_active);
CREATE INDEX IF NOT EXISTS idx_lab_report_order ON laboratory.lab_report (lab_order_id, version);
CREATE INDEX IF NOT EXISTS idx_lab_amendment_request ON laboratory.lab_result_amendment (lab_test_request_id);
