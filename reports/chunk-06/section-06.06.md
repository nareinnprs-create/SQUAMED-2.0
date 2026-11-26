# Section 06.06 — BI / ClickHouse (chunk 6)

**Verdict: SECTION REVIEW → local gates green; CI pending.** ClickHouse event mart + KPI snapshots, KPI dictionary, role command centres, export centre and reconciliation.

## 1. Scope
Per `chunk-06-enterprise-scale.md` item 6 / `19-CHUNK6-SECTIONS.md` 06.06: ClickHouse fed by the event stream + nightly dimensional sync, KPI dictionary implemented, role command centres (CEO/COO/CFO/CMO/Nursing/Facilities) read-only, export centre; KPIs reconciled against the operational DB.

## 2. Key artifacts
**Migration `V106`**: `bi.mart_sync` cursor; `bi:read|write:bi`; roles `CEO`, `COO`, `CFO`, `CMO` with read-only BI grants.

**Infrastructure**: `clickhouse/clickhouse-server:24.8-alpine` in the dev/CI compose (HTTP 8123, healthcheck), `clickhouse_data` volume.

**Backend**
- `com.clickhouse:clickhouse-jdbc:0.6.5`.
- `bi/domain/BiEntities.kt`, `repository/BiRepositories.kt`, `service/BiService.kt`, `api/BiController.kt` (5 endpoints under `/api/v1/bi`).
- `EventOutboxRepository.findSentForHospitalAfter` for the mart cursor.

## 3. Gap closure
- ✔ ClickHouse schema created on first use: `squamed_bi.events` (MergeTree) and `squamed_bi.kpi_snapshot` (ReplacingMergeTree).
- ✔ Event-fed mart: SENT outbox rows after the hospital cursor are batch-inserted; cursor persists in `bi.mart_sync`.
- ✔ Nightly dimensional sync at 02:00 for every hospital (plus on-demand `POST /bi/sync`).
- ✔ KPI dictionary: ADMISSIONS, REVENUE_QAR, LAB_ORDERS with names/units.
- ✔ Role command centres: CEO/COO/CFO/CMO/NURSING/FACILITIES metric subsets.
- ✔ Export centre: CSV (`metric,value,unit`).
- ✔ Reconciliation: ClickHouse `argMax` values vs freshly computed operational values (tolerance 0.01).

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | `biClickHouseCoverage` green |
| 2 Zero-stub | PASS | new files grep clean; CI |
| 3 Types | PASS | `compileKotlin` + `compileIntegrationTestKotlin` |
| 4 Lint + format | PASS | `ktlintCheck` clean |
| 5 Tests | PASS (local) | full chain BUILD SUCCESSFUL 13m35s - 110 PIT methods + 56 unit tests, 0 failures; JaCoCo instruction 84.88% (>=0.80) |
| 6 Accessibility | PASS | No UI change |
| 7 Design system | PASS | No UI change |
| 8 Contracts | PASS | OpenAPI snapshot regenerated (sha256 `c85320e0da8c125e2f24af0522d590438baa4281b027bc90d069d4f67be8f6c6`); api-client codegen stamped; ContractTests green |
| 9 Data | PASS | V106 additive; cross-tenant KPI rows empty |
| 10 Audit | PASS | Controller-mediated sync; cursor carries actor |
| 11 Docs | PASS | This report; LEDGER row; chunk-06 tracker; `reports/pr-body-06.06.md` |
| 12 Exit item | PASS (partial) | 06.09 exit slice |

## 5. Tests
Full suite: `integrationTest test jacocoTestReport jacocoTestCoverageVerification` - BUILD SUCCESSFUL, 110 PIT methods + 56 unit tests, 0 failures, JaCoCo instruction 84.88%.

Named PIT **`biClickHouseCoverage`**:
- Seed 1 admission, 500 QAR invoice, 1 lab order → sync KPIs 1/500/1; dictionary units; CEO sees 3 KPIs, CFO 1, unknown role 400.
- CSV export contains header + `ADMISSIONS,1`; reconcile reports no drift; second hospital sees zero KPI rows.

## 6. Accessibility
No UI change (dashboards are API surfaces for the web app).

## 7. Dual review — adversarial pass
1. **Cursor** — only SENT events after the persisted cursor are ingested; replay-safe.
2. **Idempotent KPIs** — ReplacingMergeTree + `argMax(value, generated_at)` returns the latest snapshot.
3. **Consent-free but tenant-scoped** — every KPI query filters by hospital id; cross-tenant rows are invisible.
4. **Reconciliation** — mart vs live operational values with a 0.01 tolerance; drift is surfaced.
5. **Role surfaces** — read-only role endpoints; unknown role rejected.
6. **Availability** — ClickHouse connection retries briefly and fails loudly (`BI_UNAVAILABLE`) instead of returning fake zeros.
7. **Nightly sync** — per-hospital failures are isolated with `runCatching`.

## 8. Deviations / blockers
- ClickHouse is dev/CI infrastructure; production topology (replicas, backups) is a deployment concern (`09`).
- Dev-stack stability: `.wslconfig` caps the WSL/Docker VM at 6 GB (host 15.3 GB) and compose services carry `restart: unless-stopped`; before this, the engine was killed mid-run and every container exited 255.
- KPI set is the three implemented metrics; the blueprint §9 dictionary grows as modules add metrics.
- Command centres are API responses; the web dashboards render them separately.
- 06.07 (outbound FHIR) remains IN PROGRESS and 06.09 exit remains SECTION REVIEW.

## 9. Session trail
- Added ClickHouse to the compose stack; JDBC driver resolved; named PIT verified locally.
