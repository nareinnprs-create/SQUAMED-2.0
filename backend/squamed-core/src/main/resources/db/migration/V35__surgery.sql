-- V35: OT / Surgery schema
-- Module: surgery (scheduling, allocation, team, checklists, instruments, implants, intra/post-op documentation)

CREATE SCHEMA IF NOT EXISTS surgery;

CREATE TABLE IF NOT EXISTS surgery.ot_room (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL,
    name         VARCHAR(100) NOT NULL,
    code         VARCHAR(30) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'operational',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_surgery_ot_room_code ON surgery.ot_room (hospital_id, code);

CREATE TABLE IF NOT EXISTS surgery.surgery_schedule (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id             UUID NOT NULL,
    patient_id              UUID NOT NULL,
    admission_id            UUID,
    booking_number          VARCHAR(30),
    procedure_name          VARCHAR(255) NOT NULL,
    procedure_code          VARCHAR(50),
    specialty               VARCHAR(50),
    surgeon_id              UUID,
    status                  VARCHAR(30) NOT NULL DEFAULT 'scheduled',
    scheduled_start_at      TIMESTAMPTZ NOT NULL,
    scheduled_end_at        TIMESTAMPTZ,
    actual_start_at         TIMESTAMPTZ,
    actual_end_at           TIMESTAMPTZ,
    notes                   TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_surgery_schedule_status ON surgery.surgery_schedule (status);
CREATE INDEX IF NOT EXISTS idx_surgery_schedule_patient ON surgery.surgery_schedule (patient_id);

CREATE TABLE IF NOT EXISTS surgery.ot_allocation (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID NOT NULL,
    ot_room_id        UUID NOT NULL,
    surgery_schedule_id UUID NOT NULL,
    start_time        TIMESTAMPTZ NOT NULL,
    end_time          TIMESTAMPTZ,
    status            VARCHAR(20) NOT NULL DEFAULT 'allocated',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_surgery_allocation_ot ON surgery.ot_allocation (ot_room_id);

CREATE TABLE IF NOT EXISTS surgery.surgical_team (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID NOT NULL,
    surgery_schedule_id UUID NOT NULL,
    staff_name        VARCHAR(255) NOT NULL,
    role              VARCHAR(30) NOT NULL,
    joined_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS surgery.instrument_tracking (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID NOT NULL,
    surgery_schedule_id UUID NOT NULL,
    instrument_name   VARCHAR(255) NOT NULL,
    set_name          VARCHAR(100),
    status            VARCHAR(30) NOT NULL DEFAULT 'booked',
    counted_by        UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS surgery.implant_record (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID NOT NULL,
    surgery_schedule_id UUID NOT NULL,
    implant_name      VARCHAR(255) NOT NULL,
    catalog_number    VARCHAR(50),
    lot_number        VARCHAR(50),
    expiry_date       DATE,
    quantity          INT NOT NULL DEFAULT 1,
    implanted_by      UUID,
    notes             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS surgery.pre_op_checklist (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID NOT NULL,
    surgery_schedule_id UUID NOT NULL,
    checklist_item    VARCHAR(255) NOT NULL,
    category          VARCHAR(30),
    checked           BOOLEAN NOT NULL DEFAULT FALSE,
    checked_by        UUID,
    checked_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS surgery.intra_op_record (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID NOT NULL,
    surgery_schedule_id UUID NOT NULL,
    event_name        VARCHAR(100) NOT NULL,
    notes             TEXT,
    recorded_by       UUID,
    recorded_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS surgery.post_op_record (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID NOT NULL,
    surgery_schedule_id UUID NOT NULL,
    patient_condition VARCHAR(255),
    destination       VARCHAR(30),
    pain_score        INT,
    drain_output_ml   INT,
    complications     TEXT,
    recorded_by       UUID,
    recorded_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0
);