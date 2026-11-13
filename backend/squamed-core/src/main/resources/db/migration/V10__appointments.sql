-- V10: Appointment Management schema
-- Module: squamed-scheduling

CREATE SCHEMA IF NOT EXISTS scheduling;

CREATE TABLE IF NOT EXISTS scheduling.appointment (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID,
    patient_id      UUID NOT NULL,
    department_id   UUID,
    specialty_id    UUID,
    doctor_id       UUID NOT NULL,
    appointment_type VARCHAR(30) NOT NULL,
    scheduled_start TIMESTAMPTZ NOT NULL,
    scheduled_end   TIMESTAMPTZ NOT NULL,
    duration_minutes INTEGER NOT NULL DEFAULT 15,
    status          VARCHAR(30) NOT NULL DEFAULT 'scheduled',
    reason          TEXT,
    notes           TEXT,
    visit_type      VARCHAR(30) NOT NULL DEFAULT 'outpatient',
    priority        VARCHAR(20) NOT NULL DEFAULT 'routine',
    telemedicine    BOOLEAN NOT NULL DEFAULT FALSE,
    room_id         UUID,
    external_ref    VARCHAR(100),
    source          VARCHAR(30) NOT NULL DEFAULT 'clinic',
    checked_in_at   TIMESTAMPTZ,
    confirmed_at    TIMESTAMPTZ,
    cancelled_reason VARCHAR(100),
    cancelled_at    TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    no_show_at      TIMESTAMPTZ,
    cancellation_fee NUMERIC(10,2),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_sched_appt_patient ON scheduling.appointment (patient_id);
CREATE INDEX IF NOT EXISTS idx_sched_appt_doctor ON scheduling.appointment (doctor_id);
CREATE INDEX IF NOT EXISTS idx_sched_appt_start ON scheduling.appointment (scheduled_start);
CREATE INDEX IF NOT EXISTS idx_sched_appt_status ON scheduling.appointment (status);