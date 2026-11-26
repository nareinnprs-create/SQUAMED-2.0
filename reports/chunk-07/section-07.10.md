# Section 07.10 — Specialty packs + migration studio + benchmarking/ESG (chunk 7)

**Verdict: SECTION REVIEW → local gates green; EXTERNAL-BLOCKED item remains (22 specialty packs need human clinical review).** Migration studio, consent-gated benchmarking and ESG reporting are built; the specialty-pack library is not.

## 1. Scope
Per `chunk-07-full-depth.md` item 8/11/12 / `20-CHUNK7-SECTIONS.md` 07.10: 22 specialty packs install config-only in <1 day; migration studio (extract→map→clean→transform→validate→import→reconcile, rollback, legacy dry-run); anonymized consent-gated benchmarking; ESG reporting.

## 2. Key artifacts
**Migration `V104`**: `migration.job`, `migration.staged_row`, `benchmark.participation`, `esg.metric`; `migration:read|write`, `benchmark:read|write`, `esg:read|write`.

**Backend**
- `migration/domain/MigrationEntities.kt`, `repository/MigrationRepositories.kt`.
- `service/MigrationService.kt` + `MigrationRowImporter` (per-row `REQUIRES_NEW`), `BenchmarkService.kt`, `EsgService.kt`.
- `api/MigrationControllers.kt` (11 endpoints under `/api/v1/migration`, `/api/v1/benchmark`, `/api/v1/esg`).
- `HospitalRepository.findByGroupId` for peer lookup.

## 3. Gap closure
- ✔ Migration studio: source vocabulary (HIS/EMR/LIS/ERP/EXCEL), row-level validation with error messages, staged rows, import of valid patient rows, reconciliation counts, import-once, soft rollback (imported records hidden as duplicates).
- ✔ Per-row transaction isolation so one bad row cannot poison the batch.
- ✔ Benchmarking: participation consent, group-scoped peers, anonymized own/median/p25/p75 for ADMISSIONS and REVENUE_QAR; non-participants blocked; opted-out peers excluded.
- ✔ ESG: ENERGY_KWH/WATER_L/WASTE_KG/CARBON_KG per period, totals, carbon per admission.
- ⚠ Specialty-pack library (22 packs) and the config-only installer are not built — content requires human clinical review.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS (partial) | `migrationBenchmarkEsgCoverage` green; packs absent |
| 2 Zero-stub | PASS | new files grep clean; CI |
| 3 Types | PASS | `compileKotlin` + `compileIntegrationTestKotlin` |
| 4 Lint + format | PASS | `ktlintCheck` clean |
| 5 Tests | PASS (local) | full chain BUILD SUCCESSFUL 15m36s — 108 PIT methods + 56 unit tests, 0 failures; JaCoCo instruction 84.79% (>=0.80) |
| 6 Accessibility | PASS | No UI change |
| 7 Design system | PASS | No UI change |
| 8 Contracts | PASS | snapshot regenerated from live `/api-docs`; codegen stamped `58528aa4…` |
| 9 Data | PASS | V104 additive; cross-tenant job list empty |
| 10 Audit | PASS | Controller-mediated writes; jobs carry actor |
| 11 Docs | PASS | This report; LEDGER; tracker |
| 12 Exit item | PASS (partial) | 07.11 migration/benchmark/ESG slice |

## 5. Tests
Full suite: `integrationTest test jacocoTestReport jacocoTestCoverageVerification` — BUILD SUCCESSFUL, 108 PIT methods + 56 unit tests, 0 failures, JaCoCo instruction 84.79%.

Named PIT **`migrationBenchmarkEsgCoverage`**:
- Group with two hospitals, 2 admissions in A and 1 in B; both opt in → own 2, peers 2, median 1.5, p25 1.25, p75 1.75; B opts out → peers 1; A opts out → 400.
- ESG energy/water/carbon recorded; invalid metric 400; summary totals + carbon per admission 125.
- Migration job with 2 valid + 1 invalid row → VALIDATED (2/1); import → IMPORTED with 2 patients, one row errored; re-import 409; rollback → ROLLED_BACK; invalid source 400.
- Cross-tenant migration jobs list empty.

## 6. Accessibility
No UI change.

## 7. Dual review — adversarial pass
1. **Row isolation** — each imported row commits in its own transaction; a failure marks only that row.
2. **Validation before import** — required fields and ISO date are checked before any write.
3. **Import/rollback once** — IMPORTED cannot be re-imported; rollback requires IMPORTED and is soft (no hard delete).
4. **Benchmark consent** — caller and peers must opt in; no hospital identifiers are returned.
5. **Metric vocabulary** — only ADMISSIONS/REVENUE_QAR and the four ESG types are accepted; periods validated.
6. **Tenant** — every query is hospital-scoped; cross-tenant jobs are invisible.
7. **ESG math** — non-negative values; carbon intensity only when admissions > 0.

## 8. Deviations / blockers
- **External:** the 22 specialty packs and the config-only installer await human clinical review; not shipped.
- Migration covers PATIENT rows only; other entities (orders, invoices, documents) are follow-up.
- Rollback is a soft hide (`recordsStatus=duplicate`), not a hard delete, to preserve referential integrity.
- Benchmarking is group-scoped with two metrics; no national/consortium benchmark set.
- ESG metrics are manually recorded; no meter/utility integration.

## 9. Session trail
- Fixed a batch-poisoning defect during the build: a `@Transactional` patient create inside the import transaction marked it rollback-only when a row failed; moved row imports to `REQUIRES_NEW`.
