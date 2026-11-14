# Section 01.01 — Scheduling engine (chunk 1)

**Gate-run assessment of the CURRENT tree** @ 886c51c (merged baseline + wiring PRs #2–#11). Static read-only; no builds/tests run. Verdict after verification: **PARTIAL** → **IN PROGRESS** (named contents missing). CI evidence: run 43548016617 — first all-6-gates-green run (commit 886c51c).

## 1. Scope
Per `14-CHUNK1-SECTIONS.md`: doctor schedule templates (session-based), resources (doctor+room+equipment binding), slot generation, booking, waitlist, overbooking rules (config), no-show tracking, teleconsult slot type (used chunk 6).

## 2. Built (current tree)
- `backend/squamed-core/src/main/kotlin/com/squamed/core/scheduling/domain/Appointment.kt` — appointment entity: hospital_id (nullable), patient/department/specialty/doctor, type, start/end, duration, status, visit_type, priority, **telemedicine flag**, room_id, external_ref, source, checked_in/confirmed/cancelled/completed/**no_show_at** timestamps, cancellation_fee.
- `backend/.../scheduling/api/AppointmentDtos.kt` — create/update/status/view DTOs.
- `backend/.../scheduling/api/AppointmentController.kt` — POST create (start/end/patient/doctor/type validation), GET by id, GET list (patient/doctor/status/from-to), PUT update, PATCH status (confirmed/arrived/cancelled/completed/no-show) + `appointment.status_changed` domain event.
- `backend/.../scheduling/repository/AppointmentRepository.kt` — query finders (by patient, doctor, status, date range).
- `apps/web/src/app/(app)/appointments/page.tsx` (15.8 KB) — booking + list + status/cancel UI (real page, no placeholder markers).

## 3. Data/API/event contracts delivered
- Schema: `V10__appointments.sql` → `scheduling.appointment`.
- API: `/api/v1/appointments` (POST, GET list w/ filters), `/api/v1/appointments/{id}` (GET, PUT), `/api/v1/appointments/{id}/status` (PATCH).
- Event: `appointment.status_changed` (direct Kafka via `DomainEventPublisher`).
- Permissions: `scheduling:write:appointment`, `scheduling:read:appointment` (`@RequiresPermission`).

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | **FAIL** | Booking + status transitions + no-show tracking work end-to-end (PIT). Missing named contents: session templates, slot generation, waitlist, overbooking rules, doctor+room+**equipment** resource binding (roomId only), teleconsult slot *type* (flag exists only). Gap list: session templates · slot generation · waitlist · overbooking · equipment binding |
| 2 Zero-stub | PASS | CI run 43548016617 (gate 2 green); no stub markers in scheduling module or appointments page |
| 3 Types | PASS | CI run 43548016617 (backend-gates + frontend-gates green) |
| 4 Lint + format | PASS | CI run 43548016617 (ktlint + frontend lint green) |
| 5 Tests | PASS | CI run 43548016617; named tests below; merged instruction coverage 80.13% ≥ 0.80 |
| 6 Accessibility | PASS | CI run 43548016617 (a11y-perf-gates: axe 0 serious/critical); `/appointments` IS in SCAN_ROUTES (apps/web/scripts/lib/scan-fixtures.mjs) |
| 7 Design system | PASS | CI run 43548016617 (visual regression + Lighthouse budget green) |
| 8 Contracts | **FAIL** | schema-registry harness not yet wired (00.02 scope); OpenAPI surfaces `appointments` (PIT `openApiExposesAllModuleSurfaces`); no committed OpenAPI/event-schema artifacts; event envelope v1 absent (publisher is direct Kafka, catch→log→drop) |
| 9 Data | **FAIL** | Migration `V10__appointments.sql` applied; **no rollback note** in any migration file; **no RLS / cross-tenant denial suite** (0 RLS hits in 40 migrations — 00.04/ADR-13 pending); `hospital_id` nullable + client-supplied |
| 10 Audit | PASS | `AuditAspect` intercepts all `*Controller` writes (AppointmentController included) → audit rows for every write path; PIT `collaborationAndWorkflowJourney` asserts audit logs recorded. Caveat: plain audit table (hash chain = ADR-9, pending 00.10); no scheduling-specific named audit test |
| 11 Docs | PASS | This report; chunk file `14-CHUNK1-SECTIONS.md` lists contents accurately; ledger row stays PLANNED (correct: gates not all passed) |
| 12 Exit item | **FAIL** | no scripted slice yet (e2e has only login + api-smoke; phase1-e2e.ps1 covers appointments REST walk but is not wired to CI/exit) |

## 5. Tests
Named PIT tests (integration, green in run 43548016617): `fullPatientRevenueJourney` (appointment create + status flow), `financialAndOperationsCoverage` (appointments CRUD + `/appointments/{id}/status` + `analytics/appointments`), `openApiExposesAllModuleSurfaces` (appointments surface). Pass counts: PIT 37/37 green on run 43548016617. Coverage: repo-wide merged instruction 80.13% (branch 52%); no per-section coverage measurement.

## 6. Accessibility
`/appointments` is one of the 5 axe-scanned routes — zero serious/critical on run 43548016617. Keyboard path for booking task not separately verified (static read).

## 7. Issues found during build + fixes
Baseline section — see BASELINE-AUDIT.md §2.1 (scheduling row: PARTIAL, no session templates/slots/waitlist/overbooking/resource binding). Verified in current tree: unchanged from baseline audit — no wiring PR touched scheduling.

## 8. External blockers
None (teleconsult slot type is consumed by chunk 6, not externally blocked).

## 9. Deviations from chunk file
Zero. Baseline code adopted under SC-6 (product-owner approved); no chunk-file edit required for adoption.

## 10. Exit-demo slice
`pwsh scripts/phase1-e2e.ps1` (requires dev stack up: `scripts/dev-up.sh`) — walks appointment create → status transitions via `/api/v1/appointments`. Not yet scripted as the chunk-exit slice; nearest scripted evidence = PIT `financialAndOperationsCoverage` in integration-gates.
