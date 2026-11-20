# Section 02.09 — Chunk exit (chunk 2)

**Gate-run assessment of the CURRENT tree** @ PR #80 (all chunk-2 feature PRs merged: #66, #68, #70, #72, #74, #76, #78). Verdict: **SECTION REVIEW** (never COMPLETE — same precedent as 01.12). CI evidence: **run 44705287265 — all 7 gates green**. Exit demo live: **41/41**, transcript `reports/demo-02.09-live.log`.

## 1. Scope
Per `15-CHUNK2-SECTIONS.md` 02.09: demo scenario, analyzer sim, critical-result drill timed vs SLA, interaction eval gate, FEFO test, lab parallel-run prep — all chunk-2 sections as the dependency. Zero feature code by design; the only code change is an exit-review permission fix (V71) found by the live demo.

## 2. Built
- `scripts/demo-02.09-chunk2-exit.ps1` — 41-check live walk of the chunk EXIT CRITERIA.
- `docs/runbooks/lab-parallel-run.md` — 2-week lab parallel run + pharmacy parallel stock count (daily checklists, exit criteria, rollback, sign-off table).
- `V71__doctor_lab_ordering.sql` — DOCTOR gains `laboratory:read/write:lab`, PATHOLOGIST gains `laboratory:read:lab` (exit criterion "doctor orders CBC" was impossible: only LAB_TECHNICIAN + admin held lab perms).
- `reports/chunk-02/FINAL.md` + this report + LEDGER row 02.09 + tracker row.

## 3. Demo evidence (live, 2026-11-20)
41/41 — critical result opened with SLA, release blocked `CRITICAL_RESULT_UNACKED` (400), ack → release, report v1 + `%PDF` + counter delivery, TAT rows, MWL feed, study completion + `StudyCompleted`, structured report (ICD J18.9 / SNOMED 385093006) + sign, critical finding SLA 30 + ack, controlled dispense with second authorizer, over-dispense blocked, controlled register with both actors, ledger −4/balance 36, lab + imaging `billing.chargeable_performed`, Patient 360 lab/imaging/medication thread.

Script bugs fixed during the run: lab-tech role for collect, critical value 12.0 (> master 10.0), hospital-pinned stock for FEFO, release-block status 400.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS (chunk) | Every chunk-2 section report + named PIT; demo 41/41 |
| 2 Zero-stub | PASS | CI run 44705287265 |
| 3 Types | PASS | CI run 44705287265 |
| 4 Lint + format | PASS | CI run 44705287265 |
| 5 Tests | PASS | CI run 44705287265; named chunk tests table in FINAL §5 |
| 6 Accessibility | PASS | CI run 44705287265 |
| 7 Design system | PASS | CI run 44705287265 |
| 8 Contracts | PASS | OpenAPI snapshot current (a408b879…, no API change in V71/demo) |
| 9 Data | PASS | V71 additive + rollback note |
| 10 Audit | PASS | Demo exercises audited trails (custody, critical ack, controlled register, overrides, ledger) |
| 11 Docs | PASS | FINAL + this report + runbook + LEDGER + tracker |
| 12 Exit item | PASS (partial) | Demo + named tests green; deployment/clinical gates owed per FINAL §7 |

## 5. Owed (not skipped)
Lab parallel run (2 weeks), pharmacy parallel stock count (5-day zero variance), clinical rule acceptance (B2), Orthanc/OHIF + Mirth production configs, hardware/analyzer soaks. Owned outside CI; tracked in FINAL §7 and the runbook.

## 6. Session trail
- Demo round 1: 31/41 — doctor 403 on lab order (V71 gap), collect needs lab tech, 6.8 not critical, stock not hospital-pinned (FEFO empty).
- Round 2: 40/41 — release-block returns 400, script expected 409.
- Round 3: **41/41** live on merged main code (bootRun, compose dev stack).
- Docker Desktop died twice during the run; restarted, postgres healthy.
