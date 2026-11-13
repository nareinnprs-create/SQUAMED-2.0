# CHUNK 6 — SECTION GRAPH (enterprise, scale & resilience)
Ledger source for wave 7. Est. in **agent-plan weeks** (~8 wk total). Contract: `chunk-06-enterprise-scale.md`.

| # | Section | Contents | Depends on | Est. | Notes |
|---|---|---|---|---|---|
| 06.01 ⛔ | Multi-hospital group | Hospital switcher, group EMPI (cross-hospital identity), consent-gated record sharing (artifact required, adversarial suite), referrals + records view, config inheritance (central defaults → hospital overrides), group rate cards/packages, group user directory | chunk 5 exit | 1.5 | Dual review (consent + authz) |
| 06.02 | Telemedicine | WebRTC via provider SDK, Telemedicine Guidelines 2020 checklist encoded (RMP verified, consult-type rules — non-compliant BLOCK), payment link, documentation into standard encounter, tech-support escape path | chunk 5 exit | 1.0 | ⛔ external: video SDK provider (India latency + DPDP terms) |
| 06.03 | Patient portal v2 | ABHA login, booking, token tracking, reports, prescriptions, bills + online payment, discharge instructions, care-plan view, notification center, **Web Push live (retires chunk-0 stub, SC-5)**; **RN-vs-PWA decision week 2 by usage data** | chunk 5 exit | 1.0 | Own-data ABAC |
| 06.04 | Workforce / HR-lite | Employee master activation, rostering + shift planning, leave/attendance, credential-expiry registry + renewal tasks, nurse-patient ratio report; payroll stays external (export only) | chunk 5 exit | 1.0 | |
| 06.05 | Ops modules | Procurement (indent→PO→receiving→GRN→stock, vendor performance), CSSD (tray inventory, cycles, issue/return, traceability to OT cases), facilities/assets (registry, PM schedules, work orders), **full ED module** (triage, resus, dashboard) | chunk 5 exit | 1.5 | |
| 06.06 | BI / ClickHouse | Event-fed marts + nightly dimensional sync, KPI dictionary (§9) implemented, role command centres (CEO/COO/CFO/CMO/Nursing/Facilities) read-only, export centre; KPIs reconciled vs operational DB | chunk 5 exit | 1.0 | |
| 06.07 | Interop outbound FHIR | HAPI FHIR server (Patient/Encounter/Observation/DiagnosticReport/MedicationRequest profiles, India EHR-M aligned), hospital-to-hospital + ABDM data flows, document access semantics | 06.01 | 1.0 | Profiles validated in CI |
| 06.08 ⛔ | Resilience + retention + sandbox | Degraded mode per-module whitelist (identity/finance offline-BLOCKED), store-and-forward + replay dashboard, conflict rules; **DR drill RTO 4 h / RPO 15 min measured**; Temporal adopt/defer decision (evidence-based, honest ADR); DPDP erasure automation + legal-hold tooling; sandbox hospital (synthetic generator + training env) | chunk 5 exit | 1.5 | Replay of 10k queued events = zero loss/dup |
| 06.09 ⛔ | **Chunk exit** | Demo scenario, **second hospital configured in <1 day (no code)**, consent-gate adversarial suite, 1,500-user load, planned datacenter-kill drill (20 min degraded ward → failover), drill evidence pack, dual review | all | 0.5 | |

**Lanes (≈8 wk):** (06.01→06.07) · (06.05) · (06.02/06.03/06.04) · (06.06) · (06.08) · exit last. Raw sum 10.0 wk over 5 lanes.
