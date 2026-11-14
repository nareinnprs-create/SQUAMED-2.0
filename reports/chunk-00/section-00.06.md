# Section 00.06 — Master data

## 1. Scope
Service catalog, price lists, vendor registry, equipment registry, employee master (demographic + credential fields).

## 2. Built (current tree)
- `masterdata/domain/{Drug,LabTest,ImagingProcedure,DiagnosisCode,ProcedureCode,ChargeItem}.kt` — 6 catalogs.
- `masterdata/repository/*` (6 repositories) — persistence.
- `masterdata/api/MasterDataController.kt` + `MasterDataDtos.kt` — filter + pagination APIs.
- Migration `V6__master_data.sql` (+ permission seed V8).
- Web `apps/web/src/app/(app)/master-data/` — REBUILT by PR #8: real `apiFetch` wiring (`page.tsx` → `workspace.tsx` catalog browse/create), plus `master-data.test.tsx`. Former `PlaceholderPage` stub eliminated.

## 3. Contracts delivered
- Catalog filter/search + active + pagination APIs for 6 catalogs (PIT-asserted).
- NOT delivered: vendor registry, equipment registry, employee master, price lists (charge/tariff prices live in billing only).

## 4. Gates
1. FAIL — named contents missing: vendor registry, equipment registry, employee master, price lists.
2. PASS — CI run 43548016617 (gate 2 green) — former 3 real stubs (incl. master-data page) eliminated by PR #8.
3. PASS — CI run 43548016617 (gate 3 green).
4. PASS — CI run 43548016617 (gate 4 green).
5. PASS — CI run 43548016617 (gate 5 green); named tests below.
6. PASS — CI run 43548016617 (gate 6 green) — master-data page now real and in axe scope.
7. PASS — CI run 43548016617 (gate 7 green).
8. FAIL — schema-registry harness not yet wired (00.02 scope).
9. FAIL — `V6__master_data.sql` exists but carries no hospital_id and no RLS; cross-tenant denial suite ABSENT until 00.04 lands (ADR-13).
10. PASS — AuditAspect intercepts MasterDataController writes; audit rows asserted PIT-side.
11. PASS — this report; PR #8 wiring closes the baseline UI gap.
12. PASS — PIT `catalogFilterAndPaginationCoverage` covers the catalog browse journey.

## 5. Tests
PIT: `catalogFilterAndPaginationCoverage`, `catalogValidationBranchesCoverage`, `catalogsAndHospitalStructureCoverage` (green, run 43548016617). Web: `master-data.test.tsx` (PR #8, frontend-gates green).

## 6. Accessibility
master-data page in axe gate scope; run 43548016617 gate 6 green (zero critical/serious).

## 7. Issues found during build
Baseline section — see BASELINE-AUDIT.md §2.1 masterdata row and §3.1 stub #1. Verified current tree: master-data page is real apiFetch wiring with tests (PR #8) — baseline stub verdict corrected; backend catalogs unchanged.

## 8. External blockers
None.

## 9. Deviations from chunk file
Zero; missing registries are unbuilt, not re-scoped.

## 10. Exit-demo slice
PIT `catalogFilterAndPaginationCoverage` (search=Para + active filter + pagination across 6 catalogs) — green in run 43548016617.
