# Section 01.03 — Reception workspace (chunk 1)

**Gate-run assessment of the CURRENT tree** @ 886c51c (merged baseline + wiring PRs #2–#11). Static read-only; no builds/tests run. Verdict after verification: **PARTIAL** → **IN PROGRESS** (named contents missing). CI evidence: run 43548016617 — all 6 gates green.

## 1. Scope
Per `14-CHUNK1-SECTIONS.md`: registration fast-path (chunk-0 EMPI), lookup, check-in, visitor/attendant pass, consult-fee receipts, cancel/reschedule, patient calling.

## 2. Built (current tree)
- `backend/squamed-core/src/main/kotlin/com/squamed/core/receptionqueue/domain/VisitRegistration.kt` — visit entity: hospital_id, patient/appointment/department/doctor/specialty ids, visit_number (`VN-YYYY-####`), visit_type, reason, priority, source, registration_started/registered timestamps, status.
- `backend/.../receptionqueue/service/VisitRegistrar.kt` — create visit (+ optional auto add-to-queue via QueueService), get/list, check-in, complete, cancel (reason appended).
- `backend/.../receptionqueue/api/ReceptionController.kt` — POST `/visits`, GET visit(s), PATCH `check-in` / `complete` / `cancel`.
- `apps/web/src/app/(app)/reception/page.tsx` (15 KB) — registration + check-in + queue hand-off UI (real page).

## 3. Data/API/event contracts delivered
- Schema: `V11__reception_queue.sql` → `reception.visit_registration` (shared with 01.02).
- API: `/api/v1/reception/visits` (POST, GET list w/ status/patientId/from), `/visits/{id}` (GET), `/visits/{id}/check-in`, `/visits/{id}/complete`, `/visits/{id}/cancel`.
- Events: none emitted on visit create/check-in/cancel (queue add emits nothing; queue.called only when queue ops invoked).
- Permissions: `reception:write:visit`, `reception:read:visit`.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | **FAIL** | Visit registration → check-in → complete → cancel works end-to-end; registration fast-path lookup rides patient module (`patients/match` EMPI in PIT journey). Missing named contents: **visitor/attendant pass**, **consult-fee receipts** (no receipt generation anywhere in reception module; grep 0 hits), **patient calling** from workspace, **reschedule** (appointment PUT exists via 01.01 but no reception-side reschedule action), **explicit lookup UX** (patient search page exists — partial). Gap list: attendant pass · consult-fee receipts · patient calling · reschedule |
| 2 Zero-stub | PASS | CI run 43548016617 (gate 2 green); reception page has no placeholder markers |
| 3 Types | PASS | CI run 43548016617 |
| 4 Lint + format | PASS | CI run 43548016617 |
| 5 Tests | PASS | CI run 43548016617; named tests below; merged instruction coverage 80.13% ≥ 0.80 |
| 6 Accessibility | PASS | CI run 43548016617 (axe 0 serious/critical). Caveat: `/reception` NOT in SCAN_ROUTES — not axe-scanned |
| 7 Design system | PASS | CI run 43548016617 (visual regression green) |
| 8 Contracts | **FAIL** | schema-registry harness not yet wired (00.02 scope); OpenAPI surfaces `reception` (PIT `openApiExposesAllModuleSurfaces`); no committed event schemas (visits emit none) |
| 9 Data | **FAIL** | Migration `V11__reception_queue.sql` applied; **no rollback note**; **no RLS/cross-tenant denial suite** (00.04/ADR-13 pending); `hospital_id` nullable + client-supplied |
| 10 Audit | PASS | `AuditAspect` intercepts ReceptionController writes → audit rows for register/check-in/complete/cancel; audit read asserted in PIT `collaborationAndWorkflowJourney`. Caveat: plain audit table (ADR-9 pending) |
| 11 Docs | PASS | This report; chunk file lists contents accurately; ledger row stays PLANNED |
| 12 Exit item | **FAIL** | no scripted slice yet (phase1-e2e.ps1 walks `/api/v1/reception/visits` but is not the chunk-exit script) |

## 5. Tests
Named PIT tests (green in run 43548016617): `fullPatientRevenueJourney` (reception/visits create in the patient→queue→encounter→billing journey), `financialAndOperationsCoverage` (visits create/get/complete), `JourneyTests` reception leg. Pass counts: PIT 37/37. Coverage: merged instruction 80.13% (branch 52%).

## 6. Accessibility
Repo-wide axe gate green (run 43548016617); `/reception` outside scanned routes — no per-page axe evidence.

## 7. Issues found during build + fixes
Baseline section — see BASELINE-AUDIT.md §2.1 (receptionqueue row: PARTIAL, no QR/re-print/wait estimate; receipts/pass absent). Verified in current tree: grep over reception page + receptionqueue module found zero hits for receipt/attendant/visitor/pass/reschedule/calling — named-content gaps stand.

## 8. External blockers
None.

## 9. Deviations from chunk file
Zero (SC-6 baseline adoption).

## 10. Exit-demo slice
`pwsh scripts/phase1-e2e.ps1` — walks `/api/v1/reception/visits`. Nearest scripted evidence = PIT `fullPatientRevenueJourney` (registration leg).
