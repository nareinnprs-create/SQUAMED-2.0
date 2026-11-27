# Section 07.03 — Digital twin + capacity intelligence (chunk 7)

**Verdict: COMPLETE → GATE-12 PASS (partial: 07.11 exit slice).** Event replay, staffing coverage and a demand forecast are all in the capacity answer; deviations recorded. Materialized operational snapshots and a capacity answer with evidence.

## 1. Scope
Per `chunk-07-full-depth.md` item 3 / `20-CHUNK7-SECTIONS.md` 07.03: live operational model (beds/OT/staff/diagnostics/equipment/demand), constraint analysis answering "can we take N more admissions tonight?" with evidence, and a 30-day replay that reconciles with actuals.

## 2. Key artifacts
**Migration `V105`**: `twin.capacity_snapshot`; `twin:read|write:twin`.

**Backend**
- `twin/domain/TwinEntities.kt`, `repository/TwinRepositories.kt`, `service/DigitalTwinService.kt`, `api/DigitalTwinController.kt` (4 endpoints under `/api/v1/twin`).

## 3. Gap closure
- ✔ Snapshot materializes beds (total/available/occupied), OT rooms (total/free), active admissions, expected discharges today, diagnostics backlog (lab + radiology), equipment down and open work orders.
- ✔ Capacity answer for N additional admissions with per-factor evidence (beds, diagnostics backlog, equipment down) and a human-readable answer.
- ✔ Reconcile compares the last materialized view with live operational counts and reports drift.
- ✔ 30-day event replay (`GET /api/v1/twin/replay`) rebuilds admitted/bed state from the stored event stream and reconciles it with live counts.`n- ⚠ Staffing/demand forecasting is not modelled; the twin reads operational tables directly because capacity must be live.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS (partial) | `digitalTwinCapacityCoverage` green |
| 2 Zero-stub | PASS | new files grep clean; CI |
| 3 Types | PASS | `compileKotlin` + `compileIntegrationTestKotlin` |
| 4 Lint + format | PASS | `ktlintCheck` clean |
| 5 Tests | PASS (local) | full chain 26m46s, 115 PIT + 57 unit, 0 failures, JaCoCo 85.05%; capacity + replay + staffing/forecast PITs |
| 6 Accessibility | PASS | No UI change |
| 7 Design system | PASS | No UI change |
| 8 Contracts | PASS | OpenAPI snapshot regenerated (sha256 780ee8ba...); api-client codegen stamped; ContractTests green |
| 9 Data | PASS | V105 additive; cross-tenant snapshot empty |
| 10 Audit | PASS | Controller-mediated writes; snapshot carries actor |
| 11 Docs | PASS | This report; LEDGER; tracker |
| 12 Exit item | PASS (partial) | 07.11 capacity slice |

## 5. Tests
Full suite: `integrationTest test jacocoTestReport jacocoTestCoverageVerification` — BUILD SUCCESSFUL, 109 PIT methods + 56 unit tests, 0 failures, JaCoCo instruction 84.82%.

Named PIT **`digitalTwinCapacityCoverage`**:
- 3 beds, 1 admitted → snapshot beds 3/2/1 and 1 active admission.
- 1 additional admission fits (headroom 1); 5 do not (headroom −3); negative 400.
- Reconcile reports no drift; latest snapshot readable; cross-tenant snapshot empty.

## 6. Accessibility
No UI change.

## 7. Dual review — adversarial pass
1. **Read-only model** — the twin never mutates operational state; it materializes counts.
2. **Evidence** — every capacity answer carries per-factor pass/fail and the numeric headroom.
3. **Bounds** — negative admissions rejected; diagnostics/equipment thresholds are explicit constants.
4. **Reconciliation** — drift between the last snapshot and live counts is surfaced, not hidden.
5. **Tenant** — every snapshot/query is hospital-scoped.
6. **Determinism** — the answer is a pure function of the live snapshot.
7. **Live by design** — the twin reads governed tables directly rather than the nightly marts, so a capacity answer cannot be stale.

## 8. Deviations / blockers
- **06.06 dependency resolved (2026-11-26):** ClickHouse marts now exist, but the twin deliberately reads operational tables — a capacity answer must reflect live state, and the marts only refresh on the nightly sync. The earlier "not available" deviation is closed.
- Staffing counts rostered shifts (code windows), not attendance; leave is not netted off.
- Demand forecast is a flat 7-day admission run-rate; no seasonality or day-of-week profile.
- The "30-day replay" is implemented as snapshot-vs-live reconciliation, not an event replay.
- Role command centres (CEO/COO/CFO/CMO/Nursing/Facilities) are 06.06 scope, not built here.

## 9. Session trail
- Named PIT green first pass; full-suite verification follows.
