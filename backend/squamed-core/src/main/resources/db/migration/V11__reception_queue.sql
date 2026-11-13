-- V11: Reception & Queue Engine schema
-- Module: squamed-reception / squamed-queue

CREATE TABLE IF NOT EXISTS reception.visit_registration (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID,
    patient_id      UUID NOT NULL,
    appointment_id  UUID,
    visit_number    VARCHAR(50) NOT NULL,
    visit_type      VARCHAR(30) NOT NULL DEFAULT 'outpatient',
    department_id   UUID,
    doctor_id       UUID,
    specialty_id    UUID,
    reason          TEXT,
    priority        VARCHAR(20) NOT NULL DEFAULT 'routine',
    source          VARCHAR(30) NOT NULL DEFAULT 'walk-in',
    status          VARCHAR(30) NOT NULL DEFAULT 'registered',
    insurance_verification VARCHAR(30),
    insurance_verified_at TIMESTAMPTZ,
    registration_started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    registered_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS queue.queue_entry (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID,
    queue_type      VARCHAR(30) NOT NULL,
    department_id   UUID,
    service_point_id VARCHAR(50),
    service_point_name VARCHAR(150),
    visit_id        UUID,
    patient_id      UUID NOT NULL,
    appointment_id  UUID,
    token_number    VARCHAR(20) NOT NULL,
    patient_name    VARCHAR(200),
    priority        VARCHAR(20) NOT NULL DEFAULT 'routine',
    status          VARCHAR(20) NOT NULL DEFAULT 'waiting',
    served_by       UUID,
    called_at       TIMESTAMPTZ,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    skipped_at      TIMESTAMPTZ,
    no_show_at      TIMESTAMPTZ,
    estimated_wait_minutes INTEGER,
    notes           TEXT,
    queue_date      DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS queue.service_point (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID,
    name            VARCHAR(150) NOT NULL,
    code            VARCHAR(50),
    queue_type      VARCHAR(30) NOT NULL,
    counter_number  VARCHAR(20),
    status          VARCHAR(20) NOT NULL DEFAULT 'open',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_recep_visit_patient ON reception.visit_registration (patient_id);
CREATE INDEX IF NOT EXISTS idx_recep_visit_number ON reception.visit_registration (visit_number);
CREATE INDEX IF NOT EXISTS idx_queue_entry_vist ON queue.queue_entry (visit_id);
CREATE INDEX IF NOT EXISTS idx_queue_entry_patient ON queue.queue_entry (patient_id);
CREATE INDEX IF NOT EXISTS idx_queue_entry_status ON queue.queue_entry (queue_type, status);