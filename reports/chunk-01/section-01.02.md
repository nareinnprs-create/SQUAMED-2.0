# Section 01.02 — Queue & token engine (chunk 1)

**Gate-run assessment of the CURRENT tree** @ 886c51c (merged baseline + wiring PRs #2–#11). Static read-only; no builds/tests run. Verdict after verification: **PARTIAL** → **IN PROGRESS** (named contents missing). CI evidence: run 43548016617 — all 6 gates green.

## 1. Scope
Per `14-CHUNK1-SECTIONS.md`: token issuance per dept/doctor, priority classes (emergency/VIP/assisted/appointment-order) config-driven, transfer, re-print, estimated wait, QR queue ID, emergency bump < 2 s to display.

## 2. Built (current tree)
- `backend/squamed-core/src/main/kotlin/com/squamed/core/receptionqueue/domain/QueueEntry.kt` — token entity: queue_type, department_id, service_point_id/name, visit/patient/appointment ids, token_number, patient_name, priority, status, called/started/completed/skipped/no_show timestamps, `estimated_wait_minutes` (column exists, never written), queue_date.
- `backend/.../receptionqueue/domain/ServicePoint.kt` — counter/service-point entity.
- `backend/.../receptionqueue/service/QueueService.kt` — daily token sequence (`{Q}-01…`), add, call (emits `queue.called` event), start, complete, skip, postpone, no-show, board (status+priority+time sort), callNext (priority rank emergency→urgent→routine), statistics (waiting/in-service/completed/avg wait), service points CRUD.
- `backend/.../receptionqueue/api/QueueController.kt` — POST/GET entries, board, call-next, per-token transitions, service-points, statistics endpoints.
- `backend/.../receptionqueue/api/Dtos.kt` — queue DTOs.
- `apps/web/src/app/(app)/queue/page.tsx` (9.3 KB) — board + token operations UI (real page).

## 3. Data/API/event contracts delivered
- Schema: `V11__reception_queue.sql` → `queue.queue_entry`, `queue.service_point`, `reception.visit_registration`.
- API: `/api/v1/queue/entries` (POST), `/board`, `/entries/call-next`, `/entries/{queueType}/{tokenNumber}/call|start|complete|skip|postpone|no-show`, GET entry, `/service-points` (GET/POST), `/statistics`.
- Event: `queue.called` (direct Kafka). WS `/ws` exists repo-wide but the < 2 s emergency-bump-to-display latency path is not implemented/measured.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | **FAIL** | Token issuance per queue type + lifecycle (call/start/complete/skip/postpone/no-show) + board + callNext + stats work (PIT). Missing named contents: **config-driven** priority classes (ranks hardcoded in `priorityRank()`), **transfer** between queues, **re-print**, **estimated wait** (column exists, never populated), **QR queue ID**, **emergency bump < 2 s to display**. Gap list: priority classes config · transfer · re-print · QR queue ID · estimated-wait population |
| 2 Zero-stub | PASS | CI run 43548016617 (gate 2 green); no stub markers in receptionqueue module or queue page |
| 3 Types | PASS | CI run 43548016617 |
| 4 Lint + format | PASS | CI run 43548016617 |
| 5 Tests | PASS | CI run 43548016617; named tests below; merged instruction coverage 80.13% ≥ 0.80 |
| 6 Accessibility | PASS | CI run 43548016617 (axe 0 serious/critical). Caveat: `/queue` is NOT in SCAN_ROUTES (scan covers /, /login, /appointments, /patients, /dashboard) — page not axe-scanned |
| 7 Design system | PASS | CI run 43548016617 (visual regression green) |
| 8 Contracts | **FAIL** | schema-registry harness not yet wired (00.02 scope); OpenAPI surfaces `queue` (PIT `openApiExposesAllModuleSurfaces`); `queue.called` event has no committed schema |
| 9 Data | **FAIL** | Migration `V11__reception_queue.sql` applied; **no rollback note**; **no RLS/cross-tenant denial suite** (00.04/ADR-13 pending); `hospital_id` nullable on queue_entry |
| 10 Audit | PASS | `AuditAspect` intercepts QueueController writes → audit rows for token transitions; audit read asserted in PIT `collaborationAndWorkflowJourney`. Caveat: plain audit table (ADR-9 pending) |
| 11 Docs | PASS | This report; chunk file lists contents accurately; ledger row stays PLANNED |
| 12 Exit item | **FAIL** | no scripted slice yet (phase1-e2e.ps1 walks queue board/entries/statistics but is not the chunk-exit script) |

## 5. Tests
Named PIT tests (green in run 43548016617): `queueAndScribeAndAiCoverage` (entries skip/postpone/no-show), `financialAndOperationsCoverage` (entries create/call/start/complete + service-points + statistics), `fullPatientRevenueJourney` (board + entry start). Pass counts: PIT 37/37. Coverage: merged instruction 80.13% (branch 52%).

## 6. Accessibility
Repo-wide axe gate green (run 43548016617), but `/queue` is outside the 5 scanned routes — no per-page axe evidence for this section's screen.

## 7. Issues found during build + fixes
Baseline section — see BASELINE-AUDIT.md §2.1 (receptionqueue row: PARTIAL, no config-driven priority classes/QR/re-print/estimated wait). Verified in current tree: `estimatedWaitMinutes` and `servicePointName` columns exist (queried by AnalyticsService) but no code path writes them — same PARTIAL verdict stands.

## 8. External blockers
None for this section's code (TV display latency target is 01.06's external hardware soak, separate section).

## 9. Deviations from chunk file
Zero (SC-6 baseline adoption; no chunk-file edit required).

## 10. Exit-demo slice
`pwsh scripts/phase1-e2e.ps1` — walks `/api/v1/queue/board`, `/queue/entries`, `/queue/statistics`. Not wired as chunk-exit slice; nearest scripted evidence = PIT `queueAndScribeAndAiCoverage` + `financialAndOperationsCoverage`.
