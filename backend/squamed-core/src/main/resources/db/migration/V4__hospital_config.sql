-- V4: Hospital Configuration Engine schema
-- Module: squamed-hospital-config

CREATE TABLE IF NOT EXISTS hospital_config.hospital (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(200) NOT NULL,
    code          VARCHAR(50) NOT NULL UNIQUE,
    legal_name    VARCHAR(300),
    country       VARCHAR(100) NOT NULL,
    region        VARCHAR(100),
    city          VARCHAR(100),
    address_line1 VARCHAR(300),
    address_line2 VARCHAR(300),
    postal_code   VARCHAR(20),
    phone         VARCHAR(30),
    email         VARCHAR(150),
    license_number VARCHAR(150),
    timezone      VARCHAR(50) NOT NULL DEFAULT 'UTC',
    currency      VARCHAR(10) NOT NULL DEFAULT 'USD',
    status        VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS hospital_config.campus (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID NOT NULL REFERENCES hospital_config.hospital(id),
    name          VARCHAR(200) NOT NULL,
    code          VARCHAR(50),
    address_line1 VARCHAR(300),
    city          VARCHAR(100),
    timezone      VARCHAR(50),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS hospital_config.building (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL REFERENCES hospital_config.hospital(id),
    campus_id    UUID REFERENCES hospital_config.campus(id),
    name         VARCHAR(200) NOT NULL,
    code         VARCHAR(50),
    floors       INTEGER NOT NULL DEFAULT 1,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS hospital_config.department (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL REFERENCES hospital_config.hospital(id),
    name         VARCHAR(200) NOT NULL,
    code         VARCHAR(50) NOT NULL,
    type         VARCHAR(50) NOT NULL,
    parent_id    UUID REFERENCES hospital_config.department(id),
    status       VARCHAR(20) NOT NULL DEFAULT 'active',
    description  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, code)
);

CREATE TABLE IF NOT EXISTS hospital_config.specialty (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL REFERENCES hospital_config.hospital(id),
    name         VARCHAR(200) NOT NULL,
    code         VARCHAR(50) NOT NULL,
    department_id UUID REFERENCES hospital_config.department(id),
    description  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, code)
);

CREATE TABLE IF NOT EXISTS hospital_config.ward (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL REFERENCES hospital_config.hospital(id),
    department_id UUID REFERENCES hospital_config.department(id),
    name         VARCHAR(200) NOT NULL,
    code         VARCHAR(50),
    type         VARCHAR(50) NOT NULL DEFAULT 'general',
    room_count   INTEGER NOT NULL DEFAULT 0,
    bed_count    INTEGER NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS hospital_config.room (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL REFERENCES hospital_config.hospital(id),
    ward_id      UUID REFERENCES hospital_config.ward(id),
    building_id  UUID REFERENCES hospital_config.building(id),
    floor        INTEGER,
    name         VARCHAR(200) NOT NULL,
    code         VARCHAR(50),
    type         VARCHAR(50) NOT NULL DEFAULT 'general',
    max_beds     INTEGER NOT NULL DEFAULT 1,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS hospital_config.bed (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL REFERENCES hospital_config.hospital(id),
    ward_id      UUID REFERENCES hospital_config.ward(id),
    room_id      UUID REFERENCES hospital_config.room(id),
    label        VARCHAR(50) NOT NULL,
    type         VARCHAR(50) NOT NULL DEFAULT 'standard',
    status       VARCHAR(20) NOT NULL DEFAULT 'available',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, label)
);

CREATE TABLE IF NOT EXISTS hospital_config.hospital_service (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL REFERENCES hospital_config.hospital(id),
    name         VARCHAR(200) NOT NULL,
    code         VARCHAR(50),
    category     VARCHAR(100),
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    description  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_hc_department_type ON hospital_config.department (hospital_id, type);
CREATE INDEX IF NOT EXISTS idx_hc_bed_room ON hospital_config.bed (room_id);
CREATE INDEX IF NOT EXISTS idx_hc_bed_ward ON hospital_config.bed (ward_id);
CREATE INDEX IF NOT EXISTS idx_hc_room_ward ON hospital_config.room (ward_id);
CREATE INDEX IF NOT EXISTS idx_hc_specialty_dept ON hospital_config.specialty (department_id);