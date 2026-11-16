-- V54: Scheduling engine (01.01) - session-based doctor schedule templates, materialized
-- session slots (in-person + teleconsult slot types), waitlist, booking support columns.
-- Booking itself rides the existing scheduling.appointment table (V10).
-- Rollback: DROP TABLE scheduling.waitlist_entry; DROP TABLE scheduling.session_slot;
-- DROP TABLE scheduling.schedule_template; ALTER TABLE scheduling.appointment DROP COLUMN slot_id;
-- (releases the scheduling override permission and its grants).

CREATE TABLE IF NOT EXISTS scheduling.schedule_template (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL,
    doctor_id      UUID NOT NULL,
    department_id  UUID,
    -- in_person | teleconsult (teleconsult slots are consumed by the chunk-6 telemedicine flow)
    session_type   VARCHAR(20) NOT NULL DEFAULT 'in_person',
    weekday        INTEGER NOT NULL,            -- ISO: 1=Monday .. 7=Sunday (entity maps Kotlin Int -> INTEGER)
    start_time     TIME NOT NULL,
    end_time       TIME NOT NULL,
    slot_minutes   INTEGER NOT NULL DEFAULT 15,
    capacity       INTEGER NOT NULL DEFAULT 1,
    room_id        UUID,
    equipment_ids  JSONB,                       -- masterdata.Equipment ids bound to the session
    valid_from     DATE,
    valid_to       DATE,
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_schedule_template_weekday CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT ck_schedule_template_window CHECK (end_time > start_time)
);

CREATE INDEX IF NOT EXISTS idx_schedule_template_lookup
    ON scheduling.schedule_template (hospital_id, doctor_id, active);

CREATE TABLE IF NOT EXISTS scheduling.session_slot (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL,
    template_id    UUID NOT NULL REFERENCES scheduling.schedule_template(id),
    doctor_id      UUID NOT NULL,
    department_id  UUID,
    session_type   VARCHAR(20) NOT NULL DEFAULT 'in_person',
    room_id        UUID,
    slot_date      DATE NOT NULL,
    start_time     TIME NOT NULL,
    end_time       TIME NOT NULL,
    capacity       INTEGER NOT NULL,
    booked_count   INTEGER NOT NULL DEFAULT 0,
    no_show_count  INTEGER NOT NULL DEFAULT 0,
    status         VARCHAR(20) NOT NULL DEFAULT 'open',  -- open | closed
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_session_slot UNIQUE (hospital_id, doctor_id, slot_date, start_time)
);

CREATE INDEX IF NOT EXISTS idx_session_slot_lookup
    ON scheduling.session_slot (hospital_id, doctor_id, slot_date);

CREATE TABLE IF NOT EXISTS scheduling.waitlist_entry (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL,
    patient_id     UUID NOT NULL,
    doctor_id      UUID,
    department_id  UUID,
    priority       VARCHAR(20) NOT NULL DEFAULT 'routine',
    desired_date   DATE,
    status         VARCHAR(20) NOT NULL DEFAULT 'waiting', -- waiting | booked | cancelled
    booked_appointment_id UUID,
    notes          VARCHAR(500),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_waitlist_status CHECK (status IN ('waiting', 'booked', 'cancelled'))
);

CREATE INDEX IF NOT EXISTS idx_waitlist_lookup
    ON scheduling.waitlist_entry (hospital_id, status, priority);

ALTER TABLE scheduling.appointment
    ADD COLUMN IF NOT EXISTS slot_id UUID;

CREATE INDEX IF NOT EXISTS idx_appointment_slot
    ON scheduling.appointment (hospital_id, slot_id);

-- Scheduling engine permissions (01.01): template/slot admin + booking-channel + no-show override.
INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'scheduling', 'read',    'schedule', 'Read doctor schedule templates and session slots'),
    (gen_random_uuid(), 'scheduling', 'write',   'schedule', 'Manage doctor schedule templates'),
    (gen_random_uuid(), 'scheduling', 'override','booking',  'Book past the no-show policy limit')
ON CONFLICT DO NOTHING;

-- SYSTEM_ADMIN gets the new codes (V25's cross-join ran once; new permissions need explicit grants).
INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.domain = 'scheduling'
ON CONFLICT DO NOTHING;

-- The reception workforce (contract chunk-01 PERMISSIONS: reception does booking/check-in/token ops).
-- V13 granted this set to role FRONT_DESK, which was never created - the grant silently no-op'd.
-- Re-landed here on the real role: scheduling + reception + queue.
INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain IN ('scheduling', 'reception', 'queue')
WHERE r.name = 'RECEPTIONIST'
ON CONFLICT DO NOTHING;

-- Doctors see schedules and slots; booking stays with reception/admin.
INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p
  ON (p.domain = 'scheduling' AND p.action = 'read')
WHERE r.name = 'DOCTOR'
ON CONFLICT DO NOTHING;
