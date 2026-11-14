# Section 02.02 — Lab workflow core (chunk 2)

**Gate-run assessment of the CURRENT tree** @ 886c51c (merged baseline + wiring PRs #2–#11). Static read-only; no builds/tests run. Verdict after verification: **PARTIAL** → **IN PROGRESS** (named contents missing). CI evidence: run 43548016617 — all 6 gates green.

## 1. Scope
Per `15-CHUNK2-SECTIONS.md`: order acceptance → phlebotomy worklist → barcode labeling → accessioning (chain-of-custody) → result entry (manual+instrument) → delta checks → pathologist validation; reject-and-recollect (never edit-in-place). Depends on chunk 1 exit.

## 2. Built (current tree)
- `backend/squamed-core/src/main/kotlin/com/squamed/core/laboratory/domain/LabOrder.kt` — order entity: patient/encounter, order_number (`LO-YYYY-####`), priority, status (ordered/collected/received/processing/resulted/rejected), result_status (pending/verified/released), clinical note, ordered/collected/received/started/verified/released timestamps + by-ids, rejected_reason.
- `backend/.../laboratory/domain/LabTestRequest.kt` — per-test line: test code/name, status, result value/unit/reference range, isAbnormal, result-entered by/at.
- `backend/.../laboratory/service/LabService.kt` — createOrder (multi-test), listOrders (filter patient/status/resultStatus), collectSpecimen (ordered→collected, collector id), receiveSpecimen (→received), startProcessing (→processing), enterResults (only in processing; writes value/unit/range, computes **isAbnormal from reference range** `lo-hi` numeric parse), verifyOrder (resulted→resultStatus verified), releaseOrder (verified→released + `lab.result.released` event), rejectOrder (any→rejected with reason). Status guards throughout (`LAB_ORDER_WRONG_STATUS`).
- `backend/.../laboratory/api/LabController.kt` + `LabDtos.kt` — REST surface for the full lifecycle.
- `apps/web/src/app/(app)/laboratory/page.tsx` (21.9 KB) — order lifecycle + result entry UI (real page).

## 3. Data/API/event contracts delivered
- Schema: `V14__laboratory.sql` → `laboratory.lab_order`, `laboratory.lab_test_request`.
- API: POST/GET `/api/v1/lab/orders`, GET `/{id}`, POST `/{id}/collect|receive|start|results|verify|release|reject`.
- Event: `lab.result.released` on release (direct Kafka).
- Permissions seeded: `V17__seed_lab_pharmacy_billing_perms.sql`.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | **FAIL** | Order → collect → receive → process → enter results (+abnormal flagging) → verify → release → reject works end-to-end (PIT full lifecycle walk). Missing named contents: **phlebotomy worklist** (list endpoint only; no phlebotomy queue view), **barcode labeling** (no barcode/accessioning ids), **accessioning chain-of-custody** (no custody records), **delta checks** (no prior-result comparison), **instrument result entry** (manual DTO only), **pathologist validation role separation** (verify takes any authorized id; no role gate beyond permission), **reject-and-recollect** (reject exists; no recollect flow creating a successor order). Gap list: barcode/accessioning · delta checks · worklist · recollect flow · role-gated validation |
| 2 Zero-stub | PASS | CI run 43548016617 (gate 2 green); LabService `toDoubleOrNull` hits are SC-9-allowlisted false positives |
| 3 Types | PASS | CI run 43548016617 |
| 4 Lint + format | PASS | CI run 43548016617 |
| 5 Tests | PASS | CI run 43548016617; named tests below; merged instruction coverage 80.13% ≥ 0.80 |
| 6 Accessibility | PASS | CI run 43548016617 (axe 0 serious/critical). Caveat: `/laboratory` NOT in SCAN_ROUTES — not axe-scanned |
| 7 Design system | PASS | CI run 43548016617 (visual regression green) |
| 8 Contracts | **FAIL** | schema-registry harness not yet wired (00.02 scope); OpenAPI surfaces `lab` (PIT `openApiExposesAllModuleSurfaces`); `lab.result.released` event has no committed schema |
| 9 Data | **FAIL** | Migration `V14__laboratory.sql` applied; **no rollback note**; **no RLS/cross-tenant denial suite** (00.04/ADR-13 pending); lab_order has no hospital_id tenant scoping |
| 10 Audit | PASS | `AuditAspect` intercepts LabController writes (order create, collect, receive, start, results, verify, release, reject) → audit rows; audit read asserted in PIT `collaborationAndWorkflowJourney`. Caveat: plain audit table (ADR-9 pending) |
| 11 Docs | PASS | This report; chunk file lists contents accurately; ledger row stays PLANNED |
| 12 Exit item | **FAIL** | no scripted slice yet (lab parallel-run prep is 02.09 exit scope) |

## 5. Tests
Named PIT tests (green in run 43548016617): `fullPatientRevenueJourney` (lab order create → collect → receive → start → results → verify → release full walk), `clinicalEncounterAndPharmacyCoverage` (lab order create from encounter), `openApiExposesAllModuleSurfaces` (lab surface). Pass counts: PIT 37/37. Coverage: merged instruction 80.13% (branch 52%).

## 6. Accessibility
Repo-wide axe gate green (run 43548016617); `/laboratory` outside scanned routes — no per-page axe evidence.

## 7. Issues found during build + fixes
Baseline section — see BASELINE-AUDIT.md §2.1 (laboratory row: PARTIAL — no barcode/accessioning, no delta checks, no QC/reports). Verified in current tree: unchanged from baseline; abnormal-flag computation from reference ranges is the one enrichment over the audit's one-liner.

## 8. External blockers
None for this section's code (analyzer-driven result entry depends on 02.01 intake, a sibling build dependency).

## 9. Deviations from chunk file
Zero (SC-6 baseline adoption).

## 10. Exit-demo slice
`pwsh scripts/phase1-e2e.ps1` — walks `/api/v1/lab/orders` full lifecycle. Nearest scripted evidence = PIT `fullPatientRevenueJourney` lab leg (run 43548016617).
