-- V29: Inpatient Platform (IPD) schema
-- Module: ipd (admission, transfer, discharge summary, attendant)

CREATE SCHEMA IF NOT EXISTS ipd;

CREATE TABLE IF NOT EXISTS ipd.admission (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id           UUID NOT NULL,
    patient_id            UUID NOT NULL,
    admission_type        VARCHAR(30) NOT NULL DEFAULT 'elective',
    status                VARCHAR(20) NOT NULL DEFAULT 'admitted',
    bed_id                UUID,
    ward_id               UUID,
    room_id               UUID,
    attending_doctor_id   UUID,
    admission_diagnosis   TEXT,
    source                VARCHAR(30) NOT NULL DEFAULT 'reception',
    admitted_at           TIMESTAMPTZ,
    admitted_by           UUID,
    discharged_at         TIMESTAMPTZ,
    discharge_type        VARCHAR(30),
    discharge_notes       TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    version               BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ipd_admission_patient ON ipd.admission (patient_id);
CREATE INDEX IF NOT EXISTS idx_ipd_admission_status ON ipd.admission (status);
CREATE INDEX IF NOT EXISTS idx_ipd_admission_bed ON ipd.admission (bed_id);

CREATE TABLE IF NOT EXISTS ipd.admission_transfer (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admission_id  UUID NOT NULL,
    from_bed_id   UUID,
    to_bed_id     UUID,
    reason        TEXT,
    transferred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    transferred_by UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ipd_transfer_admission ON ipd.admission_transfer (admission_id);

CREATE TABLE IF NOT EXISTS ipd.discharge_summary (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admission_id         UUID NOT NULL,
    summary_text         TEXT,
    follow_up_plan       TEXT,
    medications_at_discharge TEXT,
    instructions         TEXT,
    prepared_by          UUID,
    prepared_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ipd_discharge_summary_admission ON ipd.discharge_summary (admission_id);

CREATE TABLE IF NOT EXISTS ipd.attendant (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admission_id  UUID NOT NULL,
    name          VARCHAR(150) NOT NULL,
    relationship  VARCHAR(50),
    phone         VARCHAR(30),
    notes         TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ipd_attendant_admission ON ipd.attendant (admission_id);