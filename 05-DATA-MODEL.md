# MASTER DATA MODEL — unified schema map
**Single source of truth for tables, ownership, and cross-module rules.** Chunk files define section-level detail; this file defines the *whole* and the rules that keep the monolith coherent. Naming reality: **the Flyway migrations are the authoritative table names** (`backend/squamed-core/src/main/resources/db/migration/`, `schema.table` per module schema). The §2/§3 diagrams are conceptual — no gate enforces this map; the enforcing tests are the PIT contract suite (see 06 §1).

## 1. Conventions (all tables)
- Primary keys: **UUID v4, app-generated** (`@UuidGenerator` on `BaseEntity`; the DB `DEFAULT gen_random_uuid()` is only a fallback) — not auto-increment, merge-safe.
- Tenant scoping: **`hospital_id UUID NOT NULL`** on tenant-scoped tables (the deliberately-nullable list is documented in `V43__tenant_hardening.sql`); the tenant is resolved server-side from the JWT `hp` claim (`TenantResolver`/`TenantGuards`, mechanics in 06 §1). **RLS policies are deferred to stage 2 (ADR-13, before chunk 6)** — row-level enforcement today is application-level (NOT NULL + load-then-guard), never `tenant_id`.
- Every table: `id`, `created_at`, `created_by`, `updated_at`, `updated_by`, **`version BIGINT NOT NULL`** (JPA optimistic lock, `@Version`). Extend `BaseEntity` (or `HospitalScopedEntity` for tenant-scoped tables) — never re-declare these columns; omitting `version` fails `ddl-auto: validate`. Append-only tables (audit, events) skip `updated_*`.
- Soft delete: `deleted_at/deleted_by` exists **only** on `identity.user` and `communication.message`; `patient.patient_record` uses a `records_status` machine instead; there is no generic JPA soft-delete infrastructure. Clinical records are never deleted (retention §12).
- Money: shipped convention is **`NUMERIC(12,2)` with `_qar`-suffixed columns** (Qatari Riyal implicit; e.g. `billing.invoice.total_qar`). The earlier BIGINT-paise plan is superseded; chunk 3 owns any currency generalization. Time: `TIMESTAMPTZ` UTC. Clinical numerals stored with unit + reference range.
- Naming: `snake_case`; migrations `V<n>__<snake_topic>.sql` under `db/migration` (numbering rules in 01 §4).
- MRN: `MQ-<YYYY>-<####>` from an atomic Postgres sequence (`V53__mrn_sequence.sql` + `MrnGenerator`) — collision-safe, never max+1 in app code.
- Config documents: 8 fixed types (`working-hours, holidays, shifts, workflow, queue, approval, task-sla, dnd-hours`), per-type JSON Schemas on classpath `resources/schemas/config/`, versioned publish (validate → supersede actives → version+1; retry the PUT on unique-conflict), emits `config.changed`.
- Cross-module references: **by ID, no cross-module FK** — modules synchronize via domain events. FKs only within a module's own tables.

## 2. Core shared kernel (module: `platform` — owns these, everyone references)

```text
tenant ─┬─< hospital ─< location_node (closure table) ─< bed
        ├─< identity.user ─< identity.user_role (user × role × hospital)
        ├─< config_document (type, version, jsonb, valid_from)
        └─< entitlement (plan, feature, limits)

patient ─┬─< patient_identifier (type: MRN|ABHA|MOBILE|NATIONAL, value, tenant)
         ├─< patient_guardian (guardian_patient_id, relationship)
         ├─< patient_merge (surviving_id, merged_id, reversed_of)   ← versioned, reversible
         ├─< empi_review (candidate_a, candidate_b, score, decision)
         └─< consent (purpose, scope, artifact jsonb, signature, expiry, withdrawn_at)

task ─< task_link (subject_type, subject_id)   ← polymorphic attachment
audit.audit_log (actor, action, subject, before jsonb, after jsonb, prev_hash, hash)
events.event_outbox (+ events.event_cursor)   ← idempotency by event id
notification_request ─< notification_attempt (channel, status, provider_ref)
consent_channel_optin (patient × channel × scope)
```

**Invariants (DB-enforced):** one active MRN per patient per hospital (partial unique index); merge reversal restores exact prior state (merge row is a pointer, never mutation); audit hash chain has no gaps (verify job); task SLA escalation re-fires only via escalation row, never duplicates.

## 3. Module ownership map (all tables through chunk 8)
*Conceptual — real names live in the migrations (e.g. billing owns `billing.invoice/invoice_line/charge/tariff_item`, interop ships `interop.integration_connection`/`interop.hl7_message`; `abdm_consent_artefact` and `analyzer_message_log` are not built).*

| Module | Owns tables (from chunk files) |
|---|---|
| scheduling | `appointment, schedule_template, session_slot, resource, waitlist_entry, token, queue` |
| encounter | `encounter, clinical_note, vitals_observation, diagnosis, allergy, condition, referral` |
| orders | `order (lab/imaging/pharmacy, status machine), prescription, order_item` |
| lab | `lab_order_item, specimen, accession, analyzer_message_log, result, result_amendment, qc_run, reference_range, report_document` |
| radiology | `rad_order, study, radiology_report` |
| pharmacy | `drug, formulary_entry, stock_item, stock_ledger, indent, dispense, controlled_register, temperature_log` |
| medication-safety | `interaction_rule, dose_rule` (+ check service) |
| billing | `charge_ledger_entry, rate_card, package, bill, bill_line, estimate, deposit, discount, tax_line, credit_note, payment, shift_cashup, recon_statement` |
| insurance | `payer, policy, preauth_request, claim, claim_status, denial` |
| him | `coding_assignment, coder_query, integrity_finding` |
| finance | `gl_journal, gl_account, fiscal_period` (chunk 7) |
| ipd | `admission, bed_allocation, transfer_log, care_plan, discharge_checklist, discharge_summary, diet_order` |
| nursing | `mar_schedule, mar_administration, flowsheet_observation, fluid_balance, risk_score, handover` |
| surgery | `ot_case, intraop_record, implant_log, anaesthesia_record, pacu_score` |
| bloodbank | `blood_donor, blood_component, crossmatch_request, transfusion_event` |
| devices | `device, device_stream_buffer, threshold_config` |
| ai | `ai_request, ai_draft, eval_dataset, eval_run, agent_registration, ai_incident, attention_item` |
| workforce | `roster, shift, leave, credential` |
| ops | `purchase_requisition, purchase_order, grn, cssd_tray, cssd_cycle, asset, work_order, ed_encounter` |
| interop | `abdm_link, abdm_consent_artefact, hl7_message_log, fhir_export` |
| ecosystem | `app_registration, api_credential, marketplace_listing, developer_account` (chunk 8) |

## 4. Retention & lifecycle columns
*Design target (not yet built):* the plan is that every table carrying personal or clinical data carries `retention_class` (from §12 schedule) resolved at write; enforcement job (chunk 6) anonymizes/erases per class and writes an audit event; legal hold overrides (`legal_hold` flag checked before any erasure). Today only the audit WORM 10-year retention exists (`V49`); the retention columns and erasure jobs do not.

## 5. Index policy highlights
EMPI candidate lookup: `(tenant_id, mobile, dob)` trigram + phonetic name index. Tokens: unique `(tenant_id, dept_id, issued_date, serial)`. Beds: exclusion constraint — no overlapping active allocations. Stock ledger: running-balance enforced by trigger, not trust. Bills: immutable after `finalized_at` (trigger blocks UPDATE/DELETE).
