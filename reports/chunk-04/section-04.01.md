# Section 04.01 — Admission + bed management — gate-run report

Static read-only assessment. Baseline commit `886c51c`, CI run `43548016617` (all 6 CI gates green; merged instruction coverage 80.13%). No code, ledger, chunk file, or `.gate/` content changed by this report.

## 1. Scope
Chunk file (`17-CHUNK4-SECTIONS.md` §04.01): admission requests (ER/OPD/scheduled), allocation rules (class/isolation/gender-mix), transfers with trace, expected-discharge planning, live bed board, exclusion constraint on allocations. Present at baseline: admission lifecycle with transfers, attendants, discharge summaries. **Allocation rules, discharge planning, bed board, DB constraint absent.**

## 2. Built
- `backend/squamed-core/src/main/kotlin/com/squamed/core/ipd/service/AdmissionService.kt` — 15 functions incl. private `allocateBed` (in-process pick, no rule engine).
- IPD REST endpoints (controller-layer; PIT exercises over HTTP incl. 403 role checks).
- `V29__ipd.sql` — `ipd.admission`, `admission_transfer`, `discharge_summary`, `attendant`.

## 3. Data/API/event contracts delivered
- Tables: admission / admission_transfer / discharge_summary / attendant.
- API: admit, transfer (+history), discharge, readmit, attendants, discharge-summary endpoints; validation 400s (dup discharge, summary-before-discharge).
- Events: none (no bed-state event stream).

## 4. Gates 1–12
| # | Gate | Result | Evidence |
|---|---|---|---|
| 1 | Functional completeness | **FAIL (PARTIAL)** | Present: admit/transfer-with-history/discharge/dup-discharge-400/readmit/attendants. Missing: allocation rules (class/isolation/gender-mix), expected-discharge planning, live bed board, DB exclusion constraint on double allocation. |
| 2 | Zero-stub scan | PASS | CI run 43548016617 (gate 2 green); SC-9 allowlist. |
| 3 | Types | PASS | CI run 43548016617 (gate 3 green). |
| 4 | Lint + format | PASS | CI run 43548016617 (gate 4 green). |
| 5 | Tests | PASS | CI run 43548016617 (gate 5 green); merged instruction coverage 80.13% ≥ 0.80. |
| 6 | Accessibility | PASS | CI run 43548016617 (gate 6 green). No dedicated IPD page at baseline (backend section); bed board UI itself is missing named content. |
| 7 | Design system | PASS | CI run 43548016617 (gate 7 green). |
| 8 | Contracts | **FAIL** | Schema-registry harness not yet wired (00.02 scope). |
| 9 | Data | **FAIL** | RLS cross-tenant suite ABSENT until 00.04 (ADR-13) — tenant-scoped IPD tables. |
| 10 | Audit | PASS | All admission writes enter via `*Controller` methods, intercepted by `AuditAspect.kt:22-23`. |
| 11 | Docs | PASS | This report re-checks chunk file §04.01; ledger untouched; gate-parity green. |
| 12 | Exit item | PASS | Named PIT slice `inpatientAdmissionLifecycleCoverage` green in run 43548016617. |

## 5. Tests
- PIT (Phase2InpatientTests): `inpatientAdmissionLifecycleCoverage` — validation 400s, admit, transfer history, discharge, dup-discharge 400, readmit, attendants, discharge-summary 400-before-discharge. Green in run 43548016617.
- Unit: none dedicated.
- Coverage: merged instruction 80.13%.

## 6. Accessibility
axe green in CI; no IPD-specific page exists to scan.

## 7. Issues
- `allocateBed` picks in-process; concurrent double-allocation unguarded at DB level (no exclusion constraint) — **open** (root: constraint not in V29).
- No admission-request source typing (ER/OPD/scheduled) — open.
- Bed-state is read-from-rows; no live board — open.

## 8. External blockers
None.

## 9. Deviations from chunk file
None logged; chunk file not edited.

## 10. Exit-demo slice
`gradlew.bat :squamed-core:integrationTest --tests "*PlatformIntegrationTests*inpatientAdmissionLifecycleCoverage*"` — last run green in CI run 43548016617.
