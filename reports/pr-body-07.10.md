## 07.10 — Specialty packs + migration studio + benchmarking/ESG (partial)

Migration studio, consent-gated benchmarking and ESG reporting. The 22 specialty packs await human clinical review (external).

### Schema (`V104`, additive)
- `migration.job`, `migration.staged_row`, `benchmark.participation`, `esg.metric`.
- Permissions `migration:read|write`, `benchmark:read|write`, `esg:read|write`.

### Backend
- **Migration (`/api/v1/migration`):** source vocabulary, row-level validation, staged rows, import valid patient rows, reconciliation counts, import-once, soft rollback. Per-row `REQUIRES_NEW` so one bad row cannot poison the batch.
- **Benchmarking (`/api/v1/benchmark`):** participation consent, group-scoped peers, anonymized own/median/p25/p75 (ADMISSIONS, REVENUE_QAR); opted-out peers excluded.
- **ESG (`/api/v1/esg`):** energy/water/waste/carbon per period, totals, carbon per admission.

### Evidence
- New PIT `migrationBenchmarkEsgCoverage`: 2-vs-1 admissions benchmark (median 1.5, p25 1.25, p75 1.75), opt-out exclusion, non-participant 400; ESG totals + 125 kg/admission; migration 2 valid + 1 invalid → import 2 → 409 re-import → rollback; cross-tenant jobs empty.
- OpenAPI snapshot regenerated, codegen stamped; api-client 5/5.

### External / deviations
- **22 specialty packs + config-only installer not built** — human clinical review is external.
- Migration covers PATIENT rows only.
- Rollback is a soft hide (`recordsStatus=duplicate`), not a hard delete.
- Benchmarking is group-scoped with two metrics; no consortium set.
- ESG metrics are manually recorded (no meter integration).
