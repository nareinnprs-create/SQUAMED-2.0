# CHUNK 06 — ENTERPRISE, SCALE & RESILIENCE
**Status:** PLANNED · **Window:** Jun → Oct 2028 (16 wk) · **Depends on:** chunk 05 exit
**Stack entering:** ClickHouse (BI), Temporal (long workflows, only if discharge/claim state machines justify), HAPI FHIR server (outbound interop), Tauri shell + React Native decision point
**Baseline (SC-6):** `master` prior build has an interop module (HL7 v2, FHIR stub, integration connections) and Next.js web app with real-time SSE — partial overlap only; group/telemedicine/BI/resilience have no prior code; FHIR direction decided by D17 before 06.07.

## GOAL
One hospital becomes many: **multi-hospital group, telemedicine, patient apps, ops modules, BI, hardened DR** — the platform survives losing a datacenter and gains a second hospital without a code change (blueprint §117 north stars).

## IN SCOPE
1. **Multi-hospital group:** hospital switcher (shell already has selector), group-level EMPI (cross-hospital identity resolution with consent-gated record sharing), cross-hospital referrals + records view, group config inheritance (central defaults, hospital overrides), group entities for rate cards/packages, group user directory.
2. **Telemedicine:** video consult (WebRTC via provider SDK), tele-consult per India Telemedicine Guidelines 2020 (RMP verification, consult-type rules, prescription validity rules), payment link flow, documentation into standard encounter, tech-support escape path.
3. **Patient portal/app v2:** ABHA login, appointments/booking, token tracking, reports (labs/imaging), prescriptions, bills + online payment, discharge instructions, care-plan view, notification center (**Web Push live here — retires the chunk-0 push stub**). **Decision point:** PWA suffices vs React Native app (decide week 2 of chunk by usage data).
4. **Workforce/HR-lite:** employee master activation, rostering + shift planning, leave/attendance, credential/expiry registry with renewal tasks, nurse-patient ratio report. (Payroll stays external via export — never build payroll.)
5. **Ops modules:** procurement (indent → PO → receiving → GRN → stock, vendor performance), CSSD (tray inventory, sterilization cycles, issue/return, traceability to OT cases), facilities/assets (registry, PM schedules, work orders, downtime), full ED module (triage, resus workflows, ED dashboard) — moved here from chunk 4 deferral.
6. **Analytics/BI:** ClickHouse fed by event stream + nightly dimensional sync; KPI dictionary §9 implemented; role command centres (CEO/COO/CFO/CMO/Nursing/Facilities per blueprint §55) as read-only dashboards; export center.
7. **Interop outbound:** HAPI FHIR server (Patient, Encounter, Observation, DiagnosticReport, MedicationRequest profiles, India EHR-M aligned) — hospital-to-hospital and ABDM data flows; document access semantics. (D17 resolved 2026-11-13 → ADR-12: sequencing confirmed — no pre-adoption; baseline read-only FhirService stays internal.)
8. **Resilience:** degraded mode (blueprint §71 for real): offline write-whitelist activated per module (registration-lite, eMAR read-only+queued administrations, vitals capture queued, queue display cached), store-and-forward with replay dashboard, conflict rules (server-wins except append-only clinical events which merge by occurrence time), DR drill: full regional failover meeting RTO 4 h/RPO 15 min, quarterly schedule established.
9. **Temporal adoption decision:** discharge workflow + claim lifecycle moved to Temporal **only if** the state machines accumulated retry/timeout complexity in chunks 3–4; otherwise documented deferral (honest ADR).
10. **Data retention & erasure automation:** DPDP erasure for non-clinical data, retention clock enforcement, legal-hold tooling, anonymization for analytics/benchmarking prep (blueprint §84 gate).
11. **Sandbox hospital:** synthetic-data generator + training environment (blueprint §77) — seeded with fake patients, used for all onboarding from here on.

## OUT OF SCOPE
Marketplace/agent SDK/dev portal → chunk 8. Sovereign/on-prem packaging → chunk 8. Group finance consolidation, multi-currency/FX, ESG, benchmarking → chunk 7/8 (see chunk files).

## DATA MODEL (key additions)
`group_entity, hospital_config_override, cross_hospital_share(consent ref), referral(group), teleconsult_session, roster, shift, leave, credential(+expiry), purchase_requisition/po/grn, cssd_tray(+cycles), asset(+work_order), ed_encounter(full), bi_mart(sync metadata), retention_policy, legal_hold, synthetic_seed_catalog`

## APIS
- `/group/hospitals|config:inherit|referrals`, `/patients/{id}:share-with-hospital`
- `/teleconsult/sessions:start|join|complete`
- `/portal/*` (patient-scoped, own-data only)
- `/hr/rosters|leave|credentials`, `/procurement/requisitions|pos|grns`
- `/cssd/trays|cycles`, `/facilities/assets|work-orders`, `/ed/encounters`
- `/bi/dashboards|kpis|exports`, `/fhir/*` (HAPI)
- `/admin/retention|legal-holds|erasure`

## EVENTS
`ReferralCreated/Accepted, RecordSharedConsented, TeleconsultStarted/Completed, RosterPublished, CredentialExpiring, POIssued/GoodsReceived, SterilizationCycleCompleted, WorkOrderCreated/Completed, EDEncounterClosed, KpiThresholdBreached, FailoverInitiated/Completed, ErasureCompleted`

## PERMISSIONS (adds)
Group roles: group-admin, group-auditor (cross-hospital read with consent checks). Procurement/CSSD/facilities/ED roles. Patient portal: own records only (ABAC by patient identity).

## SAFETY / COMPLIANCE
- Cross-hospital record access requires consent artifact or treatment-relationship assertion, logged (blueprint §73 "shared identity" ≠ shared records by default).
- Telemedicine: guideline checklist encoded (RMP registration verified, consult-type eligibility) — non-compliant flows BLOCK.
- Degraded mode: financial + identity writes remain offline-BLOCKED (blueprint §71 warning honored); clinical appends allowed, replay integrity verified in drill.
- DR drill evidence pack retained for hospital IT audits.
- Benchmarking exports: anonymization verified before any aggregate leaves tenant (gate for blueprint §84).

## TESTS
- DR drill: full failover to standby region, RTO/RPO measured against §6 targets, replay of 10,000 queued offline events with zero loss/duplication.
- Consent gate: cross-hospital access without artifact = denied + security event (adversarial suite).
- BI numbers: KPI values reconciled against operational DB within tolerance for a simulated month.
- Group EMPI: same patient across 2 hospitals — deterministic link, record share only with consent, referral round-trip.
- Load: 2-hospital group, 1,500 concurrent users sustained on NFR latency.

## EXIT CRITERIA (demo scenario, live)
Second hospital configured in <1 day (config + masters import, no code) → patient registered in hospital A consults at hospital B via referral with consented record share → teleconsult completed with guideline-compliant prescription → group COO dashboard shows both hospitals' live KPIs → procurement cycle (indent→PO→receipt→stock) closes → CSSD tray traced to the OT case from chunk 4's record → **planned datacenter kill: degraded mode holds the ward for 20 min (eMAR read-only, vitals queued), failover completes within RTO/RPO, zero event loss proven by replay audit** → quarterly drill scheduled.

## ROLLOUT / ROLLBACK
Second hospital = new tenant onboarding runbook (validated by the <1-day exit test). Degraded mode exercised in staging monthly, prod drill at exit + quarterly.

## OPEN DECISIONS
- Temporal adopt/defer (evidence-based, week 8).
- React Native vs PWA for patient app (week 2).
- Video SDK provider (India latency + DPDP terms).
