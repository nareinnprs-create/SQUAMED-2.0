## 07.03 — Digital twin + capacity intelligence

Materialized operational snapshots and a capacity answer with evidence. 06.06 ClickHouse marts are still owed, so the twin reads operational tables directly.

### Schema (`V105`, additive)
- `twin.capacity_snapshot`; permissions `twin:read|write:twin`.

### Backend (`/api/v1/twin`)
- **Snapshot:** beds (total/available/occupied), OT rooms (total/free), active admissions, expected discharges today, diagnostics backlog (lab + radiology), equipment down, open work orders.
- **Capacity:** answer for N additional admissions with per-factor evidence (beds/headroom, diagnostics backlog, equipment down) and a human-readable verdict.
- **Reconcile:** last materialized view vs live counts with drift list.

### Evidence
- New PIT `digitalTwinCapacityCoverage`: 3 beds/1 admitted → snapshot 3/2/1; +1 fits (headroom 1), +5 blocked (headroom −3), negative 400; reconcile no drift; cross-tenant snapshot empty.
- OpenAPI snapshot regenerated, codegen stamped; api-client 5/5.

### Deviations
- 06.06 ClickHouse marts not available → twin reads operational tables.
- Staffing and demand forecasting not modelled; 30-day replay implemented as snapshot-vs-live reconciliation.
- Role command centres remain 06.06 scope.
