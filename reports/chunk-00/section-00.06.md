# Section 00.06 — Master data

## 1. Scope
Service catalog (the six V6 clinical catalogs were baseline-covered), price lists, vendor registry, equipment registry, employee master (demographic + credential fields). The baseline report (run 43548016617) FAILED gates 1/8/9 with the named gap: vendor/equipment/employee registries and price lists did not exist. This section closes that named gap: 4 hospital-scoped registries + per-hospital price lists over the global charge-item catalog, on the 00.04 tenancy primitives.

## 2. Built (current tree)
- Baseline half (retained): `masterdata/domain/{Drug,LabTest,ImagingProcedure,DiagnosisCode,ProcedureCode,ChargeItem}.kt` (6 global catalogs), 6 repositories, `MasterDataController.kt` (582 lines) + `MasterDataDtos.kt` (filter/search/active + pagination), `V6__master_data.sql` + V8 permission seed, web master-data page (rebuilt by PR #8).
- **`V45__master_data_registries.sql`** — 5 tables in `master_data`: `vendor`, `equipment`, `employee`, `price_list`, `price_list_item`. All hospital-scoped per ADR-13 stage 1: `hospital_id NOT NULL` FK → `hospital_config.hospital`, `UNIQUE(hospital_id, code)` per-hospital code uniqueness, indexes on `hospital_id`; `equipment.vendor_id` FK, `price_list_item` FKs to `price_list` (CASCADE) + global `charge_item`; audit columns per the V2 pattern; rollback note in header. Header also documents the tenancy decision: the six V6 catalogs stay deliberately **global reference sets** (same class as `ai.model_config` in V43's nullable list) — per-hospital pricing is carried by `price_list`, not by copying catalogs per tenant. No V43 retrofit of the V6 tables.
- 5 entities (`Vendor`, `Equipment`, `Employee`, `PriceList`, `PriceListItem` — all `BaseEntity`) + 5 repositories (pinned list / search / active variants + `findByHospitalIdAndCode` for the duplicate pre-check; `PriceListItemRepository.findByPriceListId` + `findByPriceListIdAndChargeItemId`).
- **`MasterDataRegistryController.kt`** (standalone, keeps MasterDataController untouched) + `MasterDataRegistryDtos.kt`: full list/search/active pagination, create, PUT update, PATCH active for all 4 registries; price-list items GET/POST. Permission reuse: `master-data:read:catalog` / `master-data:write:catalog` (no seed changes; only SYSTEM_ADMIN holds them). Audit via the generic `AuditAspect` pointcut (`*Controller`) — zero new code.
  - Writes pin tenant server-side: `tenantResolver.pinOrDefault(principal, dto.hospitalId)`.
  - Updates/toggles/items validate ownership via `requireSameHospital` → `AccessDeniedException("TENANT_FORBIDDEN")` (403) on foreign-hospital rows; lists always pin the admin to an explicit `?hospitalId=` or the primary hospital (no unfiltered global list).
  - Duplicate codes pre-checked in the controller → 400 `CODE_DUPLICATE` (matches the IllegalArgumentException→400 handler) instead of a constraint-violation 500.
  - Price-list items are **upsert**: re-posting a `(priceListId, chargeItemId)` pair re-prices instead of failing.
- Contract: `docs/contracts/openapi-v1.json` snapshot (282,436 bytes) + stamped `packages/api-client/src/generated/schema.d.ts` regenerated in the same PR (sha256 `dd282b0a195b8f75…`) per `docs/development/contract-codegen.md`; all 4 new endpoint groups verified present.
- Web: 4 new spec-driven `CATALOGS` entries (vendors, equipment, employees, price-lists) reuse the generic tab/search/create/toggle machinery on the master-data workspace page; `CatalogDto` widened with the registry fields; employee rows derive the display name from firstName+lastName. `master-data.test.tsx` → **16 tests**.
- `scripts/demo-00.06-master-data.ps1` — exit-demo slice (10 checks, pwsh, port 8081).

## 3. Contracts delivered
- `/api/v1/master-data/vendors` — GET (page/search/active, hospital-pinned) / POST / PUT `/{id}` / PATCH `/{id}/active`.
- `/api/v1/master-data/equipment` — GET / POST / PUT `/{id}` / PATCH `/{id}/active` (vendor link, serial, location, purchase/warranty dates).
- `/api/v1/master-data/employees` — GET / POST / PUT `/{id}` / PATCH `/{id}/active` (demographics + license credentials).
- `/api/v1/master-data/price-lists` — GET / POST / PUT `/{id}` / PATCH `/{id}/active`; `/price-lists/{id}/items` — GET / POST (upsert re-pricing).
- Snapshot + stamped codegen landed together; `openApiSnapshotMatchesLiveContract` green (drift would fail the integration gate).

## 4. Gates
1. PASS — zero real stubs; zero-stub grep job green (run 43625529098).
2. PASS — run 43625529098 (PR #18, merge c35061d; no `--admin`).
3. PASS — Detect source trees job green (5s).
4. PASS — ktlint + unit + ArchUnit (Backend gates job, 2m1s) green; local `ktlintFormat test` BUILD SUCCESSFUL.
5. PASS — Backend gates job (unit suite) green.
6. PASS — Accessibility + performance gates (axe + Lighthouse + visual, 2m22s) green; master-data page (4 new tabs) in axe scope.
7. PASS — Frontend gates (typecheck + lint + test, 1m52s) green; stamped `schema.test.ts` pins the snapshot hash; web vitest 16/16 local.
8. PASS — Integration gates job green (5m24s): all **45** PIT (44 retained + 1 new `masterDataRegistriesCoverage`) + merged-coverage ≥ 0.80; local PIT 45/45 BUILD SUCCESSFUL (5m53s).
9. PASS — V45 applied (bootRun Flyway log: "Successfully validated 45 migrations… Current version of schema public: 45"); new tables hospital-scoped (`hospital_id NOT NULL` + per-hospital uniqueness), cross-hospital isolation asserted in PIT; residual: stage-2 Postgres RLS remains its own ADR-13 item before chunk 6 (tracked since 00.04).
10. PASS — audit surface: `AuditAspect` pointcut covers `MasterDataRegistryController` (`execution(* com.squamed.core..*Controller.*(..))`), so every registry write writes an audit row; integration gates green.
11. PASS — this report; ledger row 39 + merge-log #18 + totals updated in the same docs batch.
12. PASS — scripted live slice `scripts/demo-00.06-master-data.ps1`: **10/10 on 8081** (merged main c35061d; pwsh; header documents `SERVER_PORT=8081 bootRun`), 2026-11-15; first proven 10/10 pre-merge on the branch.

## 5. Tests
- PIT (new): `CoverageTests.masterDataRegistriesCoverage` — fresh hospitals per run (RA-$s/RB-$s; dev DB persists rows): vendor pinned to the requested hospital (`hospitalId` echo-asserted), duplicate vendor code → 400, vendor update, equipment→vendor link persist + active toggle, employee demographics (`dateOfBirth`) + credentials (`licenseNumber`) persist + update + search by name, charge item → price list → item upsert re-price (`compareTo` 90.0), h1-filtered vendor list returns only h1 rows, h2 list contains no h1 rows, hospital-filtered equipment/employees/price-lists 2xx.
- PIT (retained): all 45 tests green locally (BUILD SUCCESSFUL 5m53s against the compose dev stack) and in CI run 43625529098.
- Web: `master-data.test.tsx` 16/16 (2 new: registry specs present with required-field derivation; employee display name derivation).
- API-client: 5/5 (stamped snapshot test).

## 6. Accessibility
Master-data workspace page in axe gate scope (4 new registry tabs render through the same shared components as the 6 existing tabs); a11y gate green in run 43625529098 (zero critical/serious).

## 7. Issues found during build
- **Contract drift first PIT run**: `openApiSnapshotMatchesLiveContract` failed until the snapshot + stamped codegen were regenerated — expected on contract change; fixed in-PR per contract-codegen protocol.
- **Port 2575 MLLP contention**: rerunning PIT while the 8081 snapshot app was alive failed only `aiGatewayAndInteropJourney` ("MLLP listener running" — HL7 MLLP binds 127.0.0.1:2575). Third run with the app killed was fully green. Standing rule (now documented): never run PIT while a bootRun app is alive.
- **No-op tenant check caught in self-review**: the initial update/toggle handlers called `pinOrDefault(principal, entity.hospitalId)` without comparing the result — a hospital-bound user could have mutated another hospital's row. Fixed with `requireSameHospital` (403 `TENANT_FORBIDDEN` on mismatch) across all 12 update/toggle/item routes before commit.
- **Snapshot fetch timing**: springdoc OpenAPI generation takes ~9s after first request; the api-docs poll uses a 120s deadline and the fetch a 30s timeout.

## 8. External blockers
None.

## 9. Deviations from chunk file
- **Tenancy decision**: the 4 new registries are hospital-scoped (`hospital_id NOT NULL` per ADR-13 stage 1), while the six existing V6 catalogs stay **global reference sets** (documented in the V45 header): clinical catalogs (drug, lab test, imaging, procedure/diagnosis codes, charge item) are platform-wide reference data; per-hospital pricing variation is carried by `price_list` over the global charge items. No V43-style retrofit of the V6 tables.
- **Price-list items are API-only in this section**: the UI covers the 4 registry tabs (create/update/toggle/browse) but there is no items editor on the price-lists tab — pricing rows are managed through the REST surface (`POST /price-lists/{id}/items` upsert) and covered by PIT + demo.
- Patient-count staffing etc. not in scope: employee master covers demographics + license credentials; no shift/scheduling linkage (scheduling is 01.01+).

## 10. Exit-demo slice
`pwsh scripts/demo-00.06-master-data.ps1` against the running app (8081, merged main c35061d): admin login → fresh hospital → vendor created hospital-pinned (echo-assert) → duplicate vendor code 400 → vendor update → equipment linked to vendor → employee demographics+credentials persist → employee search by name → charge item + price list + item created (85.5) → second hospital sees zero first-hospital vendors (cross-hospital isolation). **10 passed, 0 failed**, live 2026-11-15. PIT suite remains green in CI run 43625529098.
