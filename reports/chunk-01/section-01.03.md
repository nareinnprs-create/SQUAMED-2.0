# Section 01.03 — Reception workspace (chunk 1)

**Full build pass** on branch `feat/01.03-reception-workspace` (off merged main `822fc49`). Supersedes the baseline assessment (PARTIAL → IN PROGRESS): every named content of the chunk line is now implemented, integration-tested, and demoed. Local gates green at write time; CI run `<run-id>` green (ledger row 62).

## 1. Scope
Per `14-CHUNK1-SECTIONS.md`: reception workspace — patient lookup, registration fast-path, check-in, visitor/attendant pass, consult-fee receipt, patient calling, cancel, reschedule. The baseline tree already had the registration fast-path (visit create + token), patient lookup, check-in, cancel (backend), and reschedule (backend, 01.01); missing were the visitor/attendant pass, the consult-fee receipt, the patient-calling UI, the cancel UI, the reschedule UI, and `/reception` scan coverage.

## 2. Built (current tree)
Backend:
- **`db/migration/V55__reception_workspace.sql`** — `reception.visit_pass` + `reception.consult_receipt`, both `hospital_id NOT NULL` with CHECK constraints (status/relation/mode/amount > 0) and `(hospital_id, visit_id, status)` lookup indexes; rollback note present; **zero new permission codes** (writes ride `reception:write:visit`, reads `reception:read:visit` — RECEPTIONIST holds both since V3, re-landed V54).
- **`receptionqueue/domain/VisitPass.kt` / `ConsultReceipt.kt`** (+ repositories) — entities for the two new tables; sequences are **hospital-scoped** (null-safe `:hid` `limit 1` @Query — the 01.02 lesson applied up front, not rediscovered).
- **`receptionqueue/service/VisitRegistrar.kt`** — extended with:
  - **Passes**: issue (VPS- sequence per hospital, relation attendant|visitor, phone optional), list per visit, return (stamps `returnedAt`, status `returned`); re-return → 409. Pass issue on a cancelled visit → 409 `VISIT_CANCELLED`.
  - **Receipts**: issue (RCP- sequence per hospital, `amountQar` + mode cash|card|upi), list per visit, **void keeps the row** (status `voided`, `voidedAt` stamped — audit trail preserved); re-void → 409. Receipt issue on a cancelled visit → 409.
  - Pass/receipt sequences are computed **before any mutation** (same ordering discipline as the 01.02 transfer fix).
- **`receptionqueue/api/ReceptionController.kt`** — 6 new endpoints: `POST /reception/visits/{id}/passes`, `GET /reception/visits/{id}/passes`, `PATCH /reception/passes/{id}/return`, `POST /reception/visits/{id}/receipts`, `GET /reception/visits/{id}/receipts`, `PATCH /reception/receipts/{id}/void`. Cross-tenant access denied via `requireSameHospital` → **403 ACCESS_DENIED** (§9).
- **`receptionqueue/api/Dtos.kt`** — `VisitPassDto` (+ issue request), `ConsultReceiptDto` (+ issue request).

Frontend/tooling:
- **`apps/web/src/app/(app)/reception/page.tsx`** — workspace extended:
  - **Patient-calling panel**: queue select (registration/pharmacy/billing/consult) + Call-next via the 01.02 `POST /queue/entries/call-next`; inline status feedback.
  - **Cancel-reason modal** on registered/checked-in visits → `PATCH /reception/visits/{id}/cancel {reason}` (backend pre-existing; UI new).
  - **Passes modal**: issue (name + relation + optional phone), list with status badges, Return on active rows.
  - **Receipts modal**: issue (amount QAR + mode), list, Void on issued rows (voided rows stay visible with a critical badge — audit trail).
- **`apps/web/src/app/(app)/appointments/page.tsx`** — **Reschedule button + modal** on scheduled/confirmed rows: doctor + date → `/schedules/{id}/slots` radio-group (house picker, full slots disabled) → `POST /appointments/{id}/reschedule {slotId}` (01.01 backend, UI new).
- **`apps/web/scripts/lib/scan-fixtures.mjs`** — `/reception` added to SCAN_ROUTES (axe scan + VRT now cover the reception page).
- **`docs/contracts/openapi-v1.json`** regenerated + **`packages/api-client/src/generated/schema.d.ts`** stamped (snapshot sha256 `aba59fd0…`) — same PR as the surface change.
- **`scripts/demo-01.03-reception-workspace.ps1`** — 26-check exit demo (pwsh, app on :8081).
- **PIT** `@Nested inner class ReceptionWorkspaceTests` in `PlatformIntegrationTests` (fresh hospitals `RD-$s`/`RDF-$s` per run; foreign receptionist on the second hospital).

## 3. Contracts delivered
- **DB**: `V55__reception_workspace.sql` — two new tables with rollback note (`DROP TABLE reception.consult_receipt; DROP TABLE reception.visit_pass;`); `hospital_id NOT NULL` on both.
- **API**: the 6 endpoints above. Guard errors are 400 message-as-code (`VISIT_NOT_FOUND`), state guards 409 (`VISIT_CANCELLED`, already-returned/voided), **cross-tenant → 403 `ACCESS_DENIED`** via `requireSameHospital` (the reception module's established convention — §9).
- **Events**: **zero new** — the chunk line names no events for this section; EventCatalog stays at 20 EMITTED types (PIT-asserted).
- **Permissions**: **zero new codes** — `reception:read:visit` / `reception:write:visit` cover all 6 endpoints.
- **Contract artifacts**: OpenAPI snapshot + codegen stamp in the same PR; PIT `openApiSnapshotMatchesLiveContract` green.

## 4. Gates 1–12
1. PASS — all named contents implemented: visitor/attendant pass (issue/return/per-hospital sequence), consult-fee receipt (issue/void-keeps-row/per-hospital sequence), patient calling UI, cancel UI with reason, reschedule UI, `/reception` in SCAN_ROUTES.
2. PASS — CI: run `<run-id>` green (PR #43, squash-merged; no `--admin`).
3. PASS — types: `npx turbo typecheck` clean (7/7 tasks, 2026-11-17).
4. PASS — lint/format: `ktlintFormat` clean; `test ktlintCheck` re-run green **after** the final PIT edits (no order slip this section).
5. PASS — tests: turbo **146** unit tests (web 88, ui 48, api-client 5, schemas 5); PIT **62/62** (external mode, real compose PG), including the new `ReceptionWorkspaceTests`.
6. PASS — accessibility: `/reception` now in SCAN_ROUTES → axe-scanned, **zero violations of any impact** on the page (Gate 6 PASSED: zero serious/critical; only the 4 pre-existing moderates on `/` and `/login` remain, unchanged).
7. PASS — VRT: **8/8** against committed baselines on the production build (chromium, serial, 2% threshold); new `/reception` baseline created (`reception.png`, verified to render the real calling panel + table).
8. PASS — contracts: OpenAPI snapshot + api-client stamp regenerated in the same PR; EventCatalog 20 EMITTED types asserted in PIT; schema-registry harness remains the 00.02 chunk-level note (unchanged).
9. PASS — data: V55 `hospital_id NOT NULL` on both new tables; tenancy enforced at service layer (`requireSameHospital`, PIT-verified 403s); **RLS stays the 00.04/ADR-13 chunk-level seam** (§9); rollback note present.
10. PASS — audit: `AuditAspect` covers the new controller writes (pass issue/return, receipt issue/void are POST/PATCH → audit rows; list endpoints are read-only GETs by design).
11. PASS — docs: this report; ledger row 62 + merge-log entry follow the PR in the standard flow.
12. PASS — exit demo: `scripts/demo-01.03-reception-workspace.ps1` **26/26** (§10).

## 5. Tests
- **PIT** (external mode): `ReceptionWorkspaceTests.receptionWorkspaceNamedContentsCoverage` — fresh hospitals `RD-$s`/`RDF-$s` → receptionist registration (01.01 pattern) → patient lookup (fast-path) → walk-in visit `VN-` → check-in → **passes**: `VPS-0001` (attendant + phone) then `VPS-0002` (visitor) on the same hospital sequence → return stamps `returnedAt` → re-return 409 → list shows 2 → **receipts**: `RCP-0001` (cash 500) → void keeps the row (`voided` + `voidedAt`) → re-void 409 → `RCP-0002` (card) → list shows 1 voided + 1 issued → **calling**: queue entry `C-01` via `POST /queue/entries` → `PATCH /queue/entries/consult/C-01/call` → called → **cancel** with reason → cancelled visit refuses a new pass (409 `VISIT_CANCELLED`) → **foreign hospital** proves the pass sequence is per-hospital (`VPS-0001` again on `RDF-`) → **cross-tenant**: foreign receptionist denied 403 on visit read, pass list, receipt list, pass issue → **RBAC**: nurse (no `reception:read:visit`) 403 on visit list. Full suite **62/62**.
- **Frontend**: turbo 146/146 unit tests; typecheck + build clean; Gate 6 + Gate 7 green (§6/§7 of the gates).

## 6. Accessibility
`/reception` joined SCAN_ROUTES this section, so the page is now axe-scanned (**zero violations of any impact**) and visually baselined. The calling panel's queue select, the cancel-reason input, and both modals are labelled house primitives (`Select`/`Input`/`Modal`/`Button`); voided/returned states are badge text, not color-alone. The appointments reschedule modal reuses the labelled slot radio-group from 01.01. Gate 6's only findings remain the 4 pre-existing moderates on `/` and `/login` (unchanged from earlier sections).

## 7. Issues found during build + fixes
- **Cross-tenant status correction (assert bug, not a leak)**: the demo/PIT first expected 400 (the queue module's scoped-query convention) for cross-tenant pass/receipt access, but reception module code uses `requireSameHospital` → **403 ACCESS_DENIED**. A live probe confirmed denial with no data leak; the leak property is intact — the asserts were corrected 400 → 403 and recorded as a deviation (§9).
- **Wrong HTTP verb in the PIT calling check**: `POST /queue/entries/{q}/{token}/call` → 405; `QueueController` exposes it as PATCH. Fixed the PIT to PATCH.
- **Kotlin compile fix**: `JsonNode.size` → `size()` (×2) in the new PIT asserts.
- **App port**: the first `bootRun` bound the default **8080** (missed `SERVER_PORT=8081`); the demo/PIT convention is 8081 — restarted with `SERVER_PORT=8081`.
- **Demo invalidated by Docker Desktop death** (third this session): mid-demo the PG connection refused; the demo run was scrap. Restarted Docker, brought the stack back (PG healthy; Grafana :3005 bind conflict = known non-issue), reran clean. A second app death (Hikari timeout while PG was restarting) required one `bootRun` restart.
- **Snapshot regeneration trap avoided**: `pwsh ConvertTo-Json` would reformat the contract; used the documented `curl.exe -s http://localhost:8081/api-docs -o docs/contracts/openapi-v1.json` + `npm run codegen` flow (`docs/development/contract-codegen.md`).
- **ktlint order held**: `ktlintFormat` ran after all source edits, and `test ktlintCheck` was re-run green after the final PIT edits — the 01.02 CI slip was not repeated.

## 8. External blockers
None new. **Pass/receipt printing** (rendered slip pixels) needs the kiosk/print surfaces and is delivered in 01.08 — the data contract and lifecycle ship here. E1/E2/E3 unchanged from the planning ledger.

## 9. Deviations from chunk file
- **Patient calling rides the 01.02 queue endpoints** (`call-next` + per-entry call) — no new queue code; the workspace adds the UI.
- **Reschedule UI rides the 01.01 `POST /appointments/{id}/reschedule`** — no new scheduling code; the appointments page adds the picker modal.
- **Zero new events** — EventCatalog stays at 20 EMITTED types; the chunk line names no events for this section.
- **Zero new permission codes** — pass/receipt surfaces ride `reception:read:visit`/`reception:write:visit`.
- **Cross-tenant denial is 403 `ACCESS_DENIED`** (`requireSameHospital`, the reception module's existing convention), not the queue module's scoped-query + 400 — same denial strength (no leak), different status family than 01.02's convention.
- **Receipt void keeps the row** (status `voided` + `voidedAt`) rather than deleting — financial audit-trail requirement; voided rows stay listed with a critical badge.
- **Pass/receipt rendering (printed pixels) deferred to 01.08** — this section delivers numbers, lifecycle, and audit state; the kiosk/print section owns the physical output.
- **Exit demo is 26 checks**, not the sketched minimum — grew during verification (RBAC, foreign-sequence, and re-return/re-void guards as separate checks).

## 10. Exit-demo slice
`pwsh scripts/demo-01.03-reception-workspace.ps1` against the app on :8081 (`SERVER_PORT=8081 .\gradlew.bat bootRun`, compose dev stack up): admin login → two fresh hospitals `RD-$s`/`RDF-$s` + receptionists (one foreign) → patient lookup (fast-path) → walk-in visit `VN-` + check-in → passes `VPS-0001`/`VPS-0002` → return + `returnedAt` → re-return 409 → list 2 → receipt `RCP-0001` → void keeps row → re-void 409 → `RCP-0002` → list 2 (1 voided + 1 issued) → queue entry `C-01` → call-next → cancel with reason → cancelled visit refuses pass (`VISIT_CANCELLED`) → foreign hospital sequence starts `VPS-0001` → cross-tenant 403 ×4 → nurse RBAC 403. **26 passed, 0 failed**, live 2026-11-17. PIT 62/62 recorded above; CI run `<run-id>` green.
