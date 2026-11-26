-- V103: full kiosk + wayfinding (chunk-07 section 07.06)
-- Payment kiosk receipts, report-collection receipts and an indoor routing graph per campus.
-- Vendor map import remains external (E12); the graph/routing substrate is platform-native.
-- Rollback note: additive only - drop schema kiosk; delete the kiosk permissions and KIOSK grants.

CREATE SCHEMA IF NOT EXISTS kiosk;

CREATE TABLE IF NOT EXISTS kiosk.payment_receipt (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL,
    patient_id     UUID NOT NULL,
    invoice_id     UUID NOT NULL,
    payment_id     UUID NOT NULL,
    amount_qar     NUMERIC(12,2) NOT NULL,
    method         VARCHAR(30) NOT NULL,
    receipt_number VARCHAR(40) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, receipt_number)
);

CREATE INDEX IF NOT EXISTS idx_kiosk_payment_patient ON kiosk.payment_receipt (hospital_id, patient_id);

CREATE TABLE IF NOT EXISTS kiosk.report_collection (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id    UUID NOT NULL,
    patient_id     UUID NOT NULL,
    report_type    VARCHAR(10) NOT NULL,
    report_id      UUID NOT NULL,
    receipt_number VARCHAR(40) NOT NULL,
    collected_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, report_type, report_id),
    UNIQUE (hospital_id, receipt_number)
);

CREATE TABLE IF NOT EXISTS kiosk.campus (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    code        VARCHAR(40) NOT NULL,
    name        VARCHAR(160) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (hospital_id, code)
);

CREATE TABLE IF NOT EXISTS kiosk.map_node (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL,
    campus_id  UUID NOT NULL REFERENCES kiosk.campus(id) ON DELETE CASCADE,
    code       VARCHAR(40) NOT NULL,
    name       VARCHAR(160) NOT NULL,
    node_type  VARCHAR(30) NOT NULL DEFAULT 'ROOM',
    floor      VARCHAR(20),
    x          NUMERIC(10,2),
    y          NUMERIC(10,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version    BIGINT NOT NULL DEFAULT 0,
    UNIQUE (campus_id, code)
);

CREATE TABLE IF NOT EXISTS kiosk.map_edge (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID NOT NULL,
    campus_id    UUID NOT NULL REFERENCES kiosk.campus(id) ON DELETE CASCADE,
    from_node_id UUID NOT NULL REFERENCES kiosk.map_node(id) ON DELETE CASCADE,
    to_node_id   UUID NOT NULL REFERENCES kiosk.map_node(id) ON DELETE CASCADE,
    distance_m   NUMERIC(10,2) NOT NULL,
    accessible   BOOLEAN NOT NULL DEFAULT TRUE,
    bidirectional BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0,
    UNIQUE (campus_id, from_node_id, to_node_id)
);

CREATE INDEX IF NOT EXISTS idx_map_edge_campus ON kiosk.map_edge (campus_id);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'kiosk', 'read',  'kiosk', 'Kiosk lookups: dues, reports, wayfinding'),
    (gen_random_uuid(), 'kiosk', 'write', 'kiosk', 'Kiosk payments, report collection and map administration')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
JOIN identity.permission p ON p.domain = 'kiosk'
WHERE r.name IN ('SYSTEM_ADMIN', 'HOSPITAL_ADMIN', 'KIOSK')
ON CONFLICT DO NOTHING;
