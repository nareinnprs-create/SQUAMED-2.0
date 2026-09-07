-- V14: Laboratory & Diagnostics schema
-- Module: squamed-laboratory

CREATE SCHEMA IF NOT EXISTS laboratory;

CREATE TABLE IF NOT EXISTS laboratory.lab_order (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID,
    encounter_id    UUID,
    patient_id      UUID NOT NULL,
    order_number    VARCHAR(50) NOT NULL,
    priority        VARCHAR(20) NOT NULL DEFAULT 'routine',
    status          VARCHAR(30) NOT NULL DEFAULT 'ordered',
    clinical_note   TEXT,
    ordered_by      UUID,
    ordered_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    specimen_taken_at TIMESTAMPTZ,
    collected_by    UUID,
    received_at     TIMESTAMPTZ,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    rejected_reason TEXT,
    result_status   VARCHAR(30) NOT NULL DEFAULT 'pending',
    verified_at     TIMESTAMPTZ,
    verified_by     UUID,
    released_at     TIMESTAMPTZ,
    released_by     UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS laboratory.lab_test_request (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_order_id    UUID NOT NULL REFERENCES laboratory.lab_order(id) ON DELETE CASCADE,
    test_code       VARCHAR(50) NOT NULL,
    test_name       VARCHAR(255) NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'ordered',
    result_value    TEXT,
    unit            VARCHAR(30),
    reference_range TEXT,
    is_abnormal     BOOLEAN NOT NULL DEFAULT FALSE,
    notes           TEXT,
    result_entered_by UUID,
    result_entered_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_lab_order_patient ON laboratory.lab_order (patient_id);
CREATE INDEX IF NOT EXISTS idx_lab_order_status ON laboratory.lab_order (status);
CREATE INDEX IF NOT EXISTS idx_lab_order_ordered_at ON laboratory.lab_order (ordered_at);
CREATE INDEX IF NOT EXISTS idx_lab_test_order ON laboratory.lab_test_request (lab_order_id);