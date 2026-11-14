# Section 04.08 — IPD billing + dietary + discharge — gate-run report

Static read-only assessment. Baseline commit `886c51c`, CI run `43548016617` (all 6 CI gates green; merged instruction coverage 80.13%). No code, ledger, chunk file, or `.gate/` content changed by this report.

## 1. Scope
Chunk file §04.08 (CS-11): bed-hour room charges, nursing procedure charges, surgical packages, interim bills; diet orders + kitchen manifest (allergy-aware); discharge checklist → coded signed summary (med-rec gate) → follow-up → final bill → settlement → bed release → housekeeping task. Depends on 04.02, 03. Present at baseline: discharge + plain-TEXT discharge summary + bed release primitive. **Charges, interim bills, dietary, coded/med-rec-gated summary, post-discharge chain absent.**

## 2. Built
- `backend/squamed-core/src/main/kotlin/com/squamed/core/ipd/service/AdmissionService.kt` — discharge, discharge-summary, `dischargeBed()` bed-release function.
- `V29__ipd.sql` — `discharge_summary` (TEXT content; no coded diagnosis/med-rec gate columns).

## 3. Data/API/event contracts delivered
- Tables: admission/discharge_summary (TEXT), attendant.
- API: discharge endpoint (dup-discharge 400), discharge-summary endpoint (400 before discharge), bed release via `dischargeBed()`.
- Events: no housekeeping task, no follow-up/final-bill events.

## 4. Gates 1–12
| # | Gate | Result | Evidence |
|---|---|---|---|
| 1 | Functional completeness | **FAIL (PARTIAL)** | Present: discharge + dup-discharge-400, discharge summary (400-before-discharge), bed release. Missing: bed-hour room charges, nursing procedure charges, surgical packages, interim bills, diet orders + kitchen manifest, coded signed summary with med-rec gate (content is plain TEXT), follow-up → final bill → settlement → housekeeping chain. |
| 2 | Zero-stub scan | PASS | CI run 43548016617 (gate 2 green); SC-9 allowlist. |
| 3 | Types | PASS | CI run 43548016617 (gate 3 green). |
| 4 | Lint + format | PASS | CI run 43548016617 (gate 4 green). |
| 5 | Tests | PASS | CI run 43548016617 (gate 5 green); merged instruction coverage 80.13% ≥ 0.80. |
| 6 | Accessibility | PASS | CI run 43548016617 (gate 6 green). Backend section; no dedicated page. |
| 7 | Design system | PASS | CI run 43548016617 (gate 7 green). |
| 8 | Contracts | **FAIL** | Schema-registry harness not yet wired (00.02 scope). |
| 9 | Data | **FAIL** | RLS cross-tenant suite ABSENT until 00.04 (ADR-13) — tenant-scoped IPD tables. |
| 10 | Audit | PASS | Discharge/summary writes controller-mediated; `AuditAspect.kt:22-23` covers them. |
| 11 | Docs | PASS | This report re-checks chunk file §04.08; ledger untouched; gate-parity green. |
| 12 | Exit item | PASS | Named PIT slice `inpatientAdmissionLifecycleCoverage` discharge leg green in run 43548016617. |

## 5. Tests
- PIT: `inpatientAdmissionLifecycleCoverage` — discharge, dup-discharge 400, discharge-summary 400-before-discharge. Green in run 43548016617.
- Unit: none dedicated.
- Coverage: merged instruction 80.13%.

## 6. Accessibility
axe green in CI; no dedicated page.

## 7. Issues
- Signed summary is free TEXT — no coding, no med-rec gate (CS-11 core requirement unmet) — **open**.
- No IPD charge generation at all (depends on unbuilt 03.02 engine) — open.
- Post-discharge chain stops at bed release — open.

## 8. External blockers
None vendor-gated; blocked internally by 03.02 (billing engine) and 04.02 (nursing charges).

## 9. Deviations from chunk file
None logged; chunk file not edited.

## 10. Exit-demo slice
`gradlew.bat :squamed-core:integrationTest --tests "*PlatformIntegrationTests*inpatientAdmissionLifecycleCoverage*"` — last run green in CI run 43548016617.
