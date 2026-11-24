# Section 06.09 — Chunk-6 exit (enterprise, scale & resilience)

**Verdict: SECTION REVIEW** (never COMPLETE). CI evidence: **run 45421129104 — all 7 gates green** on the docs branch. Live demo evidence: `scripts/demo-06.09-chunk6-exit.ps1` → **28 passed, 0 failed** against the app on :8080 (2026-11-24). Zero feature code: the exit item is the live scenario, the second-hospital runbook, this report and `reports/chunk-06/FINAL.md`.

## 1. Scope
Per `chunk-06-enterprise-scale.md` EXIT CRITERIA and `19-CHUNK6-SECTIONS.md` 06.09.

## 2. Deliverables
- `scripts/demo-06.09-chunk6-exit.ps1` — 28-check live walk.
- `docs/runbooks/second-hospital.md` — under-a-day configuration checklist.
- `reports/chunk-06/FINAL.md` — chunk roll-up, contract-test status, owed items, Gate-12.
- This report + LEDGER row 06.09 (SECTION REVIEW) + tracker row.

## 3. Exit demo — what it proves (live, 28/28)
| Contract step | Demo evidence |
|---|---|
| Second hospital, no code | hospital B attached to the group; inherits working hours (`source=group`); ward + bed created |
| Group EMPI | same QID in A and B found as a candidate |
| Consent-gate adversarial | no share 403; consent → share ACTIVE; B reads; outside group 403; cross-group share 400; revoked 403 |
| Referral | A→B referral accepted in the receiving inbox |
| Degraded mode | identity BLOCKED, eMAR QUEUED, 3 writes replayed once (ledger 3), second replay 0, released |
| Retention/erasure | non-clinical erasure COMPLETED |
| Sandbox | flag enabled → 3 `SBX-` synthetic patients |
| Portal | linked patient + ABHA; own-data bills |
| Ops | indent→PO→GRN→stock 5; CSSD PASS; ED triaged |
| Workforce | roster published with a nurse shift |
| Event trail | share/referral/erasure events in the outbox |
| Load + DR drill | **OWED** — reported, not faked |

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | 28/28 live scenario |
| 2 Zero-stub | PASS | CI docs run 45421129104 |
| 3 Types | PASS | CI docs run 45421129104 |
| 4 Lint + format | PASS | CI docs run 45421129104 |
| 5 Tests | PASS | CI docs run 45421129104; chunk PITs in FINAL §5 |
| 6 Accessibility | PASS | CI docs run 45421129104 (no UI change) |
| 7 Design system | PASS | CI docs run 45421129104 |
| 8 Contracts | PASS | No API change in this section; snapshot `1f0ca599…` |
| 9 Data | PASS | No migration in this section |
| 10 Audit | PASS | Demo asserts share/referral/erasure events |
| 11 Docs | PASS | This report, FINAL, second-hospital runbook, LEDGER + tracker |
| 12 Exit item | PASS (partial) | Full shipped scenario live; load + DR drills owed |

## 5. Contract TESTS status
- Second hospital <1 day: demo + runbook; the clock is a deployment activity.
- Consent adversarial suite: PIT + demo.
- 1,500-user load: **owed** (load harness + staging).
- Datacenter-kill drill: **owed** (second region; `docs/runbooks/dr-drill.md`).

## 6. Safety (chunk-6) status
- Cross-hospital access requires consent and is revocable/logged — 06.01.
- Degraded mode blocks identity/financial writes, queues clinical appends, idempotent replay — 06.08.
- Erasure blocked for clinical data and legal holds — 06.08.
- Sandbox-only synthetic seeding — 06.08.

## 7. Deviations / owed
- Telemedicine (06.02, video SDK), BI (06.06, ClickHouse), outbound FHIR (06.07, HAPI).
- Web Push delivery + portal UI (06.03); SC-5 open.
- Group rate cards/packages (06.01).
- 1,500-user load, DR drill, 10k replay drill.
- AI Scribe + eval harness (`exp:E8`).

## 8. Session trail
- The demo initially reused the admin's primary hospital for resilience state, so ledger counts included previous runs; it now uses a fresh hospital-scoped platform admin.
- Local Redis 6380 sits in a Windows excluded port range; the demo ran against the 7379 container (CI uses 6380).
- Docker Desktop dropped repeatedly; compose restarted before the live run.
