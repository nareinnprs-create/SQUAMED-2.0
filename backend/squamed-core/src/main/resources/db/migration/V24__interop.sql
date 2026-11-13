-- V24: Interoperability (FHIR/HL7) schema and seed permissions
-- Module: interop

CREATE SCHEMA IF NOT EXISTS interop;

CREATE TABLE IF NOT EXISTS interop.integration_connection (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id         UUID,
    name                VARCHAR(160) NOT NULL,
    connection_type     VARCHAR(30) NOT NULL,
    endpoint            VARCHAR(500),
    username            VARCHAR(255),
    password            VARCHAR(255),
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    last_health_check_at TIMESTAMPTZ,
    last_success_at     TIMESTAMPTZ,
    last_error          TEXT,
    error_count         INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_integration_connection_type ON interop.integration_connection (connection_type);

CREATE TABLE IF NOT EXISTS interop.hl7_message (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID,
    message_type VARCHAR(50),
    control_id   VARCHAR(100),
    direction    VARCHAR(20) NOT NULL,
    source       VARCHAR(50),
    ack_code     VARCHAR(10),
    raw_message  TEXT,
    parsed_json  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_hl7_message_type ON interop.hl7_message (message_type);
CREATE INDEX IF NOT EXISTS idx_hl7_message_created ON interop.hl7_message (created_at);

INSERT INTO identity.permission (id, domain, action, resource, description)
VALUES
    ('00000000-0000-0000-0000-00000000f106', 'integration', 'read',   'integration', 'Read integrations, FHIR/HL7 and connections'),
    ('00000000-0000-0000-0000-00000000f107', 'integration', 'manage', 'integration', 'Create and manage integration connections')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id, constraints)
SELECT r.id, p.id, NULL
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.domain = 'integration'
ON CONFLICT DO NOTHING;