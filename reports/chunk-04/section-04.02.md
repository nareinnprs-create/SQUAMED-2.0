# Section 04.02 — eMAR + nursing core — gate-run report

Static read-only assessment. Baseline commit `886c51c`, CI run `43548016617` (all 6 CI gates green; merged instruction coverage 80.13%). No code, ledger, chunk file, or `.gate/` content changed by this report.

## 1. Scope
Chunk file §04.02: MAR schedule **from dispensed meds (chunk 2)**, barcode patient+drug administration (mismatch = hard block), PRN with reason, omission codes + prescriber notification, high-alert double-check, nursing notes, per-shift task lists. Present at baseline: manual MAR scheduling + administration, notes, handovers, vitals, risk assessments. **Barcode block, pharmacy link, PRN/omission, high-alert check, shift tasks absent.**

## 2. Built
- `backend/squamed-core/src/main/kotlin/com/squamed/core/nursing/` — nursing service/controller/domain (MAR, notes, handovers, vitals, risk assessments).
- `V30__nursing.sql` — 7 nursing tables.

## 3. Data/API/event contracts delivered
- Tables: MAR + administration records, nursing notes, handover, vitals, risk-assessment (7 tables, V30).
- API: MAR scheduled → administered → invalid (invalid-after-administration = 400); notes; handovers; vitals; risk assessments; role-less requests = 403.
- Events: no prescriber notification event (omission path absent).

## 4. Gates 1–12
| # | Gate | Result | Evidence |
|---|---|---|---|
| 1 | Functional completeness | **FAIL (PARTIAL)** | Present: MAR lifecycle scheduled→administered→invalid-400, notes, handovers, vitals, falls risk assessments. Missing: MAR-from-dispensed-med link, barcode patient+drug hard block, PRN-with-reason, omission codes + prescriber notification, high-alert double-check, per-shift task lists. |
| 2 | Zero-stub scan | PASS | CI run 43548016617 (gate 2 green); SC-9 allowlist. |
| 3 | Types | PASS | CI run 43548016617 (gate 3 green). |
| 4 | Lint + format | PASS | CI run 43548016617 (gate 4 green). |
| 5 | Tests | PASS | CI run 43548016617 (gate 5 green); merged instruction coverage 80.13% ≥ 0.80. |
| 6 | Accessibility | PASS | CI run 43548016617 (gate 6 green). Backend section; no nursing page at baseline. |
| 7 | Design system | PASS | CI run 43548016617 (gate 7 green). |
| 8 | Contracts | **FAIL** | Schema-registry harness not yet wired (00.02 scope). |
| 9 | Data | **FAIL** | RLS cross-tenant suite ABSENT until 00.04 (ADR-13) — tenant-scoped nursing tables. |
| 10 | Audit | PASS | MAR/notes/handover writes are controller-mediated; `AuditAspect.kt:22-23` pointcut covers them. |
| 11 | Docs | PASS | This report re-checks chunk file §04.02; ledger untouched; gate-parity green. |
| 12 | Exit item | PASS | Named PIT slice `nursingDocumentationCoverage` green in run 43548016617. |

## 5. Tests
- PIT: `nursingDocumentationCoverage` — assessments, careplan status transitions, notes, handovers, vitals, MAR scheduled→administered→invalid-400, risk-assessments (falls), 403 role-less. Green in run 43548016617.
- Unit: none dedicated.
- Coverage: merged instruction 80.13%.

## 6. Accessibility
axe green in CI; no nursing-specific page exists to scan.

## 7. Issues
- Barcode mismatch does **not** hard-block administration (CS-06 patient-safety gap) — **open**.
- MAR schedule is hand-created; not derived from chunk-2 dispensed meds — open (cross-chunk link missing).
- No omission code → no prescriber notification path — open.

## 8. External blockers
None.

## 9. Deviations from chunk file
None logged; chunk file not edited.

## 10. Exit-demo slice
`gradlew.bat :squamed-core:integrationTest --tests "*PlatformIntegrationTests*nursingDocumentationCoverage*"` — last run green in CI run 43548016617.
