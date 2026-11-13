-- V39: Blood Bank schema
-- Module: bloodbank (donors, inventory, grouping, crossmatch, reservation, issue, transfusion, reactions)

CREATE SCHEMA IF NOT EXISTS bloodbank;

CREATE TABLE IF NOT EXISTS bloodbank.blood_donor (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id      UUID NOT NULL,
    donor_number     VARCHAR(30),
    full_name        VARCHAR(255) NOT NULL,
    blood_group      VARCHAR(5) NOT NULL,
    gender           VARCHAR(20),
    date_of_birth    DATE,
    phone            VARCHAR(30),
    last_donation_at TIMESTAMPTZ,
    status           VARCHAR(20) NOT NULL DEFAULT 'active',
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS bloodbank.blood_unit (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id      UUID NOT NULL,
    unit_number      VARCHAR(30) NOT NULL,
    blood_group      VARCHAR(5) NOT NULL,
    component        VARCHAR(30) NOT NULL DEFAULT 'whole_blood',
    collection_date  DATE,
    expiry_date      DATE NOT NULL,
    volume_ml        INT,
    status           VARCHAR(20) NOT NULL DEFAULT 'available',
    donor_id         UUID,
    storage_location VARCHAR(50),
    received_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_bloodbank_unit_status ON bloodbank.blood_unit (status);
CREATE INDEX IF NOT EXISTS idx_bloodbank_unit_group ON bloodbank.blood_unit (blood_group);

CREATE TABLE IF NOT EXISTS bloodbank.blood_grouping (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id      UUID NOT NULL,
    unit_id          UUID NOT NULL,
    abo_result       VARCHAR(10) NOT NULL,
    rh_result        VARCHAR(10) NOT NULL,
    confirmed        BOOLEAN NOT NULL DEFAULT FALSE,
    confirmed_by     UUID,
    confirmed_at     TIMESTAMPTZ,
    grouped_by       UUID,
    grouped_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_bloodbank_grouping_unit ON bloodbank.blood_grouping (unit_id);

CREATE TABLE IF NOT EXISTS bloodbank.crossmatch (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id      UUID NOT NULL,
    patient_id       UUID NOT NULL,
    unit_id          UUID NOT NULL,
    technique        VARCHAR(30) NOT NULL,
    compatibility    VARCHAR(20) NOT NULL DEFAULT 'equivocal',
    result_note      TEXT,
    requested_by     UUID,
    performed_by     UUID,
    performed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS bloodbank.blood_reservation (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id      UUID NOT NULL,
    patient_id       UUID NOT NULL,
    unit_id          UUID NOT NULL,
    reserved_for     UUID,
    status           VARCHAR(20) NOT NULL DEFAULT 'reserved',
    reserved_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reserved_by      UUID,
    released_at      TIMESTAMPTZ,
    released_by      UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS bloodbank.blood_issue (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id      UUID NOT NULL,
    patient_id       UUID NOT NULL,
    unit_id          UUID NOT NULL,
    issued_for       UUID,
    status           VARCHAR(20) NOT NULL DEFAULT 'issued',
    requested_by     UUID,
    issued_by        UUID,
    issued_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS bloodbank.transfusion_record (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id         UUID NOT NULL,
    patient_id          UUID NOT NULL,
    unit_id             UUID NOT NULL,
    transfusion_started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    transfusion_ended_at   TIMESTAMPTZ,
    volume_ml           INT,
    start_rate_ml_per_hour INT,
    observations        TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'in_progress',
    performed_by        UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS bloodbank.transfusion_reaction (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id         UUID NOT NULL,
    patient_id          UUID NOT NULL,
    transfusion_record_id UUID NOT NULL,
    reaction_type       VARCHAR(40) NOT NULL,
    severity            VARCHAR(20) NOT NULL,
    onset_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    symptoms            TEXT,
    actions             TEXT,
    reported_by         UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT NOT NULL DEFAULT 0
);