# MASTER DATA MODEL — unified schema map
**Single source of truth for tables, ownership, and cross-module rules.** Chunk files define section-level detail; this file defines the *whole* and the rules that keep the monolith coherent. Migration files must match this map — drift fails the architecture gate.

## 1. Conventions (all tables)
- Primary keys: **ULID** (`CHAR(26)`), not auto-increment — merge-safe, offline-generatable.
- Every tenant-scoped table: `tenant_id UUID NOT NULL` + RLS policy (chunk 0 §00.04).
- Every mutable table: `created_at, created_by, updated_at, updated_by`; append-only tables (audit, events) skip `updated_*`.
- Soft delete only where legally required (patient); elsewhere status machines + audit. Clinical records are **never deleted** (retention §12).
- Money: `BIGINT` paise + `currency CHAR(3)`. Time: `TIMESTAMPTZ` UTC. All clinical numerals stored with unit + reference range.
- Naming: `snake_case`, singular, module-prefixed where ambiguous (`lab_result`, `rad_order`).
- Cross-module references: **by ID, no FK** — modules synchronize via events (keeps ArchUnit boundaries real). FKs only within a module's own tables.

## 2. Core shared kernel (module: `platform` — owns these, everyone references)

```text
tenant ─┬─< hospital ─< location_node (closure table) ─< bed
        ├─< staff_user ─< role_binding (user × role × org-node scope)
        ├─< config_document (type, version, jsonb, valid_from)
        └─< entitlement (plan, feature, limits)

patient ─┬─< patient_identifier (type: MRN|ABHA|MOBILE|NATIONAL, value, tenant)
         ├─< patient_guardian (guardian_patient_id, relationship)
         ├─< patient_merge (surviving_id, merged_id, reversed_of)   ← versioned, reversible
         ├─< empi_review (candidate_a, candidate_b, score, decision)
         └─< consent (purpose, scope, artifact jsonb, signature, expiry, withdrawn_at)

task ─< task_link (subject_type, subject_id)   ← polymorphic attachment
audit_event (actor, action, subject, before jsonb, after jsonb, prev_hash, hash)
event_outbox → event_dispatch (idempotency by event id)
notification_request ─< notification_attempt (channel, status, provider_ref)
consent_channel_optin (patient × channel × scope)
```

**Invariants (DB-enforced):** one active MRN per patient per hospital (partial unique index); merge reversal restores exact prior state (merge row is a pointer, never mutation); audit hash chain has no gaps (verify job); task SLA escalation re-fires only via escalation row, never duplicates.

## 3. Module ownership map (all tables through chunk 8)

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
Every table carrying personal or clinical data carries `retention_class` (from §12 schedule) resolved at write; enforcement job (chunk 6) anonymizes/erases per class and writes an audit event. Legal hold overrides: `legal_hold` flag checked before any erasure.

## 5. Index policy highlights
EMPI candidate lookup: `(tenant_id, mobile, dob)` trigram + phonetic name index. Tokens: unique `(tenant_id, dept_id, issued_date, serial)`. Beds: exclusion constraint — no overlapping active allocations. Stock ledger: running-balance enforced by trigger, not trust. Bills: immutable after `finalized_at` (trigger blocks UPDATE/DELETE).
