-- V18: Add missing audit columns to billing.invoice_line
-- Module: squamed-billing

ALTER TABLE billing.invoice_line
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_by UUID;