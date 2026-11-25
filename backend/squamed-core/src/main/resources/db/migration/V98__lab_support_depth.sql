-- V98: lab & support depth (chunk-07 section 07.09)
-- Microbiology ID/AST + antibiogram, histopathology/cytology slide workflow,
-- blood-bank NAT screening records and full food-service operations.
-- Rollback note: additive only - drop laboratory.micro_ast, laboratory.micro_culture,
-- laboratory.path_slide, laboratory.path_specimen, bloodbank.nat_screening,
-- ipd.tray_delivery, ipd.kitchen_production, ipd.menu_item; delete the kitchen permissions
-- and KITCHEN_STAFF role grants.

CREATE TABLE IF NOT EXISTS laboratory.micro_culture (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL,
    lab_order_id   UUID NOT NULL REFERENCES laboratory.lab_order(id) ON DELETE CASCADE,
    specimen_type  VARCHAR(60) NOT NULL,
    status         VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    organism_code  VARCHAR(40),
    organism_name  VARCHAR(200),
    identified_at  TIMESTAMPTZ,
    identified_by  UUID,
    finalized_at   TIMESTAMPTZ,
    finalized_by   UUID,
    notes          VARCHAR(600),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    UNIQUE (lab_order_id)
);

CREATE INDEX IF NOT EXISTS idx_micro_culture_hospital ON laboratory.micro_culture (hospital_id, status);
CREATE INDEX IF NOT EXISTS idx_micro_culture_organism ON laboratory.micro_culture (hospital_id, organism_code);

CREATE TABLE IF NOT EXISTS laboratory.micro_ast (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID NOT NULL,
    culture_id        UUID NOT NULL REFERENCES laboratory.micro_culture(id) ON DELETE CASCADE,
    antibiotic_code   VARCHAR(40) NOT NULL,
    antibiotic_name   VARCHAR(120) NOT NULL,
    susceptibility    VARCHAR(1) NOT NULL,
    mic               NUMERIC(10,3),
    panel             VARCHAR(60) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0,
    UNIQUE (culture_id, antibiotic_code)
);

CREATE INDEX IF NOT EXISTS idx_micro_ast_culture ON laboratory.micro_ast (culture_id);

CREATE TABLE IF NOT EXISTS laboratory.path_specimen (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID NOT NULL,
    patient_id        UUID NOT NULL,
    accession_no      VARCHAR(40) NOT NULL,
    specimen_type     VARCHAR(20) NOT NULL,
    site              VARCHAR(120),
    gross_description VARCHAR(2000),
    grossed_by        UUID,
    grossed_at        TIMESTAMPTZ,
    status            VARCHAR(30) NOT NULL DEFAULT 'ACCESSIONED',
    diagnosis_code    VARCHAR(40),
    diagnosis_text    VARCHAR(2000),
    signed_by         UUID,
    signed_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, accession_no)
);

CREATE INDEX IF NOT EXISTS idx_path_specimen_hospital ON laboratory.path_specimen (hospital_id, status);

CREATE TABLE IF NOT EXISTS laboratory.path_slide (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL,
    specimen_id  UUID NOT NULL REFERENCES laboratory.path_specimen(id) ON DELETE CASCADE,
    slide_no     INTEGER NOT NULL,
    stain        VARCHAR(60) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PREPARED',
    ready_at     TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0,
    UNIQUE (specimen_id, slide_no)
);

CREATE INDEX IF NOT EXISTS idx_path_slide_specimen ON laboratory.path_slide (specimen_id);

CREATE TABLE IF NOT EXISTS bloodbank.nat_screening (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    unit_id     UUID NOT NULL REFERENCES bloodbank.blood_unit(id) ON DELETE CASCADE,
    assay       VARCHAR(60) NOT NULL,
    result      VARCHAR(20) NOT NULL,
    tested_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tested_by   UUID,
    notes       VARCHAR(400),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (unit_id, assay)
);

CREATE INDEX IF NOT EXISTS idx_nat_screening_unit ON bloodbank.nat_screening (unit_id);

CREATE TABLE IF NOT EXISTS ipd.menu_item (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    diet_type   VARCHAR(60) NOT NULL,
    meal        VARCHAR(20) NOT NULL,
    name        VARCHAR(120) NOT NULL,
    allergens   VARCHAR(400),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, diet_type, meal, name)
);

CREATE INDEX IF NOT EXISTS idx_menu_item_lookup ON ipd.menu_item (hospital_id, meal, diet_type);

CREATE TABLE IF NOT EXISTS ipd.kitchen_production (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    production_date DATE NOT NULL,
    meal            VARCHAR(20) NOT NULL,
    diet_type       VARCHAR(60) NOT NULL,
    planned_count   INTEGER NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    started_at      TIMESTAMPTZ,
    ready_at        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, production_date, meal, diet_type)
);

CREATE INDEX IF NOT EXISTS idx_kitchen_production_day ON ipd.kitchen_production (hospital_id, production_date, meal);

CREATE TABLE IF NOT EXISTS ipd.tray_delivery (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    diet_order_id   UUID NOT NULL REFERENCES ipd.diet_order(id) ON DELETE CASCADE,
    patient_id      UUID NOT NULL,
    production_date DATE NOT NULL,
    meal            VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PREPARED',
    prepared_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered_at    TIMESTAMPTZ,
    delivered_by    UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (diet_order_id, production_date, meal)
);

CREATE INDEX IF NOT EXISTS idx_tray_delivery_day ON ipd.tray_delivery (hospital_id, production_date, meal);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'kitchen', 'read',  'kitchen', 'Read menus, production plans and tray delivery'),
    (gen_random_uuid(), 'kitchen', 'write', 'kitchen', 'Manage menus, kitchen production and tray delivery')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role (name, display_name, description, is_system)
VALUES ('KITCHEN_STAFF', 'Kitchen Staff', 'Food-service production planning and tray delivery', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'kitchen'
WHERE r.name IN ('SYSTEM_ADMIN', 'HOSPITAL_ADMIN', 'KITCHEN_STAFF')
ON CONFLICT DO NOTHING;
