# Section 01.02 — Queue & token engine (chunk 1)

**Full build pass** on branch `feat/01.02-queue-token-engine` (off merged main `d0a76a1`). Supersedes the baseline assessment (PARTIAL → IN PROGRESS @ 886c51c): every named content of the chunk line is now implemented, integration-tested, and demoed. Local gates green at write time; CI evidence recorded after the PR opens (ledger row 61).

## 1. Scope
Per `14-CHUNK1-SECTIONS.md`: token issuance per dept/doctor, **config-driven** priority classes (emergency/VIP/assisted/appointment-order), transfer, re-print, estimated wait, QR queue ID, emergency bump < 2 s to display. The baseline tree had the token entity + lifecycle (call/start/complete/skip/postpone/no-show) + board + callNext + statistics, but priority ranks were hardcoded, the estimated-wait column was never written, and transfer/re-print/QR/bump did not exist.

## 2. Built (current tree)
Backend:
- **`receptionqueue/service/QueueService.kt`** — the engine core, extended:
  - **Config-driven priority classes**: `priorityRanks()` reads the hospital's latest ACTIVE `queue` config doc; the `priorityClasses` array order = rank (first = highest), falling back to the built-in `emergency, urgent, vip, assisted, appointment-order, routine` when absent; unknown classes rank last. Board sorting and `callNext` consume the same rank map.
  - **Estimated wait**: `estimateWaitMinutes()` = waiting-ahead count × per-queue `avgConsultMinutes` (fallback 5), populated on add and transfer — the previously dead column now has a writer.
  - **Transfer**: a waiting entry moves to another queue and gets a fresh token from the target sequence + a fresh estimate; `queue.transferred` emitted. Guard: only `waiting` entries transfer (else 409 `QUEUE_ENTRY_NOT_WAITING`).
  - **Emergency bump**: raises the entry to emergency priority and publishes `queue.bumped` (hospitalId stamped on the event; the WS bridge scopes broadcasts by it).
  - **QR queue id**: `QueueEntryView.qrPayload` = `SQM:{hospitalId}:{queueType}:{token}:{queueDate}` on every entry view (add/call/board/reprint).
- **`receptionqueue/api/QueueController.kt`** — `POST /entries/{queueType}/{tokenNumber}/transfer` and `/bump` (`queue:manage:queue`), `GET /entries/{queueType}/{tokenNumber}/reprint` (stateless, `queue:read:queue`, readOnly transaction).
- **`receptionqueue/api/Dtos.kt`** — `QueueTransferDto`, `qrPayload` on `QueueEntryView`.
- **`receptionqueue/repository/QueueEntryRepository.kt`** — token-sequence lookup made **hospital-scoped** (null-safe `:hid` @Query, `order by tokenNumber desc limit 1`), fixing a latent cross-tenant sequence bug (§7).
- **`events/EventCatalog.kt`** — `queue.called` cataloged; `queue.transferred` + `queue.bumped` added: 17 → **20** EMITTED types.
- **`schemas/config/queue.schema.json`** — `priorityClasses` array (minItems 1, uniqueItems) on queue entries.

Frontend/tooling:
- **`apps/web/scripts/lib/scan-fixtures.mjs`** — `/queue` added to SCAN_ROUTES (axe scan + VRT now cover the queue page; page itself unchanged from baseline).
- **`docs/contracts/openapi-v1.json`** regenerated + **`packages/api-client/src/generated/schema.d.ts`** stamped (snapshot sha256 `9d2c3558…`) — same PR as the surface change.
- **`scripts/demo-01.02-queue-token.ps1`** — 20-check exit demo (pwsh, app on :8081).
- **PIT** `@Nested inner class QueueEngineTests` in `PlatformIntegrationTests` (fresh hospital `QUL-$s` per run; foreign hospital `QUF-$s` + `tag = "foreign"` receptionist via the 01.01 `registerRoleUser` pattern).

## 3. Contracts delivered
- **DB**: zero new migrations — `queue.queue_entry.hospital_id` NOT NULL since V43, queue permissions (`queue:read:queue`, `queue:manage:queue`) re-landed on RECEPTIONIST in V54. V11 created the tables (pre-convention, see §9).
- **API**: `/api/v1/queue/entries/{q}/{token}/transfer` (POST), `/bump` (POST), `/reprint` (GET). Guard errors are 400 message-as-code (`QUEUE_ENTRY_NOT_FOUND`), state guards 409 (`QUEUE_ENTRY_NOT_WAITING`); cross-tenant specific-token reads/writes → 400.
- **Events**: `queue.called` (existing, now cataloged), `queue.transferred`, `queue.bumped` — outbox-dispatched, envelope v1, hospitalId-stamped; WS `/ws` bridge scopes `queue.bumped` broadcast per hospital.
- **Config contract**: `priorityClasses` added to the `queue` config schema; `avgConsultMinutes` feeds the wait estimate.
- **QR queue id**: `SQM:{hospitalId}:{queueType}:{token}:{date}` string contract on every entry view.
- **Contract artifacts**: OpenAPI snapshot + codegen stamp in the same PR; PIT `openApiSnapshotMatchesLiveContract` green.

## 4. Gates 1–12
1. PASS — all named contents implemented: config-driven priority classes (vip above emergency proves the doc wins over the built-in order), transfer with fresh target token, stateless re-print, estimated-wait population, QR queue id payload, emergency bump.
2. PASS — CI: branch run opened with the PR (id recorded in the following ledger PR, per the 01.01 flow); no `--admin`.
3. PASS — types: `npx turbo typecheck` clean (28/28 tasks, 2026-11-17; web needed a forced `build` first for `.next/types` — tooling note, not a code change).
4. PASS — lint/format: `ktlintFormat` clean; frontend lint unchanged (3 pre-existing chunk-00 warnings).
5. PASS — tests: turbo **146** unit tests (web 88, ui 48, api-client 5, schemas 5); PIT **61/61** (external mode, real compose PG), including the new `QueueEngineTests`.
6. PASS — accessibility: `/queue` now in SCAN_ROUTES → axe-scanned (zero serious/critical), joining the other five routes.
7. PASS — VRT: **7/7** against committed baselines on the production build (chromium, serial, 2% threshold); new `/queue` baseline created (`queue.png`).
8. PASS — contracts: OpenAPI snapshot + api-client stamp regenerated in the same PR; EventCatalog 20 EMITTED types asserted in PIT; schema-registry harness remains the 00.02 chunk-level note (unchanged).
9. PASS — data: no migration needed (`hospital_id` NOT NULL since V43; tenancy enforced at service layer and PIT-verified); **RLS stays the 00.04/ADR-13 chunk-level seam** (§9); V11 rollback-note gap recorded as a deviation.
10. PASS — audit: `AuditAspect` covers the new controller writes (transfer/bump are POSTs → audit rows; reprint is a read-only GET by design).
11. PASS — docs: this report; ledger row 61 + merge-log entry follow the PR in the standard flow.
12. PASS — exit demo: `scripts/demo-01.02-queue-token.ps1` **20/20** (§10).

## 5. Tests
- **PIT** (external mode): `QueueEngineTests.queueTokenEngineNamedContentsCoverage` — fresh hospital per run → config publish with `priorityClasses = [vip, emergency, routine]` → tokens C-01/C-02/C-03 with estimates 0/10/20 (avg 10) → QR payload `SQM:{hid}:consult:C-01:{date}` → board order `[C-02, C-03, C-01]` (config-driven: vip beats emergency) → callNext → C-02 called → transfer C-01 → lab **L-01** with fresh estimate → lab board shows L-01 → transferring the called C-02 → 409 → reprint L-01 stateless + QR intact → bump C-03 → emergency priority + `queue.bumped` in the outbox → **WS frame < 2000 ms** from publish to hospital-scoped WS receipt → foreign receptionist: specific-token read 400, bump 400, own board 200-empty (leak property: no foreign rows) → catalog asserts 20 EMITTED. Full suite **61/61** (see §7 for the two root-cause fixes it surfaced).
- **Frontend**: turbo 146/146 unit tests; typecheck + lint clean; `next build` green.

## 6. Accessibility
`/queue` joined SCAN_ROUTES this section, so the page is now axe-scanned (zero serious/critical) and visually baselined. No new UI controls were added to the page in this section (board + operations UI pre-existed); the new API surfaces are backend-only.

## 7. Issues found during build + fixes
- **Root-cause fix 1 — cross-tenant token sequence (latent bug in shared code)**: the first PIT run's transfer produced **L-02** for a fresh hospital. DB inspection showed the fresh hospital had a single lab row tokened L-02 and **no L-01**: `findTopByQueueTypeAndQueueDateOrderByTokenNumberDesc` had no hospital filter, so another test's same-day seed-hospital lab entry (L-01) maxed across tenants. Fixed hospital-scoped via the repo's null-safe `:hid` @Query pattern. The derived-query → @Query swap initially dropped the implicit `LIMIT 1` (multi-row `IncorrectResultSizeDataAccessException` → 500s in the check-in/consult paths caught by the next full run) — restored with explicit `limit 1` (Hibernate 6.5.3 supports it in HQL).
- **Root-cause fix 2 — `transfer()` vs Hibernate auto-flush**: computing the target token after mutating `entry.queueType` flushed the dirty entry mid-transaction, making the entry itself match the target-queue token query (its old `C-01` parsed as sequence 1 → next = L-02). Token and estimate are now computed **before** any mutation; comment in-code.
- **Fresh hospital per run**: daily token sequences would collide with leftovers in the shared seed hospital (same lesson as 01.01's lifecycle test); `QueueEngineTests` mints its own hospital `QUL-$s`/`QUF-$s`.
- **Event-catalog count drift**: the two 17-type asserts updated to 20 (QueueCalled/Transferred/Bumped EMITTED).
- **Demo script bugs, not engine bugs** (first run 17/20, API probed and confirmed correct): (a) pwsh interpolated `$h:` as a drive-qualified variable in `"SQM:$h:consult:…"` → empty; fixed with `${h}`; (b) the cross-tenant board check expected 400, but a foreign receptionist reading **their own** board is legitimately 200-empty — the leak property is "no foreign rows", corrected to assert an empty list; (c) the bump check initially targeted C-03 (already transferred context) → C-04.
- **ktlint slip caught by CI, not locally**: the first PR commit had one `multiline-expression-wrapping` violation in the new PIT test — the local gate list ran `ktlintFormat` before the PIT edits landed (run order, not a tool gap). CI run 43949877857 failed `:ktlintCheck`; `ktlintFormat` applied (commit `73d7be1`) and the re-run is the recorded branch run.
- **Infra**: Docker Desktop died mid-session (second time this chunk) — restarted, compose stack brought back up (PG healthy; Grafana :3005 bind conflict = known non-issue). One PIT attempt timed out on an orphaned test-app JVM from a killed shell; stale JVMs cleaned before the green run.

## 8. External blockers
None new. QR **image rendering** needs the kiosk (01.08) / TV display (01.06) surfaces and is delivered there; the payload contract ships here. The < 2 s bump-to-display latency is measured as publish→WS-receipt in-JVM (PIT, < 2000 ms); real-display soak remains 01.06's external hardware scope. E1/E2/E3 unchanged from the planning ledger.

## 9. Deviations from chunk file
- **QR queue ID is delivered as a payload contract, not rendered pixels** — `qrPayload` (`SQM:{hospitalId}:{queueType}:{token}:{date}`) ships on every entry view; drawing the QR image belongs to the kiosk (01.08) and TV display (01.06) sections that own those screens.
- **Bump-to-display < 2 s is PIT-measured publish→WS-receipt** (assert < 2000 ms on the hospital-scoped WS frame), not a physical-display stopwatch — real-display soak is 01.06's external hardware scope.
- **RLS not applied to queue tables** — `hospital_id` NOT NULL + service-layer tenancy (PIT-verified 400s); the row-level-security suite is the 00.04/ADR-13 chunk-level deliverable and will cover queue tables then.
- **V11 rollback note absent** — `V11__reception_queue.sql` predates the rollback-note convention; no new migration shipped in this section to re-touch it. Recorded here per the honest-verdict rule (same treatment as 01.01's V-precedent notes).
- **Guard errors map to 400/409 via the shared handler** (message-as-code), not dedicated conflict codes — chunk file does not specify status codes; consistent with 01.01.
- **Exit demo is 20 checks**, not the sketched minimum — grew during verification (RBAC trio, outbox, catalog as separate checks).

## 10. Exit-demo slice
`pwsh scripts/demo-01.02-queue-token.ps1` against the app on :8081 (`SERVER_PORT=8081 SPRING_PROFILES_ACTIVE=dev .\gradlew.bat bootRun`, compose dev stack up): admin login → two fresh hospitals + receptionists (one foreign) → patients → queue config publish with `priorityClasses = [vip, emergency, routine]` (vip above emergency proves config-driven) → tokens C-01/C-02/C-03 → estimates 0/10/20 → QR payload → config-driven board `[C-02, C-03, C-01]` → callNext → C-02 → transfer C-01 → lab L-01 → lab board → called-entry transfer 409 → re-print stateless + QR → add C-04 → bump C-03 → emergency → outbox carries `queue.*` (≥3 incl. `queue.bumped`) → cross-tenant read 400 / own-board 200-empty / cross-tenant bump 400 → catalog 3 queue events. **20 passed, 0 failed**, live 2026-11-17. PIT 61/61 recorded above; CI run id recorded in the ledger PR.
