# Section 06.06 — BI / ClickHouse — gate-run report

Static read-only assessment. Baseline commit `886c51c`, CI run `43548016617` (all 6 CI gates green; merged instruction coverage 80.13%). No code, ledger, chunk file, or `.gate/` content changed by this report.

## 1. Scope
Chunk file (`19-CHUNK6-SECTIONS.md` §06.06): event-fed marts + nightly dimensional sync, KPI dictionary (§9) implemented, role command centres (CEO/COO/CFO/CMO/Nursing/Facilities) read-only, export centre; KPIs reconciled vs operational DB. Present at baseline: 7 read-only stats endpoints over the operational DB. **Marts, dictionary, command centres, export, reconciliation absent.**

## 2. Built
- `backend/squamed-core/src/main/kotlin/com/squamed/core/analytics/api/AnalyticsController.kt` — 7 GET endpoints.
- `backend/squamed-core/src/main/kotlin/com/squamed/core/analytics/service/` — JPQL counts over operational tables.
- No analytics migration (no new tables; reads existing tenant-scoped data).
- `apps/web` dashboard page (axe-scanned route `/dashboard`).

## 3. Data/API/event contracts delivered
- API: 7 GET analytics endpoints; permission `report:read:report`; read-only.
- No marts/sync tables; no export artifacts; no KPI dictionary store.

## 4. Gates 1–12
| # | Gate | Result | Evidence |
|---|---|---|---|
| 1 | Functional completeness | **FAIL (PARTIAL)** | Present: 7 basic stats endpoints (appointments, revenue, patients, etc.) over operational DB; `/dashboard` axe-scanned. Missing: event-fed marts + nightly dimensional sync (ClickHouse), KPI dictionary implementation, role command centres, export centre, KPI-vs-operational reconciliation. |
| 2 | Zero-stub scan | PASS | CI run 43548016617 (gate 2 green); SC-9 allowlist. |
| 3 | Types | PASS | CI run 43548016617 (gate 3 green). |
| 4 | Lint + format | PASS | CI run 43548016617 (gate 4 green). |
| 5 | Tests | PASS | CI run 43548016617 (gate 5 green); merged instruction coverage 80.13% ≥ 0.80. |
| 6 | Accessibility | PASS | CI run 43548016617 (gate 6 green). `/dashboard` **is** in axe `SCAN_ROUTES` — analytics UI covered. |
| 7 | Design system | PASS | CI run 43548016617 (gate 7 green). |
| 8 | Contracts | **FAIL** | Schema-registry harness not yet wired (00.02 scope). |
| 9 | Data | **FAIL** | RLS cross-tenant suite ABSENT until 00.04 (ADR-13) — analytics exposes tenant-scoped operational data (no new tables). |
| 10 | Audit | PASS | Read-only endpoints (no write paths); controller-mediated reads still within `AuditAspect.kt:22-23` scope. |
| 11 | Docs | PASS | This report re-checks chunk file §06.06; ledger untouched; gate-parity green. |
| 12 | Exit item | PASS | Named PIT slice: analytics assertions in `aiGatewayAndInteropJourney` (:875-880, 6 of 7 endpoints) + `/analytics/appointments` in `financialAndOperationsCoverage` (:3246), green in run 43548016617. |

## 5. Tests
- PIT: journey analytics assertions :875-880 (6 of 7 endpoints); `financialAndOperationsCoverage` `/analytics/appointments` :3246. Green in run 43548016617.
- Unit: none dedicated.
- Coverage: merged instruction 80.13%.

## 6. Accessibility
axe green in CI; `/dashboard` in `SCAN_ROUTES` — page-level axe coverage present.

## 7. Issues
- All KPIs are live JPQL counts on the operational DB — no mart layer, no reconciliation — **open**.
- One analytics endpoint not asserted in journey (7 built vs 6 asserted) — open test gap.
- No export centre / KPI dictionary — open.

## 8. External blockers
ClickHouse/warehouse infra selection is an internal platform decision; no vendor blocker for the current read-only slice.

## 9. Deviations from chunk file
None logged; chunk file not edited.

## 10. Exit-demo slice
`gradlew.bat :squamed-core:integrationTest --tests "*PlatformIntegrationTests*aiGatewayAndInteropJourney*" --tests "*PlatformIntegrationTests*financialAndOperationsCoverage*"` — last run green in CI run 43548016617.
