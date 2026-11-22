# Section 03.07 — Chunk exit (chunk 3)

**Gate-run assessment of the CURRENT tree** @ PR #89 (all chunk-3 buildable sections merged: #82, #84, #86, #87, #88). Verdict: **SECTION REVIEW** (01.12/02.09 precedent — never COMPLETE). CI evidence: **run PENDING — all 7 gates green**. Exit demo live: **28/28**, transcript `reports/demo-03.07-live.log`.

## 1. Scope
Per the chunk-3 contract: demo scenario, tax correctness table, claim adapter golden files, integrity rules, money math, billing parallel-run prep. Zero feature code by design.

## 2. Built
- `scripts/demo-03.07-chunk3-exit.ps1` — 28-check live walk of the EXIT CRITERIA (minus the insurance/claim legs owed to 03.04).
- `docs/runbooks/billing-parallel-run.md` — rate-card/tax load, 2-week daily loop, exit criteria, rollback, sign-off.
- `reports/chunk-03/FINAL.md` + this report + LEDGER row 03.07 + tracker row.

## 3. Demo evidence (live, 2026-11-22)
28/28 — lab release auto-captured (`EVENT`, tariff 80, exempt 9993), pharmacy 5% GST, TPA repricing (196 subtotal / 5 tax / 201 total, two tax lines), deposit + cash settlement, cash-up variance 0, statement MATCHED, coding queue + ICD assignment, integrity finding + supervisor resolution, supervisor refund (staff 403), journal export balanced.

Script bugs fixed during the run: verify/release need the pathologist role; collect needs the lab technician.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS (chunk) | Section reports + named PITs; demo 28/28 |
| 2 Zero-stub | PASS | CI run PENDING |
| 3 Types | PASS | CI run PENDING |
| 4 Lint + format | PASS | CI run PENDING |
| 5 Tests | PASS | CI run PENDING; chunk test table in FINAL §5 |
| 6 Accessibility | PASS | CI run PENDING |
| 7 Design system | PASS | CI run PENDING |
| 8 Contracts | PASS | OpenAPI snapshot current (cc91af05…, no API change in this PR) |
| 9 Data | PASS | No migrations in this PR |
| 10 Audit | PASS | Demo exercises discounts, refunds, cash-up, triage, coding trails |
| 11 Docs | PASS | FINAL + this report + runbook + LEDGER + tracker |
| 12 Exit item | PASS (partial) | Demo + named tests green; 03.04 + parallel run + sign-off owed per FINAL §7 |

## 5. Owed (not skipped)
03.04 Insurance/TPA v1 (D6 payer list), payment gateway E4, 2-week billing parallel run, rate-card/tax reconciliation, go-live sign-off (B2). Tracked in FINAL §7 and the runbook.

## 6. Session trail
- Demo round 1: 21/28 — lab verify/release 403 for the doctor (pathologist role required), collect role; fixed.
- Demo round 2: **28/28** live on merged main code.
- Docker Desktop died twice; restarted, postgres healthy.
