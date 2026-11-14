# Section 00.04 — Tenancy & RLS

## 1. Scope
Tenancy hardening for the whole platform (ADR-13, two-stage): **stage 1 (this section)** = `hospital_id` NOT NULL on all tenant-scoped domain tables, server-side tenant resolution (tenant derived from the authenticated principal, never trusted from the client), and the cross-tenant denial PIT suite that unblocks repo-wide gate 9 (00.03 landed it partial by design). **Stage 2** = Postgres RLS row-level policies — deliberately deferred to before chunk 6 per ADR-13; the NOT NULL + server-side pinning is the data-integrity and API-authority half, RLS is the row-level read-isolation half.

## 2. Built (current tree)
- `V43__tenant_hardening.sql` — backfills NULL `hospital_id` rows to the primary (first-created) hospital, then `SET NOT NULL` on **16 tenant-scoped tables**: `ai.interaction_log`, `billing.charge/invoice/tariff_item`, `clinical.encounter/order_entry`, `laboratory.lab_order`, `patient.patient_record`, `pharmacy.dispensation`, `queue.queue_entry/service_point`, `reception.visit_registration`, `scheduling.appointment`, `scribe.scribe_session`, `tasks.task`, `workflow.workflow_instance`. Header documents the 11 deliberately-nullable tables (`audit.audit_log`, `identity.session/device/user_role` — user_role NULL = platform-wide assignment and is the tenancy SOURCE — `ai.model_config/agent_config`, `workflow.workflow_definition`, `communication.channel`, `notifications.notification`, `interop.hl7_message/integration_connection`) and the rollback note (NOT NULLs droppable; backfilled values are historical attributions, not auto-reversible). Fresh-DB note: constraints apply cleanly before bootstrap seeds the first hospital; the old V43-style bootstrap-order guard was removed — Flyway runs before seeding on a fresh DB.
- `common/tenant/TenantResolver.kt` — `pin(principal, explicit)` (hospital users pinned; platform admins may choose), `pinOrDefault(...)` (primary-hospital fallback for NOT NULL writes), `primaryHospitalId()` (first-created hospital, else `NO_HOSPITAL_CONFIGURED`). Source of truth: JWT `hp` claim resolved at login from `user_role` assignments.
- `common/tenant/TenantInterceptor.kt` — hospital-bound users hitting `/api/v1/hospital-config/hospitals/{otherId}/...` path identifiers get `AccessDeniedException("TENANT_FORBIDDEN")` → 403; platform admins pass. Registered in `WebConfig`.
- `AuthService.resolveHospital` — 1 assignment → that hospital; >1 → claimed only if the claim ∈ assigned; 0 → null (platform admin). `RegisterDto.hospitalId` seeds hospital-bound users at registration. Login claims can never widen a tenant.
- `BootstrapDataInitializer` — default-hospital seed so fresh environments always have a primary tenant.
- **Controller sweep (17 controllers)**: every tenant-scoped write pins the tenant at the controller (`dto.copy(hospitalId = ...)` / `pinOrDefault`) and services only gain explicit `hospitalId` params where the service itself constructs entities (`PatientService.create`/`findOrCreateFromHl7`, `ScribeService.create`, `PharmacyService.dispense`, `BillingService`, `LabService`, `EncounterService`, `VisitRegistrar`, `QueueService`, …). Client-supplied `hospitalId` is honored only for platform admins.
- `Hl7Service` — MLLP ingest has no principal: ADT patient imports attribute to `TenantResolver.primaryHospitalId()` (stage-1 answer; revisit when MLLP carries hospital context).
- `application.yml` — removed `squamed.tenant.header` (the client never sets the tenant; server-side resolution only).
- Contract: OpenAPI snapshot + `@squamed/api-client` codegen refreshed in the same PR, drift stamp pinned (sha256 `ac99c808…`).

## 3. Contracts delivered
- No new REST endpoints — tenancy is attribute- and enforcement-level. `POST /api/v1/auth/register` gained optional `hospitalId`; patient create payloads gained optional (admin-only-honored) `hospitalId`; these surface in the regenerated snapshot.
- Snapshot + stamped codegen landed together (contract-codegen.md protocol); `openApiSnapshotMatchesLiveContract` green.
- Stage 2 (RLS) is a DB policy change, not a contract change.

## 4. Gates
1. PASS — zero real stubs; Zero-stub grep job green (run 43576150514).
2. PASS — run 43576150514 (PR #16, merge 7d567ec).
3. PASS — Detect source trees job green.
4. PASS — ktlint + unit + ArchUnit (Backend gates job) green; local `test ktlintFormat` BUILD SUCCESSFUL.
5. PASS — Backend gates job (unit suite) green.
6. PASS — Accessibility + performance gates (axe + Lighthouse + visual) green; no UI added.
7. PASS — Frontend gates (typecheck + lint + test) green.
8. PASS — Integration gates job green (event-envelope harness retained from 00.02).
9. PASS — V43 applied (schema v43); PIT denial suite green (`crossTenantAccessIsDeniedAndServerPinsTenant`); this closes the gate-9 partial left open by 00.03. **Stage 2 (PG RLS policies) remains open by design** — ADR-13 schedules it before chunk 6; tracked as the 00.04 residual until then.
10. PASS — audit surface unchanged; integration gates green.
11. PASS — this report; ledger row + merge-log #16 + totals updated.
12. PASS — scripted live slice `scripts/demo-00.04-tenant-rls.ps1`: **11/11 on 8081** (pwsh; header documents `SERVER_PORT=8081 bootRun`), 2026-11-14.

## 5. Tests
- PIT (new): `TenantIsolationTests.crossTenantAccessIsDeniedAndServerPinsTenant` — two fresh hospitals + a doctor bound to A; asserts (a) patient create with no claim resolves server-side to A, (b) payload claiming B is overridden to A, (c) login claiming B does not widen the token tenant, (d) search pinned to A even when B requested (no B rows leak), (e/f) foreign hospital-config tree read and department write → 403 `TENANT_FORBIDDEN`, (g) platform admin keeps access to both tenants. Green locally + in CI run 43576150514.
- PIT (retained): all 43 tests green locally (BUILD SUCCESSFUL 3m 26s against the compose dev stack) and in CI. The NOT NULL constraint is exercised by every PIT journey that writes tenant-scoped data.
- Unit: existing suite green (no new unit tests — the denial matrix is inherently integration-shaped; the server-pinning logic is exercised end-to-end).
- E2E: unchanged; a11y gates green.

## 6. Accessibility
No UI change (server-side enforcement + schema only). Axe/Lighthouse/visual gates green in run 43576150514.

## 7. Issues found during build
- **Platform-admin token (`hospitalId` null) writing NOT NULL tables 500'd** — caught by PIT (7 failures → 4 product root causes, all fixed): (a) `ScribeController.create` assigned `hospitalId` **after** the repository save — the post-hoc set never flushed; `ScribeService.create` now takes `hospitalId` and builds the entity with it. (b) `PharmacyController.dispense` passed `principal.hospitalId` (null for admins) — now `pinOrDefault`. (c) `AiController` chat/command/chatStream logged `InteractionLog` with the admin's null hospital — now `pinOrDefault` at all three sites. (d) `Hl7Service.importFromAdt` had no tenant context — now attributes ADT imports to `primaryHospitalId()`.
- **Incident I-2** (ledger): V43 hand-applied to the dev DB with a placeholder `flyway_schema_history` checksum of 0 → Flyway validate rejected every later run (all 43 PIT skipped on failure-threshold) → history row corrected to the real checksum -1291971274. Rule recorded: hand-applied migrations must carry the real Flyway checksum.
- **Contract drift**: new DTO fields tripped `openApiSnapshotMatchesLiveContract`; snapshot + codegen regenerated in the same PR per the protocol.
- **Demo-script bugs** (not product): `Invoke-RestMethod -SkipHttpErrorCheck` returns the parsed body only (no `.status`/`.success`), so the 403 predicate never matched even though the denial fired correctly; fixed with `Invoke-WebRequest` + status-and-`TENANT_FORBIDDEN` assertions. Bare `$fail++` inside the Check function was function-scoped, undercounting failures; fixed to `$script:fail++`.
- **Environment**: host 8080 is occupied by an unrelated docker container (`mythic_graphql`) — the app boots with `SERVER_PORT=8081` for local/demo use (documented in the demo header; matches the 00.02/00.03 slices).

## 8. External blockers
None. (Stage-2 RLS timing is a decided ADR-13 schedule item, not a blocker.)

## 9. Deviations from chunk file
- Stage 2 (Postgres RLS) not in this section — per ADR-13 the two stages are separate changes; stage 1 lands the NOT NULL + server-side resolution + denial suite, stage 2 lands row-level policies before chunk 6. The chunk's RLS wording is intentionally unmet until then.
- MLLP ADT imports attribute to the primary hospital — the stage-1 answer for principal-less ingest; revisit if hospital context arrives on the MLLP channel.
- Pre-existing observation (out of scope, flagged): `/auth/register` is anonymous and accepts an arbitrary `roles` list — an identity-hardening candidate for a later section.

## 10. Exit-demo slice
`pwsh scripts/demo-00.04-tenant-rls.ps1` against the running app (8081, merged main): admin login → two hospitals → doctor registered into A → patient create with no claim resolves server-side to A → payload claiming B overridden to A → login claiming B does not widen → cross-tenant search denied (no B rows leak) → foreign tree read 403 TENANT_FORBIDDEN → foreign department write 403 TENANT_FORBIDDEN → admin sees both tenants. **11 passed, 0 failed**, live 2026-11-14. PIT denial suite remains green in CI run 43576150514.
