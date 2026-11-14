# Section 04.03 — Assessments + care plans + risk scores — gate-run report

Static read-only assessment. Baseline commit `886c51c`, CI run `43548016617` (all 6 CI gates green; merged instruction coverage 80.13%). No code, ledger, chunk file, or `.gate/` content changed by this report.

## 1. Scope
Chunk file §04.03: configurable assessment forms, problem→intervention→goal plan templates, falls/pressure scores, discharge-checklist start. Present at baseline: fixed-structure assessments, care plans with status transitions, falls risk assessment. **Form configurability, pressure score, discharge checklist absent.**

## 2. Built
- `backend/squamed-core/src/main/kotlin/com/squamed/core/nursing/` — assessment + care plan + risk-assessment logic inside nursing module.
- `V30__nursing.sql` — assessment/careplan/risk tables (within the 7 nursing tables).

## 3. Data/API/event contracts delivered
- Tables: assessments, care plans (status transitions), risk assessments.
- API: assessment CRUD, careplan status transitions, falls risk-assessment endpoints (403 role-less).
- Events: none.

## 4. Gates 1–12
| # | Gate | Result | Evidence |
|---|---|---|---|
| 1 | Functional completeness | **FAIL (PARTIAL)** | Present: assessments, care plans with status transitions, falls risk scores. Missing: configurable form templates, pressure (score) risk assessment, discharge-checklist start. |
| 2 | Zero-stub scan | PASS | CI run 43548016617 (gate 2 green); SC-9 allowlist. |
| 3 | Types | PASS | CI run 43548016617 (gate 3 green). |
| 4 | Lint + format | PASS | CI run 43548016617 (gate 4 green). |
| 5 | Tests | PASS | CI run 43548016617 (gate 5 green); merged instruction coverage 80.13% ≥ 0.80. |
| 6 | Accessibility | PASS | CI run 43548016617 (gate 6 green). Backend section; no dedicated page. |
| 7 | Design system | PASS | CI run 43548016617 (gate 7 green). |
| 8 | Contracts | **FAIL** | Schema-registry harness not yet wired (00.02 scope). |
| 9 | Data | **FAIL** | RLS cross-tenant suite ABSENT until 00.04 (ADR-13) — tenant-scoped nursing tables. |
| 10 | Audit | PASS | Assessment/careplan writes controller-mediated; `AuditAspect.kt:22-23` covers them. |
| 11 | Docs | PASS | This report re-checks chunk file §04.03; ledger untouched; gate-parity green. |
| 12 | Exit item | PASS | Named PIT slice `nursingDocumentationCoverage` (assessments + careplan transitions + falls) green in run 43548016617. |

## 5. Tests
- PIT: `nursingDocumentationCoverage` (assessment CRUD, careplan status transitions, falls risk assessments). Green in run 43548016617.
- Unit: none dedicated.
- Coverage: merged instruction 80.13%.

## 6. Accessibility
axe green in CI; no dedicated page.

## 7. Issues
- Form structure is hard-coded; no template/config model → "configurable" claim unmet — **open**.
- Pressure score not implemented (only falls) — open.
- No discharge-checklist anchor for 04.08 chain — open.

## 8. External blockers
None.

## 9. Deviations from chunk file
None logged; chunk file not edited.

## 10. Exit-demo slice
`gradlew.bat :squamed-core:integrationTest --tests "*PlatformIntegrationTests*nursingDocumentationCoverage*"` — last run green in CI run 43548016617.
