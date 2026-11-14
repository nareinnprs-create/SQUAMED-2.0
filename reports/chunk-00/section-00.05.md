# Section 00.05 — Org tree & config engine

## 1. Scope
Two halves: (a) **org-tree location structure** (campus→building→ward→room→bed hierarchy), config CRUD APIs, and audit on config change — delivered in the pre-gate-run build (V4 + entities/repositories/controller + generic AuditAspect interception + web workspace page) and covered by the 00.05 gate-run row; (b) **config documents as versioned, validated JSONB** ("configure, don't fork") — the named gap that made the original 00.05 report FAIL: one document per hospital per type, append-only version history, server-side JSON-Schema validation, and a `config.changed` domain event on every publish. This section closes (b) on top of (a).

## 2. Built (current tree)
- Org-tree half (pre-existing, retained): `hospitalconfig/domain/{Hospital,Campus,Building,Ward,Room,Bed,Department,Specialty,HospitalService}.kt`, 9 repositories, `HospitalConfigController.kt` + `HospitalConfigDtos.kt` (tree CRUD + validation branches), migration `V4__hospital_config.sql` (+ V5 permission seed), `AuditAspect` generic audit trail, `apps/web` hospital-config workspace page.
- **`V44__config_document.sql`** — `hospital_config.config_document`: `hospital_id` NOT NULL FK (00.04 guarantees attribution), `type` VARCHAR(50), `doc_version` INTEGER, `content` JSONB NOT NULL, `status` (`active`/`superseded`), `created/updated` audit columns + optimistic-lock `version`; unique `(hospital_id, type, doc_version)` + `(hospital_id, type, status)` lookup index. Header documents the append-only semantics and rollback.
- `ConfigDocument.kt` — `HospitalScopedEntity`; the business version is `documentVersion` (doc_version column) so it never collides with `BaseEntity.version` (the `@Version` optimistic-lock column); content carried as Jackson `JsonNode` via `@JdbcTypeCode(SqlTypes.JSON)`.
- `ConfigDocumentRepository.kt` — 5 derived queries (latest by version, full history, active-only latest, actives list, at-version).
- `ConfigDocumentService.kt` — `publish()`: reject unknown type (`UNKNOWN_CONFIG_TYPE`), reject missing hospital, validate content against the per-type JSON Schema via `SchemaRegistry` (`CONFIG_VALIDATION_FAILED: <violations>`), supersede all active rows, save the next `doc_version`, publish `config.changed` DomainEvent (payload `type` + `version`) to `squamed.domain-events`. `latest()` returns the active row; `versions()`/`atVersion()` read the history. All reads `readOnly`.
- `ConfigDocumentController.kt` — 4 routes nested under `/api/v1/hospital-config/hospitals/{hospitalId}/config/...`: `PUT /config/{type}` (publish), `GET /config/{type}` (latest active), `GET /config/{type}/versions`, `GET /config/{type}/versions/{version}`. Nesting under `/hospitals/{hospitalId}` means **TenantInterceptor (00.04) 403s foreign-hospital access for free**, the seeded `hospital-config:read/write:hospital` permissions gate it unchanged, and `AuditAspect` (pointcut `execution(* com.squamed.core..*Controller.*(..))`) audits every config write with zero new code.
- **Six JSON Schemas** (draft 2020-12, absolute `$id` `https://squamed.local/schemas/config/<type>.schema.json`, `additionalProperties: false`) under `src/main/resources/schemas/config/`: `working-hours` (tz + per-day open/close, clock pattern `([01][0-9]|2[0-3]):[0-5][0-9]`), `holidays` (year + dated entries, date pattern `^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$`), `shifts` (named start/end, crossesMidnight), `workflow` (code/name/ordered steps with role + slaMinutes), `queue` (code/name/strategy fifo|priority, avgConsultMinutes), `approval` (rules with approverRole + thresholdAmount).
- `SchemaRegistry` (00.02 event-envelope harness) is now a Spring `@Component` — reused as the config validation engine; manual construction in existing tests remains valid.
- IllegalArgumentException → 400 via the existing GlobalExceptionHandler (same `errorResponsesFollowContract` shape).
- Contract: OpenAPI snapshot regenerated from live `/api-docs` + `@squamed/api-client` codegen refreshed and stamped in the same PR (sha256 `8ae6a19a7fb906daf1e57ee5bb9bba58032cf239b181d71b90bda47bd5a8b26d`).
- `scripts/demo-00.05-config-engine.ps1` — exit-demo slice (12 checks, pwsh, port 8081).

## 3. Contracts delivered
- `PUT /api/v1/hospital-config/hospitals/{hospitalId}/config/{type}` — publish next version of a config document (6 known types; 400 on unknown type / invalid content / missing hospital).
- `GET /api/v1/hospital-config/hospitals/{hospitalId}/config/{type}` — latest **active** version (400 `CONFIG_DOCUMENT_NOT_FOUND` if none).
- `GET /api/v1/hospital-config/hospitals/{hospitalId}/config/{type}/versions` — full version history, newest first (status per row).
- `GET /api/v1/hospital-config/hospitals/{hospitalId}/config/{type}/versions/{version}` — exact version readback (400 when absent).
- Snapshot + stamped codegen landed together per `docs/development/contract-codegen.md`; `openApiSnapshotMatchesLiveContract` green (drift would fail the integration gate).

## 4. Gates
1. PASS — zero real stubs (zero-stub grep over the new code: no matches; the one `shortcut:` comment documents the concurrent-publish race left to the DB unique constraint, not a stub); Zero-stub grep job green (run 43579913260).
2. PASS — run 43579913260 (PR #17, merge d0d852b; no `--admin`).
3. PASS — Detect source trees job green (4s).
4. PASS — ktlint + unit + ArchUnit (Backend gates job, 1m44s) green; local `test ktlintFormat` BUILD SUCCESSFUL.
5. PASS — Backend gates job (unit suite) green.
6. PASS — Accessibility + performance gates (axe + Lighthouse + visual, 3m13s) green; no UI added.
7. PASS — Frontend gates (typecheck + lint + test, 2m45s) green; stamped `schema.test.ts` pins the snapshot hash.
8. PASS — Integration gates job green (4m20s): all **44** PIT (43 retained + 1 new) + merged-coverage ≥ 0.80.
9. PASS — V44 applied (bootRun log: "Current version of schema public: 44"); PIT `configDocumentLifecycleCoverage` green; tenant denial suite extended with the foreign config PUT 403 and green. No residual from 00.04 (stage-2 RLS remains its own ADR-13 item, tracked in 00.04's report).
10. PASS — audit surface: `AuditAspect` pointcut covers `ConfigDocumentController` (verified: `execution(* com.squamed.core..*Controller.*(..))`), so every publish writes an audit row; integration gates green.
11. PASS — this report; ledger row 38 + merge-log #17 + totals updated in the same docs batch.
12. PASS — scripted live slice `scripts/demo-00.05-config-engine.ps1`: **12/12 on 8081** (merged main d0d852b; pwsh; header documents `SERVER_PORT=8081 bootRun`), 2026-11-14; `config.changed` verified on the wire via `kafka-console-consumer.sh` AND consumed back by `DomainEventListener` (bootRun log: "Received domain event type=config.changed" ×6).

## 5. Tests
- PIT (new): `CoverageTests.configDocumentLifecycleCoverage` — runs against a **fresh hospital created per run** (deterministic version counters): unknown type → 400; schema-invalid working-hours (`day: funday`, `25:99`) → 400 with `CONFIG_VALIDATION_FAILED` in the body; valid publish → v1 active; second publish → v2; latest = v2 with v2 content; history = exactly 2 rows; v1 readback status = superseded; v99 → 400; holidays `2026-13-40` → 400 (date-pattern rejection); valid holiday publish → v1.
- PIT (extended): `TenantIsolationTests.crossTenantAccessIsDeniedAndServerPinsTenant` — foreign hospital config PUT now asserted 403 alongside the department-write denial (TenantInterceptor coverage of the new routes).
- PIT (retained): all 44 tests green locally (BUILD SUCCESSFUL 4m 12s against the compose dev stack) and in CI run 43579913260.
- Unit: existing suite green (`ktlintFormat test`); `SchemaRegistry` promotion to `@Component` keeps the manual-construction tests valid.
- Live Kafka round-trip: publish → `config.changed` on `squamed.domain-events` → consumed by `DomainEventListener` (bootRun log evidence).

## 6. Accessibility
No UI change (backend + contract only). Axe/Lighthouse/visual gates green in run 43579913260.

## 7. Issues found during build
- **networknt 1.5.8 rejects relative `$id`**: `config/working-hours.schema.json is not a valid $id` surfaced as a 500 (schema load failure) instead of 4xx; all six schemas now carry absolute `$id`s (`https://squamed.local/schemas/...`), matching the envelope schema convention. Caught by my own PIT assert (500 ≠ 4xx).
- **Holidays date pattern too loose**: `^[0-9]{4}-[0-9]{2}-[0-9]{2}$` accepted `2026-13-40`; tightened to month `0[1-9]|1[0-2]` and day `0[1-9]|[12][0-9]|3[01]`. (Leap-year false positives like `2026-02-30` remain possible in a pure regex — acceptable for v1; server consumers treat the calendar as data, not as a schedule solver.)
- **Version-counter drift in the lifecycle test**: initially used the shared `ensureSeed` hospital; repeated runs accumulated rows ("expected 1 but was 3"). Fixed by creating a fresh hospital per run — the same determinism pattern the 00.04 tenant suite uses. Dev-DB persistence is a standing property: any test asserting absolute versions must own its hospital.
- **Gradle task-name trap**: `PIT` is ambiguous in this project (candidates: `prepareKotlinBuildScriptModel`, `processIntegrationTestResources`); the integration suite task is `integrationTest` — run it by exact name.
- **Demo-script bug** (not product): `squamed-kafka` is not a bitnami image — `kafka-console-consumer` is not on PATH (`/opt/kafka/bin/kafka-console-consumer.sh` is); `2>$null` swallowed the exec failure into a false FAIL. Fixed the path and widened the scan to 2000 messages; re-ran clean 12/12.
- **Snapshot regen environment**: host 8080 occupied by an unrelated container (`mythic_graphql`) — app boots with `SERVER_PORT=8081` for snapshot/demo use (documented in the script header).

## 8. External blockers
None.

## 9. Deviations from chunk file
- The org-tree half was already standing before this section's build PR; this section's delta is the config-document engine (the named FAIL gap), not a rebuild of the tree.
- Concurrent publishes of the same (hospital, type) race on the `doc_version` allocation: the DB unique constraint makes the loser fail loudly; no retry/queueing — split out if concurrent config editing ever becomes a real workflow (`shortcut:` in code names this).
- No dedicated config-document UI in this section — the deliverable is the versioned REST + validation + event surface; the existing workspace page covers the org-tree half.

## 10. Exit-demo slice
`pwsh scripts/demo-00.05-config-engine.ps1` against the running app (8081, merged main d0d852b): admin login → fresh hospital → unknown config type 400 `UNKNOWN_CONFIG_TYPE` → schema-invalid working-hours 400 `CONFIG_VALIDATION_FAILED` → publish v1 (active) → publish v2 → latest is v2 with v2 content → version history (2 rows, v1 superseded) → missing version 400 → invalid holiday date 400 → valid holiday publish v1 → `config.changed` present on `squamed.domain-events` (Kafka). **12 passed, 0 failed**, live 2026-11-14. PIT suite remains green in CI run 43579913260.
