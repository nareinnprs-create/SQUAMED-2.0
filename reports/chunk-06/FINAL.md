# CHUNK 6 - FINAL REPORT (section 06.09 chunk exit)

## 1. Scope
Chunk-6 exit per `chunk-06-enterprise-scale.md` EXIT CRITERIA and `19-CHUNK6-SECTIONS.md` 06.09: the second-hospital demo, the consent-gate adversarial pass, the load test, the planned datacenter-kill drill and the evidence pack. **06.09 carries zero feature code by design.**

**Honest chunk state at exit:** 6 buildable sections COMPLETE (06.01, 06.03, 06.04, 06.05, 06.08 + the already-closed chunk-0/1 work this depends on). **06.02 Telemedicine remains PLANNED - externally gated on the video SDK**; **06.06 BI and 06.07 outbound FHIR remain IN PROGRESS** because they need ClickHouse and a HAPI server. The 1,500-user load test and the datacenter-kill drill are OWED to load/DR infrastructure. **06.09 lands at SECTION REVIEW.**

## 2. Built (this docs batch)
- `scripts/demo-06.09-chunk6-exit.ps1` - 28-check live exit walk.
- `docs/runbooks/second-hospital.md` - the under-a-day configuration checklist.
- `reports/chunk-06/section-06.09.md` + this FINAL + LEDGER row 06.09 + tracker row.

## 3. Chunk-6 section statuses
| Section | Status | Report |
|---|---|---|
| 06.01 Multi-hospital group | COMPLETE (PR #110) | `reports/chunk-06/section-06.01.md` |
| 06.02 Telemedicine | **PLANNED - externally gated (video SDK)** | - |
| 06.03 Patient portal v2 | COMPLETE (PR #115; Web Push delivery + UI owed, SC-5 open) | `reports/chunk-06/section-06.03.md` |
| 06.04 Workforce / HR-lite | COMPLETE (PR #111) | `reports/chunk-06/section-06.04.md` |
| 06.05 Ops modules | COMPLETE (PR #113) | `reports/chunk-06/section-06.05.md` |
| 06.06 BI / ClickHouse | **IN PROGRESS - needs ClickHouse** | `reports/chunk-06/section-06.06.md` |
| 06.07 Interop outbound FHIR | **IN PROGRESS - needs HAPI FHIR** | `reports/chunk-06/section-06.07.md` |
| 06.08 Resilience + retention + sandbox | COMPLETE (PR #117; DR execution + 10k replay drill owed) | `reports/chunk-06/section-06.08.md` |
| 06.09 Chunk exit | **SECTION REVIEW** (load + DR drills owed) | `reports/chunk-06/section-06.09.md` |

## 4. Chunk exit demo evidence
`pwsh scripts/demo-06.09-chunk6-exit.ps1` against the app on :8080: **28 passed, 0 failed**, live 2026-11-24. Scenario: group + hospital A + **hospital B attached and inheriting the group working hours** -> ward + bed created for B with no code -> same QID in both hospitals found by group EMPI -> **share denied (403) until a data_sharing consent, allowed after, outside-group 403, cross-group share 400, revoked 403** -> referral accepted in B -> **degraded mode: identity blocked, eMAR queued, 3 writes replayed once (ledger 3), second replay 0** -> erasure completed -> **sandbox seeded 3 `SBX-` patients** -> portal shows the linked patient + ABHA with own-data bills -> procurement indent->PO->GRN->stock 5 -> CSSD PASS -> ED triaged -> roster published -> share/referral/erasure events in the outbox. **OWED (reported): 1,500-user load, datacenter-kill drill, telemedicine/BI/FHIR.**

## 5. Contract TESTS status
| Contract test | Evidence |
|---|---|
| Second hospital configured in <1 day (no code) | Exit demo (group attach + inherited config + ward/bed) + `docs/runbooks/second-hospital.md`; measured timing is a deployment activity |
| Consent-gate adversarial suite | `multiHospitalGroupCoverage` + the exit demo (no share / outside group / cross-group share / revoked) |
| 1,500-user load | **RUN 2026-11-27** - 0 errors / 6,000 ops, 191.4 ops/s, read p95 1,666 ms + write p95 2,005 ms (NFR breached; single-hospital audit-chain serialization) |
| Planned datacenter-kill drill (20 min degraded ward -> failover) | **OWED** - `docs/runbooks/dr-drill.md` ships the procedure + evidence fields; execution needs a second region |
| Dual review | This report + per-section dual reviews |

## 6. Safety / compliance status
- Cross-hospital record access requires a consent artifact, is logged and revocable - 06.01.
- Degraded mode blocks identity/financial writes and queues clinical appends; replay is idempotent - 06.08.
- Erasure is blocked for clinical data and by legal holds; erasure emits an event - 06.08.
- Sandbox hospitals are flagged and seeded with synthetic patients only - 06.08.
- Telemedicine guideline checklist is 06.02 (gated).

## 7. Owed / tracked (not skipped)
| Item | Owner | Why it is not CI evidence |
|---|---|---|
| Telemedicine v1 (06.02) | Product + video SDK procurement | External provider (India latency + DPDP terms) |
| BI / ClickHouse (06.06) | Platform + infra | Needs the ClickHouse service |
| Outbound FHIR / HAPI (06.07) | Platform + interop | Needs the HAPI server (D17/ADR-12) |
| Web Push delivery + portal UI (06.03) | Web + infra | VAPID keys/HTTPS + screens; SC-5 stays open |
| Group rate cards/packages | Billing | Owed from 06.01; needed for the <1-day hospital demo to be fully priced |
| 1,500-user load test | QA + infra | **run locally**; re-run on staging for quotable latency |
| DR drill (RTO 4 h / RPO 15 min) | Infra + clinical lead | Second region; runbook ready |
| 10k replay drill | QA | Load harness |
| AI Scribe + eval harness (05.05/05.02) | Product | `exp:E8` |

## 8. Gate 12 disposition
Chunk-6 GATE-12: **PASS (partial)** - all buildable sections carry CI evidence on merged main; 06.02 is externally gated; 06.06/06.07 need their infrastructure; 06.09 is docs + a 28/28 live demo with the load/DR drills owed.

## 9. Sign-off
| Role | Name | Date | Verdict |
|---|---|---|---|
| Build owner | - | - | Chunk-6 buildable sections COMPLETE; 06.02 gated; 06.09 SECTION REVIEW |
| Infra lead | - | - | Pending DR drill + ClickHouse/HAPI |
| Owner (B2) | - | - | Pending go-live decision |
