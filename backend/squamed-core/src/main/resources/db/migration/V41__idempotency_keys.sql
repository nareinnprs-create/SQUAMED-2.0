-- V41: Idempotency middleware store (chunk 0 section 00.02, contract foundation)
-- Module: common

CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE IF NOT EXISTS platform.idempotency_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) NOT NULL,
    method          VARCHAR(10) NOT NULL,
    endpoint        VARCHAR(512) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    response_status INTEGER,
    response_body   TEXT,
    content_type    VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_idempotency_key UNIQUE (idempotency_key, method, endpoint)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_created_at ON platform.idempotency_keys (created_at);
