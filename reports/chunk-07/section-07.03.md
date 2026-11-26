# Section 07.03 — Digital twin + capacity intelligence (chunk 7)

**Verdict: SECTION REVIEW → local gates green; dependency deviation recorded (06.06 ClickHouse marts not available).** Materialized operational snapshots and a capacity answer with evidence.

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
- ⚠ Staffing/demand forecasting and the 30-day event replay are not modelled; 06.06 ClickHouse marts are not available, so the twin reads operational tables directly.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS (partial) | `digitalTwinCapacityCoverage` green |
| 2 Zero-stub | PASS | new files grep clean; CI |
| 3 Types | PASS | `compileKotlin` + `compileIntegrationTestKotlin` |
| 4 Lint + format | PASS | `ktlintCheck` clean |
| 5 Tests | PASS (local) | full chain BUILD SUCCESSFUL 13m07s — 109 PIT methods + 56 unit tests, 0 failures; JaCoCo instruction 84.82% (>=0.80) |
| 6 Accessibility | PASS | No UI change |
| 7 Design system | PASS | No UI change |
| 8 Contracts | pending | snapshot + codegen |
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
7. **No ClickHouse dependency** — the twin reads governed tables directly until 06.06 lands.

## 8. Deviations / blockers
- **Dependency deviation:** 06.06 (BI/ClickHouse) is still IN PROGRESS; the twin uses operational tables instead of ClickHouse marts.
- Staffing (roster) and demand forecasting are not part of the capacity model yet.
- The "30-day replay" is implemented as snapshot-vs-live reconciliation, not an event replay.
- Role command centres (CEO/COO/CFO/CMO/Nursing/Facilities) are 06.06 scope, not built here.

## 9. Session trail
- Named PIT green first pass; full-suite verification follows.
