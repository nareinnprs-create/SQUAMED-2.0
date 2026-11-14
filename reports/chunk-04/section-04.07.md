# Section 04.07 — Blood bank — gate-run report

Static read-only assessment. Baseline commit `886c51c`, CI run `43548016617` (all 6 CI gates green; merged instruction coverage 80.13%). No code, ledger, chunk file, or `.gate/` content changed by this report.

## 1. Scope
Chunk file §04.07: donor registry + deferral, screening, component inventory (expiry), crossmatch→reservation→issue with **bedside wristband check (BLOCK on mismatch)**, reaction reporting, donor→recipient traceability report (CS-10, event-sourced trace proof). Present at baseline: full donor→unit→crossmatch→reserve→issue→transfusion→reaction chain with validations. **Wristband BLOCK and traceability report absent.**

## 2. Built
- `backend/squamed-core/src/main/kotlin/com/squamed/core/bloodbank/` — blood bank service/controller/domain.
- `V39__bloodbank.sql` — 8 blood bank tables (donors, units, grouping, crossmatch, reservations, issues, transfusions, reactions).

## 3. Data/API/event contracts delivered
- Tables: 8 tables per V39 (donor registry incl. deferral, component units w/ expiry, ABO grouping, crossmatch, reservation, issue, transfusion, reaction).
- API: donor/defer; unit expiry-400; ABO grouping confirm + duplicate-400; crossmatch (technique/compatibility); reservation/release + dup-400; issue/reissue-400; transfusion requires prior issue (400 otherwise); reactions w/ severity-400; 403 role-less.
- Events: **no event-sourced trace stream** (CS-10 trace proof unmet).

## 4. Gates 1–12
| # | Gate | Result | Evidence |
|---|---|---|---|
| 1 | Functional completeness | **FAIL (PARTIAL)** | Present: donor registry+deferral, screening, component inventory with expiry-400, crossmatch→reservation→issue→transfusion→reaction chain with full 400-validation set. Missing: bedside wristband BLOCK check on mismatch, donor→recipient traceability report (event-sourced proof). |
| 2 | Zero-stub scan | PASS | CI run 43548016617 (gate 2 green); SC-9 allowlist. |
| 3 | Types | PASS | CI run 43548016617 (gate 3 green). |
| 4 | Lint + format | PASS | CI run 43548016617 (gate 4 green). |
| 5 | Tests | PASS | CI run 43548016617 (gate 5 green); merged instruction coverage 80.13% ≥ 0.80. |
| 6 | Accessibility | PASS | CI run 43548016617 (gate 6 green). Backend section; no blood-bank page at baseline. |
| 7 | Design system | PASS | CI run 43548016617 (gate 7 green). |
| 8 | Contracts | **FAIL** | Schema-registry harness not yet wired (00.02 scope). |
| 9 | Data | **FAIL** | RLS cross-tenant suite ABSENT until 00.04 (ADR-13) — tenant-scoped blood-bank tables. |
| 10 | Audit | PASS | All blood-bank writes controller-mediated; `AuditAspect.kt:22-23` covers them. |
| 11 | Docs | PASS | This report re-checks chunk file §04.07; ledger untouched; gate-parity green. |
| 12 | Exit item | PASS | Named PIT slice `bloodBankWorkflowCoverage` green in run 43548016617. |

## 5. Tests
- PIT: `bloodBankWorkflowCoverage` — donor/defer, unit expiry-400, ABO confirm/dup-400, crossmatch technique/compatibility, reservation/release/dup-400, issue/reissue-400, transfusion-requires-issue-400, reactions severity-400, 403s. Green in run 43548016617.
- Unit: none dedicated.
- Coverage: merged instruction 80.13%.

## 6. Accessibility
axe green in CI; no blood-bank-specific page exists to scan.

## 7. Issues
- Wristband mismatch does not BLOCK transfusion at bedside (CS-10 patient-safety gap) — **open**.
- No donor→recipient traceability report / event-sourced proof — open (chain rows exist but no derived report or event stream).

## 8. External blockers
None.

## 9. Deviations from chunk file
None logged; chunk file not edited.

## 10. Exit-demo slice
`gradlew.bat :squamed-core:integrationTest --tests "*PlatformIntegrationTests*bloodBankWorkflowCoverage*"` — last run green in CI run 43548016617.
