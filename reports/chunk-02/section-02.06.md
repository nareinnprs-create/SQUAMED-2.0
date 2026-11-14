# Section 02.06 — Pharmacy stock + dispensing (chunk 2)

**Gate-run assessment of the CURRENT tree** @ 886c51c (merged baseline + wiring PRs #2–#11). Static read-only; no builds/tests run. Verdict after verification: **PARTIAL** → **IN PROGRESS** (named contents missing). CI evidence: run 43548016617 — all 6 gates green.

## 1. Scope
Per `15-CHUNK2-SECTIONS.md`: drug master activation, formulary, indents, dispensing (partial, substitution audited), batch/expiry, FEFO (BLOCK near-expiry), stock ledger (trigger-enforced balance), min-level alerts, controlled register, returns/wastage. Depends on chunk 1 exit. Parallel with 02.01.

## 2. Built (current tree)
- `backend/squamed-core/src/main/kotlin/com/squamed/core/pharmacy/domain/PharmacyStock.kt` — stock entity: hospital_id, drugId/drugCode/drugName, batchNumber, expiryDate, quantityOnHand, reorderLevel (default 10), unitPrice, location, isActive.
- `backend/.../pharmacy/domain/Dispensation.kt` — dispensation entity: hospital_id, prescriptionId, patientId, encounterId, drugId/drugName, quantity, status (pending/dispensed/verified/returned/cancelled), dispensedBy/At, verifiedBy/At, unitPrice, totalPrice, notes.
- `backend/.../pharmacy/service/PharmacyService.kt` — addStock (batch/expiry/reorder-level fields; merges quantity into existing same drug+batch row), listStock (search/active-only), updateStock, dispense (stock decrement with `INSUFFICIENT_STOCK` guard when stockId/drugId supplied; dispensation record with unit/total price; `prescription.dispensed` event), verifyDispensation (dispensed→verified), returnOrCancel (pending→cancelled no restock; dispensed/verified→returned **with restock** back to stock), listDispensations (patient/status filters).
- `backend/.../pharmacy/api/PharmacyController.kt` + DTOs/views — REST surface with `@RequiresPermission` (read:stock, write:stock, dispense:prescription).
- `backend/squamed-core/src/main/resources/db/migration/V15__pharmacy.sql` — `pharmacy.pharmacy_stock`, `pharmacy.dispensation`.
- `apps/web/src/app/(app)/pharmacy/page.tsx` (24.2 KB) — stock + dispensing UI with batch/expiry/reorder-level surface (real page).

## 3. Data/API/event contracts delivered
- Schema: `V15__pharmacy.sql` → `pharmacy.pharmacy_stock`, `pharmacy.dispensation`.
- API: GET/POST `/api/v1/pharmacy/stock`, PUT `/stock/{id}`, GET/POST `/api/v1/pharmacy/dispensations`, POST `/dispensations/{id}/verify|return`.
- Event: `prescription.dispensed` on dispense (direct Kafka).
- Permissions seeded: `V17__seed_lab_pharmacy_billing_perms.sql`.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | **FAIL** | Stock intake (batch/expiry/reorder), dispense with stock guard, verify, return-with-restock all work (PIT walk). Missing named contents: **drug master activation** (drugId optional/client-supplied; no drug-master linkage), **formulary**, **indents**, **substitution audited** (no substitution concept), **FEFO/BLOCK near-expiry** (dispense picks `firstOrNull` stock row by drugId — ignores batch/expiry order and expiry blocking), **stock ledger trigger-enforced** (quantity mutated in place; no ledger table/trigger), **min-level alerts** (reorderLevel stored, never evaluated), **controlled register** (none), **wastage** (returns only). Also: `addStock` hardcodes default hospitalId `…0001`; return-restock targets first stock row for drugId, not the batch dispensed. Gap list: FEFO · formulary/indents · ledger trigger · min-level alerts · controlled register |
| 2 Zero-stub | PASS | CI run 43548016617 (gate 2 green) |
| 3 Types | PASS | CI run 43548016617 |
| 4 Lint + format | PASS | CI run 43548016617 |
| 5 Tests | PASS | CI run 43548016617; named tests below; merged instruction coverage 80.13% ≥ 0.80. Caveat: **CS-04 (high-alert medication, bound to 02.06) has no test** |
| 6 Accessibility | PASS | CI run 43548016617 (axe 0 serious/critical). Caveat: `/pharmacy` NOT in SCAN_ROUTES — not axe-scanned |
| 7 Design system | PASS | CI run 43548016617 (visual regression green) |
| 8 Contracts | **FAIL** | schema-registry harness not yet wired (00.02 scope); OpenAPI surfaces `pharmacy` (PIT `openApiExposesAllModuleSurfaces`); `prescription.dispensed` event has no committed schema |
| 9 Data | **FAIL** | Migration `V15__pharmacy.sql` applied; **no rollback note**; **no RLS/cross-tenant denial suite** (00.04/ADR-13 pending); `hospital_id` nullable + client-supplied (addStock default `…0001`) |
| 10 Audit | PASS | `AuditAspect` intercepts PharmacyController writes (addStock, updateStock, dispense, verifyDispensation, returnOrCancel) → audit rows; pharmacy legs asserted in PIT `fullPatientRevenueJourney` |
| 11 Docs | PASS | This report; chunk file lists contents accurately; ledger row stays PLANNED |
| 12 Exit item | **FAIL** | no scripted slice yet (FEFO test is 02.09 exit scope) |

## 5. Tests
Named PIT tests (green in run 43548016617): `clinicalEncounterAndPharmacyCoverage` (stock add/update, dispense, verify, return-with-restock), `fullPatientRevenueJourney` (pharmacy stock → dispensation → verify leg), `openApiExposesAllModuleSurfaces` (pharmacy surface). Pass counts: PIT 37/37. Coverage: merged instruction 80.13% (branch 52%).

## 6. Accessibility
Repo-wide axe gate green (run 43548016617); `/pharmacy` outside scanned routes — no per-page axe evidence.

## 7. Issues found during build + fixes
Baseline section — see BASELINE-AUDIT.md §2.1 (pharmacy row: PARTIAL — stock/dispense only). Verified in current tree: unchanged from baseline; no FEFO, no formulary, no controlled register, no alerts added since.

## 8. External blockers
None for this section's code (drug master/formulary data depends on 00.03 master-data setup; controlled-drug register needs regulatory list input).

## 9. Deviations from chunk file
Zero (SC-6 baseline adoption).

## 10. Exit-demo slice
`pwsh scripts/phase1-e2e.ps1` — walks `/api/v1/pharmacy` stock + dispensation endpoints. Nearest scripted evidence = PIT `clinicalEncounterAndPharmacyCoverage` (run 43548016617).
