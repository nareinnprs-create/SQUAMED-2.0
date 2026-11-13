-- V7: Patient Identity & EMPI schema
-- Module: squamed-patient

CREATE TABLE IF NOT EXISTS patient.patient_record (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id      UUID,
    mrn              VARCHAR(50) NOT NULL,
    external_id      VARCHAR(100),
    title            VARCHAR(20),
    first_name       VARCHAR(100) NOT NULL,
    middle_name      VARCHAR(100),
    last_name        VARCHAR(100) NOT NULL,
    preferred_name   VARCHAR(100),
    date_of_birth    DATE NOT NULL,
    gender           VARCHAR(20) NOT NULL,
    blood_group      VARCHAR(5),
    nationality      VARCHAR(100),
    religion         VARCHAR(50),
    marital_status   VARCHAR(20),
    occupation       VARCHAR(100),
    photo_url        VARCHAR(500),
    phone            VARCHAR(30),
    alternate_phone  VARCHAR(30),
    email            VARCHAR(150),
    address_line1    VARCHAR(300),
    address_line2    VARCHAR(300),
    city             VARCHAR(100),
    country          VARCHAR(100),
    postal_code      VARCHAR(20),
    emergency_contact_name VARCHAR(150),
    emergency_contact_phone VARCHAR(30),
    emergency_contact_relation VARCHAR(50),
    qid              VARCHAR(20),
    passport_number  VARCHAR(30),
    id_type          VARCHAR(30),
    id_number        VARCHAR(50),
    insurance_company VARCHAR(150),
    insurance_member_id VARCHAR(100),
    insurance_policy_no VARCHAR(100),
    insurance_plan   VARCHAR(100),
    is_deceased      BOOLEAN NOT NULL DEFAULT FALSE,
    death_date       DATE,
    deceased_note    VARCHAR(255),
    language_preference VARCHAR(30) DEFAULT 'en',
    communication_preference VARCHAR(30) DEFAULT 'phone',
    registration_channel VARCHAR(30) DEFAULT 'walk-in',
    records_status   VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, mrn)
);

CREATE TABLE IF NOT EXISTS patient.patient_identifier (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id    UUID NOT NULL REFERENCES patient.patient_record(id) ON DELETE CASCADE,
    id_type       VARCHAR(30) NOT NULL,
    id_value      VARCHAR(100) NOT NULL,
    issuing_authority VARCHAR(100),
    primary_flag  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id_type, id_value)
);

CREATE TABLE IF NOT EXISTS patient.contact_point (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id    UUID NOT NULL REFERENCES patient.patient_record(id) ON DELETE CASCADE,
    type          VARCHAR(30) NOT NULL,
    value         VARCHAR(150) NOT NULL,
    purpose       VARCHAR(50),
    is_primary    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS patient.address (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id    UUID NOT NULL REFERENCES patient.patient_record(id) ON DELETE CASCADE,
    type          VARCHAR(30) NOT NULL DEFAULT 'home',
    line1         VARCHAR(300),
    line2         VARCHAR(300),
    city          VARCHAR(100),
    state_province VARCHAR(100),
    country       VARCHAR(100),
    postal_code   VARCHAR(20),
    is_primary    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS patient.episode_of_care (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id    UUID NOT NULL REFERENCES patient.patient_record(id) ON DELETE CASCADE,
    period_type   VARCHAR(50) NOT NULL,
    start_date    DATE,
    end_date      DATE,
    reason        VARCHAR(255),
    care_team     VARCHAR(255),
    status        VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS patient.medical_history (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id    UUID NOT NULL REFERENCES patient.patient_record(id) ON DELETE CASCADE,
    condition_name VARCHAR(255) NOT NULL,
    diagnosis_code VARCHAR(50),
    diagnosis_system VARCHAR(20) DEFAULT 'ICD-10',
    onset_date    DATE,
    resolved_date DATE,
    notes         TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS patient.allergy (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id    UUID NOT NULL REFERENCES patient.patient_record(id) ON DELETE CASCADE,
    allergen      VARCHAR(255) NOT NULL,
    reaction      VARCHAR(255),
    severity      VARCHAR(20) NOT NULL DEFAULT 'mild',
    recorded_date DATE,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    notes         TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS patient.patient_note (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id    UUID NOT NULL REFERENCES patient.patient_record(id) ON DELETE CASCADE,
    author_id     UUID,
    title         VARCHAR(200),
    body          TEXT,
    is_flagged    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS patient.empi_score (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id    UUID NOT NULL REFERENCES patient.patient_record(id) ON DELETE CASCADE,
    matched_patient_id UUID,
    score         NUMERIC(5,2) NOT NULL,
    attribute     VARCHAR(50),
    rule          VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_pat_mrn ON patient.patient_record (mrn);
CREATE INDEX IF NOT EXISTS idx_pat_name ON patient.patient_record (last_name, first_name);
CREATE INDEX IF NOT EXISTS idx_pat_dob ON patient.patient_record (date_of_birth);
CREATE INDEX IF NOT EXISTS idx_pat_phone ON patient.patient_record (phone);
CREATE INDEX IF NOT EXISTS idx_pat_qid ON patient.patient_record (qid);
CREATE INDEX IF NOT EXISTS idx_pat_ident_value ON patient.patient_identifier (id_value);
CREATE INDEX IF NOT EXISTS idx_pat_contact_value ON patient.contact_point (value);