-- V70: RIS-lite + imaging (chunk-02 section 02.05)
-- Buy-list integration: PACS = Orthanc, viewer = OHIF (no DICOM storage or rendering here).
-- We ship the RIS surfaces Orthanc/OHIF consume: modality worklist feed (JSON for the MWL
-- channel), study completion with Orthanc refs, radiologist worklist, structured report with
-- ICD/SNOMED codes + versioned amendments, critical-finding escalation (same SLA rule as 02.03),
-- scoped viewer tokens, and the imaging charge hook.
-- Rollback note: additive only — drop schema radiology cascade, drop sequence radiology.accession_seq,
-- delete identity.permission rows (domain 'radiology') and their role grants to revert.

CREATE SCHEMA IF NOT EXISTS radiology;

-- Concurrency-safe accession numbers for the modality worklist (RAD-yyyyMMdd-NNNN).
CREATE SEQUENCE IF NOT EXISTS radiology.accession_seq;

CREATE TABLE IF NOT EXISTS radiology.rad_order (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID NOT NULL,
    patient_id    UUID NOT NULL,
    encounter_id  UUID,
    accession     VARCHAR(40) NOT NULL UNIQUE,
    modality      VARCHAR(16) NOT NULL,
    body_part     VARCHAR(80) NOT NULL,
    priority      VARCHAR(10) NOT NULL DEFAULT 'routine' CHECK (priority IN ('routine', 'urgent', 'stat')),
    status        VARCHAR(12) NOT NULL DEFAULT 'ordered' CHECK (status IN ('ordered', 'scheduled', 'in_progress', 'completed', 'reported', 'signed')),
    clinical_note VARCHAR(400),
    ordered_by    UUID,
    ordered_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_rad_order_hospital_status ON radiology.rad_order (hospital_id, status);
CREATE INDEX IF NOT EXISTS idx_rad_order_patient ON radiology.rad_order (patient_id);

CREATE TABLE IF NOT EXISTS radiology.study (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id           UUID NOT NULL REFERENCES radiology.rad_order(id),
    hospital_id        UUID NOT NULL,
    study_instance_uid VARCHAR(128) NOT NULL UNIQUE,
    orthanc_study_id   VARCHAR(64),
    series_count       INTEGER,
    completed_at       TIMESTAMPTZ,
    completed_by       UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_rad_study_order ON radiology.study (order_id);

CREATE TABLE IF NOT EXISTS radiology.report (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    study_id           UUID NOT NULL REFERENCES radiology.study(id),
    order_id           UUID NOT NULL REFERENCES radiology.rad_order(id),
    hospital_id        UUID NOT NULL,
    report_version     INTEGER NOT NULL DEFAULT 1,
    template_code      VARCHAR(40),
    findings           TEXT NOT NULL,
    impression         TEXT NOT NULL,
    icd_codes          VARCHAR(200),
    snomed_codes       VARCHAR(200),
    status             VARCHAR(10) NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'signed')),
    signed_by          UUID,
    signed_at          TIMESTAMPTZ,
    amends_report_id   UUID REFERENCES radiology.report(id),
    amendment_reason   VARCHAR(400),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_rad_report_order ON radiology.report (order_id);
-- Version numbers are unique per order: concurrent amendments cannot mint the same version twice.
CREATE UNIQUE INDEX IF NOT EXISTS uq_rad_report_order_version ON radiology.report (order_id, report_version);

CREATE TABLE IF NOT EXISTS radiology.critical_finding (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id       UUID NOT NULL REFERENCES radiology.report(id),
    hospital_id     UUID NOT NULL,
    severity        VARCHAR(10) NOT NULL CHECK (severity IN ('critical', 'urgent')),
    finding         TEXT NOT NULL,
    sla_minutes     INTEGER NOT NULL DEFAULT 60,
    status          VARCHAR(14) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ACKNOWLEDGED')),
    raised_by       UUID,
    raised_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    acknowledged_by UUID,
    acknowledged_at TIMESTAMPTZ,
    ack_note        VARCHAR(400),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_rad_critical_hospital_status ON radiology.critical_finding (hospital_id, status);

-- Permissions: imaging read/order/report/sign/ack. Doctors order and draft; radiologists sign.
INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    (gen_random_uuid(), 'radiology', 'read',   'worklist', 'Read the imaging worklist and MWL feed'),
    (gen_random_uuid(), 'radiology', 'order',  'study',    'Order imaging studies'),
    (gen_random_uuid(), 'radiology', 'draft',  'report',   'Draft imaging reports'),
    (gen_random_uuid(), 'radiology', 'sign',   'report',   'Sign imaging reports'),
    (gen_random_uuid(), 'radiology', 'ack',    'critical', 'Acknowledge critical imaging findings')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE p.domain = 'radiology'
  AND r.name IN ('RADIOLOGIST', 'DOCTOR', 'SYSTEM_ADMIN')
  AND NOT (r.name = 'DOCTOR' AND p.action = 'sign')
  AND NOT (r.name = 'DOCTOR' AND p.action = 'ack')
ON CONFLICT DO NOTHING;
