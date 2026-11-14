# Section 01.11 — Analytics mini + shadow-run prep (chunk 1)

**Gate-run assessment of the CURRENT tree** @ 886c51c (merged baseline + wiring PRs #2–#11). Static read-only; no builds/tests run. Verdict after verification: **PARTIAL** → **IN PROGRESS** (named contents missing). CI evidence: run 43548016617 — all 6 gates green.

## 1. Scope
Per `14-CHUNK1-SECTIONS.md`: OPD counts, wait times, no-show rate; pilot shadow-run checklist. Depends on 01.03, 01.04.

## 2. Built (current tree)
- `backend/squamed-core/src/main/kotlin/com/squamed/core/analytics/service/AnalyticsService.kt` (13.7 KB, read-only JPQL over live tables) — `overview()` (patients/encounters/appointments today, queue waiting, avg wait minutes, lab orders/pending, invoices/revenue/amount due, dispensations today), `patients()` (totals, by gender/blood group/nationality, 6-month registration trend), `encounters()` (total/today, by status/type, avg duration), `appointments()` (total/today, by status incl. no-show bucket, by type), `queue()` (waiting, called/completed today, avg wait, by service point, by queue type), `lab()` (total/today, by status/result status, released today), `billing()` (revenue today/week/month, outstanding, charges by category).
- `backend/.../analytics/api/AnalyticsController.kt` + `AnalyticsDtos.kt` — GET endpoints per domain.
- `apps/web/src/app/(app)/dashboard/page.tsx` (15.3 KB) — overview dashboard UI (no-show badge variant present).

## 3. Data/API/event contracts delivered
- API: `/api/v1/analytics/overview`, `/patients`, `/encounters`, `/appointments`, `/queue`, `/lab`, `/billing` (read-only).
- Events: none consumed (no event-fed marts — direct table JPQL).
- No new schema (read model over V10/V11/V12/V14/V15/V16 tables).

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | **FAIL** | OPD counts (appointments/encounters today) and wait times (avg wait via `estimatedWaitMinutes` — **which no code path populates**, so avgWait is structurally null) delivered. Missing named contents: **no-show rate** (no computed rate — only by-status raw counts; no dashboard metric), **pilot shadow-run checklist** (no artifact in repo). Gap list: wait-time population · no-show rate · shadow-run checklist |
| 2 Zero-stub | PASS | CI run 43548016617 (gate 2 green); `toDouble()` hits in AnalyticsService are the SC-9-allowlisted "todo"-substring false positives |
| 3 Types | PASS | CI run 43548016617 |
| 4 Lint + format | PASS | CI run 43548016617 |
| 5 Tests | PASS | CI run 43548016617; named tests below; merged instruction coverage 80.13% ≥ 0.80 |
| 6 Accessibility | PASS | CI run 43548016617 (axe 0 serious/critical); `/dashboard` IS in SCAN_ROUTES — scanned green |
| 7 Design system | PASS | CI run 43548016617 (visual regression green) |
| 8 Contracts | **FAIL** | schema-registry harness not yet wired (00.02 scope); OpenAPI surfaces `analytics` (PIT `openApiExposesAllModuleSurfaces`) |
| 9 Data | PASS (read-only) | No migration of its own; nothing to roll back; no tenant-scoped writes. Caveat: reads are not hospital-filtered (global aggregates) — noted, blocked by 00.04/ADR-13 scope decision |
| 10 Audit | PASS | Read-only endpoints; `AuditAspect` logs reads; no write paths |
| 11 Docs | PASS | This report; chunk file lists contents accurately; ledger row stays PLANNED |
| 12 Exit item | **FAIL** | no scripted slice yet (phase1-e2e.ps1 walks all `/api/v1/analytics/*` endpoints; not the chunk-exit script) |

## 5. Tests
Named PIT tests (green in run 43548016617): `financialAndOperationsCoverage` (asserts `/api/v1/analytics/appointments`), `aiGatewayAndInteropJourney` (AnalyticsService mocked for AI intents — unit side in `AiIntentServiceTest`), `openApiExposesAllModuleSurfaces` (analytics surface). Pass counts: PIT 37/37. Coverage: merged instruction 80.13% (branch 52%).

## 6. Accessibility
`/dashboard` is one of the 5 axe-scanned routes — zero serious/critical on run 43548016617.

## 7. Issues found during build + fixes
Baseline section — see BASELINE-AUDIT.md §2.1 (analytics row: PARTIAL — "basic stats only; no event-fed marts, KPI dictionary, command centres"). Verified in current tree: AnalyticsService grew to 7 stat endpoints incl. queue/lab/billing; wait-time metric exists but is structurally null (source column never written — cross-links to 01.02 gap).

## 8. External blockers
None (shadow-run checklist is a pilot-phase artifact; pilot is a later-chunk decision).

## 9. Deviations from chunk file
Zero (SC-6 baseline adoption).

## 10. Exit-demo slice
`pwsh scripts/phase1-e2e.ps1` — walks `/api/v1/analytics/{overview,patients,encounters,appointments,queue,lab,billing}`. Nearest scripted evidence = PIT `financialAndOperationsCoverage` analytics assert.
