-- V92: ops modules (chunk-06 section 06.05)
-- Procurement (indent -> PO -> GRN -> stock), CSSD trays/cycles/issue traceability,
-- facilities assets + work orders, and the full ED encounter flow.
-- Rollback note: additive only - drop schema ops.

CREATE SCHEMA IF NOT EXISTS ops;

CREATE TABLE IF NOT EXISTS ops.purchase_requisition (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL,
    number       VARCHAR(40) NOT NULL,
    department   VARCHAR(100) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    notes        VARCHAR(600),
    requested_by UUID,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ops.requisition_line (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requisition_id UUID NOT NULL,
    item_code      VARCHAR(60) NOT NULL,
    item_name      VARCHAR(200) NOT NULL,
    quantity       INTEGER NOT NULL,
    unit_price_qar NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ops.purchase_order (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL,
    requisition_id UUID NOT NULL,
    vendor_id      UUID,
    number         VARCHAR(40) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    total_qar      NUMERIC(14,2) NOT NULL DEFAULT 0,
    issued_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ops.goods_receipt (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    po_id       UUID NOT NULL,
    number      VARCHAR(40) NOT NULL,
    received_by UUID,
    notes       VARCHAR(400),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ops.goods_receipt_line (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grn_id      UUID NOT NULL,
    item_code   VARCHAR(60) NOT NULL,
    quantity    INTEGER NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ops.stock_item (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    item_code       VARCHAR(60) NOT NULL,
    item_name       VARCHAR(200) NOT NULL,
    quantity_on_hand INTEGER NOT NULL DEFAULT 0,
    reorder_level   INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, item_code)
);

CREATE TABLE IF NOT EXISTS ops.cssd_tray (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    code        VARCHAR(60) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, code)
);

CREATE TABLE IF NOT EXISTS ops.cssd_cycle (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL,
    tray_id      UUID NOT NULL,
    cycle_number INTEGER NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    result       VARCHAR(20),
    operator     VARCHAR(120),
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ops.cssd_issue (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id         UUID NOT NULL,
    tray_id             UUID NOT NULL,
    surgery_schedule_id UUID,
    issued_by           UUID,
    returned_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ops.asset (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID NOT NULL,
    code          VARCHAR(60) NOT NULL,
    name          VARCHAR(200) NOT NULL,
    category      VARCHAR(60) NOT NULL DEFAULT 'equipment',
    location      VARCHAR(120),
    status        VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    next_pm_on    DATE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, code)
);

CREATE TABLE IF NOT EXISTS ops.work_order (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id      UUID NOT NULL,
    asset_id         UUID NOT NULL,
    number           VARCHAR(40) NOT NULL,
    type             VARCHAR(20) NOT NULL DEFAULT 'CORRECTIVE',
    status           VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    description      VARCHAR(600) NOT NULL,
    downtime_minutes INTEGER NOT NULL DEFAULT 0,
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ops.ed_encounter (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id      UUID NOT NULL,
    patient_id       UUID NOT NULL,
    triage_level     INTEGER NOT NULL,
    chief_complaint  VARCHAR(400) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    resus_activated  BOOLEAN NOT NULL DEFAULT FALSE,
    closed_at        TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ops_ed_status ON ops.ed_encounter (hospital_id, status, triage_level);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'ops', 'read',  'ops',         'Read procurement, CSSD, facilities and ED data'),
    (gen_random_uuid(), 'ops', 'write', 'procurement', 'Manage requisitions, purchase orders, receipts and stock'),
    (gen_random_uuid(), 'ops', 'write', 'cssd',        'Manage CSSD trays, cycles and issue/return'),
    (gen_random_uuid(), 'ops', 'write', 'facilities',  'Manage assets and work orders'),
    (gen_random_uuid(), 'ops', 'write', 'ed',          'Run ED encounters, triage and disposition')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role (name, display_name, description, is_system)
VALUES
    ('PROCUREMENT_OFFICER', 'Procurement Officer', 'Indents, purchase orders, receiving and stock', TRUE),
    ('CSSD_TECHNICIAN', 'CSSD Technician', 'Tray inventory, sterilization cycles and issue/return', TRUE),
    ('FACILITIES_OFFICER', 'Facilities Officer', 'Assets, PM schedules and work orders', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'ops'
WHERE r.name = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'ops' AND (p.action = 'read' OR (p.action = 'write' AND p.resource = 'procurement'))
WHERE r.name = 'PROCUREMENT_OFFICER'
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'ops' AND (p.action = 'read' OR (p.action = 'write' AND p.resource = 'cssd'))
WHERE r.name = 'CSSD_TECHNICIAN'
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'ops' AND (p.action = 'read' OR (p.action = 'write' AND p.resource = 'facilities'))
WHERE r.name = 'FACILITIES_OFFICER'
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'ops' AND (p.action = 'read' OR (p.action = 'write' AND p.resource = 'ed'))
WHERE r.name IN ('DOCTOR', 'NURSE')
ON CONFLICT DO NOTHING;
