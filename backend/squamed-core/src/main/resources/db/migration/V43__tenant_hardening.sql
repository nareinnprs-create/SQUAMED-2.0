-- 00.04 tenancy hardening (ADR-13 stage 1): hospital_id NOT NULL on tenant-scoped domain tables.
-- NULL rows are attributed to the primary (first-created) hospital. On a fresh database the tables
-- are empty and the constraints apply cleanly before bootstrap seeds the first hospital; if rows
-- exist without any hospital to attribute them to, the NOT NULL below fails loudly instead of guessing.
-- Stage 2 (Postgres RLS) closes row-level read isolation before chunk 6.
-- Rollback: the NOT NULL constraints below can be dropped with ALTER TABLE ... ALTER COLUMN hospital_id DROP NOT NULL;
-- backfilled values are historical attributions and are not auto-reversible.

-- Primary hospital = first created (matches TenantResolver.primaryHospitalId()).
CREATE TEMP TABLE _primary_hospital AS
    SELECT id FROM hospital_config.hospital ORDER BY created_at ASC, id ASC LIMIT 1;

-- Backfill NULLs to the primary hospital (dev data was written under the platform-admin token).
UPDATE ai.interaction_log        SET hospital_id = (SELECT id FROM _primary_hospital) WHERE hospital_id IS NULL;
UPDATE billing.charge            SET hospital_id = (SELECT id FROM _primary_hospital) WHERE hospital_id IS NULL;
UPDATE billing.invoice           SET hospital_id = (SELECT id FROM _primary_hospital) WHERE hospital_id IS NULL;
UPDATE billing.tariff_item       SET hospital_id = (SELECT id FROM _primary_hospital) WHERE hospital_id IS NULL;
UPDATE clinical.encounter        SET hospital_id = (SELECT id FROM _primary_hospital) WHERE hospital_id IS NULL;
UPDATE clinical.order_entry      SET hospital_id = (SELECT id FROM _primary_hospital) WHERE hospital_id IS NULL;
UPDATE laboratory.lab_order      SET hospital_id = (SELECT id FROM _primary_hospital) WHERE hospital_id IS NULL;
UPDATE patient.patient_record    SET hospital_id = (SELECT id FROM _primary_hospital) WHERE hospital_id IS NULL;
UPDATE pharmacy.dispensation     SET hospital_id = (SELECT id FROM _primary_hospital) WHERE hospital_id IS NULL;
UPDATE queue.queue_entry         SET hospital_id = (SELECT id FROM _primary_hospital) WHERE hospital_id IS NULL;
UPDATE queue.service_point       SET hospital_id = (SELECT id FROM _primary_hospital) WHERE hospital_id IS NULL;
UPDATE reception.visit_registration SET hospital_id = (SELECT id FROM _primary_hospital) WHERE hospital_id IS NULL;
UPDATE scheduling.appointment    SET hospital_id = (SELECT id FROM _primary_hospital) WHERE hospital_id IS NULL;
UPDATE scribe.scribe_session     SET hospital_id = (SELECT id FROM _primary_hospital) WHERE hospital_id IS NULL;
UPDATE tasks.task                SET hospital_id = (SELECT id FROM _primary_hospital) WHERE hospital_id IS NULL;
UPDATE workflow.workflow_instance SET hospital_id = (SELECT id FROM _primary_hospital) WHERE hospital_id IS NULL;

-- NOT NULL on tenant-scoped domain data.
ALTER TABLE ai.interaction_log         ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE billing.charge             ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE billing.invoice            ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE billing.tariff_item        ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE clinical.encounter         ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE clinical.order_entry       ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE laboratory.lab_order       ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE patient.patient_record     ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE pharmacy.dispensation      ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE queue.queue_entry          ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE queue.service_point        ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE reception.visit_registration ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE scheduling.appointment     ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE scribe.scribe_session      ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE tasks.task                 ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE workflow.workflow_instance ALTER COLUMN hospital_id SET NOT NULL;

-- Deliberately nullable (platform-scoped or pre-tenant-attribution):
--   audit.audit_log            pre-auth login events and platform-admin activity
--   identity.session           platform-admin sessions
--   identity.device            platform-admin devices
--   identity.user_role         NULL = platform-wide role assignment (tenancy SOURCE for login)
--   ai.model_config            global model registry
--   ai.agent_config            global agent registry
--   workflow.workflow_definition  global templates, bound to a hospital at trigger time
--   communication.channel      direct channels are deliberately cross-hospital
--   notifications.notification system events can be platform-wide
--   interop.hl7_message        MLLP ingest has no hospital context yet
--   interop.integration_connection global connection registry
