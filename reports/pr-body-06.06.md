## 06.06 — BI / ClickHouse

Event-fed ClickHouse marts, KPI dictionary, role command centres, export centre, and KPI reconciliation against the operational DB.

### Infrastructure
- `clickhouse/clickhouse-server:24.8-alpine` in the dev/CI compose (HTTP 8123, `127.0.0.1` healthcheck), `clickhouse_data` volume.
- `com.clickhouse:clickhouse-jdbc:0.6.5`.

### Schema (`V106`, additive)
- `bi.mart_sync` per-hospital event cursor; permissions `bi:read|write:bi`; roles `CEO`, `COO`, `CFO`, `CMO` with read-only BI grants.

### Backend (`/api/v1/bi`)
- **sync:** SENT outbox events after the hospital cursor are batch-inserted into `squamed_bi.events`; KPI snapshots written to `squamed_bi.kpi_snapshot` (ReplacingMergeTree, `argMax(value, generated_at)` on read).
- **kpis:** KPI dictionary (ADMISSIONS, REVENUE_QAR, LAB_ORDERS) with names/units.
- **dashboards/{role}:** CEO/COO/CFO/CMO/NURSING/FACILITIES read-only metric subsets; unknown role 400.
- **exports:** CSV `metric,value,unit`.
- **reconcile:** mart values vs freshly computed operational values (tolerance 0.01).
- Nightly dimensional sync at 02:00 per hospital; failures isolated per hospital.

### Evidence
- New PIT `biClickHouseCoverage` (real ClickHouse, no substitute): 1 admission + 500 QAR invoice + 1 lab order → KPIs 1/500/1; dictionary units; CEO 3 metrics, CFO 1, unknown role 400; CSV header + `ADMISSIONS,1`; reconcile no drift; second hospital sees zero KPI rows.
- Dev stack: `.wslconfig` caps the WSL/Docker VM at 6 GB and the six core compose services carry `restart: unless-stopped` — the engine was being killed mid-run and taking every container down with it.
- Full local chain: 110 PIT methods + 56 unit tests, 0 failures; JaCoCo instruction 84.88% (≥ 0.80).
- OpenAPI snapshot regenerated, codegen stamped.

### Deviations
- ClickHouse is dev/CI infrastructure; production topology (replicas/backups) is a deployment concern.
- KPI set is the three implemented metrics; the blueprint dictionary grows as modules add metrics.
- Command centres are API surfaces; the web dashboards render them separately.
- 06.07 (outbound FHIR) and 06.09 (chunk exit) remain open.
