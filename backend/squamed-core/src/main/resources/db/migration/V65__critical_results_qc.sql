-- V65: Critical results + QC (chunk-02 section 02.03)
-- Critical result detection with SLA/ack trail, delta threshold blocking,
-- Westgard QC runs that hard-block release.
-- Rollback note: additive only — drop laboratory.qc_run, laboratory.critical_result_ack,
-- laboratory.critical_result, drop the added master_data.lab_test /
-- laboratory.lab_test_request columns to revert.

ALTER TABLE master_data.lab_test
    ADD COLUMN IF NOT EXISTS critical_low DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS critical_high DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS critical_sla_minutes INTEGER NOT NULL DEFAULT 60;

ALTER TABLE laboratory.lab_test_request
    ADD COLUMN IF NOT EXISTS is_delta_blocked BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS laboratory.critical_result (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id        UUID NOT NULL,
    lab_order_id       UUID NOT NULL REFERENCES laboratory.lab_order(id) ON DELETE CASCADE,
    lab_test_request_id UUID NOT NULL REFERENCES laboratory.lab_test_request(id) ON DELETE CASCADE,
    patient_id         UUID NOT NULL,
    test_code          VARCHAR(50) NOT NULL,
    test_name          VARCHAR(255) NOT NULL,
    result_value       VARCHAR(50),
    criticality        VARCHAR(20) NOT NULL CHECK (criticality IN ('CRITICAL_LOW', 'CRITICAL_HIGH')),
    status             VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ACKNOWLEDGED')),
    sla_minutes        INTEGER NOT NULL,
    acked_by           UUID,
    acked_at           TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS laboratory.critical_result_ack (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    critical_result_id UUID NOT NULL REFERENCES laboratory.critical_result(id) ON DELETE CASCADE,
    actor_id           UUID,
    note               VARCHAR(400),
    occurred_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS laboratory.qc_run (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID NOT NULL,
    instrument    VARCHAR(100),
    test_code     VARCHAR(50) NOT NULL,
    level         VARCHAR(10) NOT NULL CHECK (level IN ('LOW', 'NORMAL', 'HIGH')),
    observed_value DOUBLE PRECISION NOT NULL,
    target_mean   DOUBLE PRECISION NOT NULL,
    target_sd     DOUBLE PRECISION NOT NULL,
    westgard_rules TEXT,
    status        VARCHAR(20) NOT NULL CHECK (status IN ('ACCEPTED', 'WARNING', 'REJECTED')),
    note          VARCHAR(400),
    run_by        UUID,
    run_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_critical_result_order ON laboratory.critical_result (lab_order_id);
CREATE INDEX IF NOT EXISTS idx_critical_result_open ON laboratory.critical_result (hospital_id, status);
CREATE INDEX IF NOT EXISTS idx_critical_result_ack_result ON laboratory.critical_result_ack (critical_result_id);
CREATE INDEX IF NOT EXISTS idx_qc_run_lookup ON laboratory.qc_run (hospital_id, test_code, run_at);
