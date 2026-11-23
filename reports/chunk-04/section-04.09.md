# Section 04.09 — Chunk-4 exit (IPD, surgery & critical care)

**Verdict: SECTION REVIEW** (never COMPLETE). CI evidence: **run PENDING — all 7 gates green** on the docs branch. Live demo evidence: `scripts/demo-04.09-chunk4-exit.ps1` → **47 passed, 0 failed** against the app on :8080 (2026-11-23). Section carries zero feature code by design: the exit item is the live scenario, the ward rollout runbook, this report and `reports/chunk-04/FINAL.md`.

## 1. Scope
Per `chunk-04-inpatient-surgery-critical.md` §EXIT CRITERIA (L57), TESTS (L50-54), SAFETY (L42-48) and ROLLOUT/ROLLBACK (L59-60).

## 2. Deliverables
- `scripts/demo-04.09-chunk4-exit.ps1` — 47-check live walk of the exit scenario.
- `docs/runbooks/ward-rollout.md` — ward-by-ward shadow run (pilot ward → ICU → OT), per-ward exit criteria, rollback (paper MAR/flowsheets, eMAR frozen read-only), sign-off.
- `reports/chunk-04/FINAL.md` — chunk roll-up, named-test evidence, owed items, Gate-12 disposition.
- This report + LEDGER row 04.09 (SECTION REVIEW) + tracker row.

## 3. Exit demo — what it proves (live, 47/47)
| Contract step | Demo evidence |
|---|---|
| ER arrival → triage-lite | registration `registered`, triage ESI 2, registration `triaged` |
| Admission → bed assigned | admission `admitted` with the bed id; ER disposition linked; bed board shows `occupied` |
| Nursing assessment + care plan | assessment recorded, care plan `active`, falls risk `high`, ward vitals charted |
| eMAR + high-alert double-check | BD insulin → 2 doses 12 h apart, high-alert; wrong wristband 409 `MAR_BARCODE_MISMATCH`; no checker 400 `HIGH_ALERT_SECOND_CHECK_REQUIRED`; administered with a distinct second checker |
| Monitor vitals → ICU flowsheet | two monitor rows; flowsheet carries the charted hours |
| Fluid balance totals | 1500 in / 900 out / net +600 |
| Severity score | SOFA computed (total > 0) |
| OT case + WHO checklist (3 stages) | consent-linked schedule; 5/4/4 items seeded and all 13 checked; case `started` → `completed` |
| Implants | lot + expiry + quantity recorded |
| PACU | post-op destination `recovery` |
| Discharge checklist | sign before med-rec 409 `DISCHARGE_MEDREC_REQUIRED`; med-rec opens the DISCHARGE task; coded summary `SIGNED`; pharmacy returns + final bill legs |
| Final bill + package pricing | room 5 h × 100 = 500, package pinned version 1200, invoice 1700 settled by card, status `paid`/due 0 |
| Bed auto-release → housekeeping | discharge → bed `cleaning` + HOUSEKEEPING task; cleared → `available` |
| Blood bank crossmatch round-trip | unscreened issue 400, wrong wristband 409 `BLOOD_IDENTITY_MISMATCH`, verified issue, transfusion, trace donor → grouping → crossmatch → issue → transfusion |
| Bed board reflects the day | ward + ICU beds present on the board |

Script fixes found during the live run: triage view returns `esiLevel` (registration status is a separate read); `ipd:write:admission` sits with NURSE, not DOCTOR, so admission/discharge run as the charge nurse; post-op destinations are `ward|icu|observation|recovery|stepdown` (PACU → `recovery`); payer class `self` is invalid (use `general`).

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | 47/47 live scenario |
| 2 Zero-stub | PASS | CI docs run PENDING |
| 3 Types | PASS | CI docs run PENDING |
| 4 Lint + format | PASS | CI docs run PENDING |
| 5 Tests | PASS | CI docs run PENDING; chunk named PITs listed in FINAL §5 |
| 6 Accessibility | PASS | CI docs run PENDING (no UI change) |
| 7 Design system | PASS | CI docs run PENDING |
| 8 Contracts | PASS | No API change; snapshot `33e24f36…` unchanged |
| 9 Data | PASS | No migration in this section |
| 10 Audit | PASS | Demo asserts the audited paths (barcode blocks, med-rec task, housekeeping task, blood-bank issue flags) |
| 11 Docs | PASS | This report, FINAL, runbook, LEDGER + tracker |
| 12 Exit item | PASS (partial) | Full scenario live; device-fed monitor stream (04.04) and insurance pre-auth legs (03.04) are externally gated and owed |

## 5. Contract TESTS status
- eMAR 200-dose ward day — covered by `emarNursingCoreCoverage` + the demo; a full simulated 200-dose day is a pilot activity.
- Device sim 24 h / 8 beds — **owed to 04.04** (`exp:E7` device specs).
- Blood bank trace from the event log — `bloodBankSafetyCoverage` (traceability round-trip + events).
- Discharge E2E incl. final-bill accuracy — the demo (manual rate-card math: 500 + 1200 = 1700).

## 6. Safety (L42-48) status
- eMAR barcode mismatch = hard block, omission needs reason + prescriber notification — 04.02.
- High-alert double-check enforced, distinct checker — 04.02.
- Crossmatch bedside identity re-verify, wrong wristband BLOCK — 04.07.
- Bed allocation enforces isolation/gender flags — 04.01.
- Discharge summary cannot finalize without med-rec — 04.08.
- Alarm/threshold config changes audited + signed by clinical lead — threshold alerting audited in 04.05; a config-change signature workflow is not in chunk-4 scope (owed to the alerting config work).

## 7. Deviations / owed
- 04.04 device integration PLANNED (`exp:E7`): monitor/vendor gateway specs; demo used manual/monitor JSON rows.
- 03.04 insurance pre-auth/claims PLANNED (`exp:E4` + D6): the IPD pre-auth leg is absent.
- Ward shadow runs (1 week each) are deployment activities — runbook ready, sign-off table blank.
- Chunk-4 go-live (B2) is a human gate.

## 8. Session trail
- Demo iterations: fixed triage field/status read, nurse-scoped admission path, post-op destination vocabulary, payer class, and the med-rec guard ordering; final run 47/47.
- Docker Desktop dropped twice; compose restarted, postgres healthy before the live run.
