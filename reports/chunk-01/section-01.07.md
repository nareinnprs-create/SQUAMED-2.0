# Section 01.07 — Voice announcement engine (chunk 1)

**Full build pass** on branch `feat/01.07-voice-announcements` (off merged main `ac5454b`). The chunk line — **TTS (en/hi + regional), repeat/priority rules, audio zone mapping** — is implemented end-to-end: two new queue tables (`V58`), `AnnouncementService` fan-out at call time, `announcement.enqueued` in the outbox + WS broadcast, and a dark `/announcements` display page whose speech runner speaks the payload via the browser SpeechSynthesis API. CI green on branch (run **44099053847**, 7/7 jobs; first run 44096078575 failed Integration gates on a WS-race in the new journey, fixed by `da940e3`, §7); PR #52 squash-merged as `e18525a` (no `--admin`); merge-log row in this docs PR (ledger row 66).

## 1. Scope
Per `14-CHUNK1-SECTIONS.md`: TTS (en/hi + regional), repeat/priority rules, audio zone mapping. Pilot decision for regional language #1 = **Hindi** (`languages` column is a comma-ordered list, default `en,hi`); ride-on: the existing queue call flow (`call` / `call-next` on 01.02's engine) now emits announcements; a display page consumes them.

## 2. Built (current tree)
Backend:
- **`db/migration/V58__voice_announcements.sql`** — `queue.display_zone` (audio zone bound to a counter via `service_point_id VARCHAR(50)` — the `queue_entry.service_point_id` String domain; `volume 0–100` CHECK, default 80) and `queue.announcement_rule` (`queue_type` exact-match or `'*'` fallback, `repeat_count 1–10` CHECK, `languages` comma-ordered, `zone_id` NULL = every zone). Rollback note present. Header states **zero new permission codes** (§9).
- **`receptionqueue/domain/DisplayZone.kt` + `AnnouncementRule.kt`** + repositories.
- **`receptionqueue/service/AnnouncementService.kt`** — call-time fan-out: rule resolution (exact `queue_type` match → `'*'` fallback → defaults: repeat 2, `en,hi`, every zone, volume 80, counter = queue type), zone resolution (rule `zoneId` → entry's counter's zone → null), en/hi text templates carrying the token + counter, **repeat doubled for `priority = emergency`** (capped at 10), volume from the resolved zone; `POST /api/v1/queue/announcements` manual enqueue (texts echo, default repeat 2); validations → `IllegalArgumentException` → 400 with code.
- **`receptionqueue/api/QueueController.kt` + `Dtos.kt`** — `GET/POST /api/v1/queue/zones`, `GET/POST /api/v1/queue/announcement-rules`, `POST /api/v1/queue/announcements`; all hospital-scoped (writes pin via `tenantResolver.pinOrDefault`; a foreign `zoneId` → 400).
- **`receptionqueue/service/QueueService.kt`** — `call()` / `callNext()` now call `AnnouncementService` after emitting `queue.called`; root-cause fix carried from 01.02/01.06: `servicePointName` stamped onto the `queue.called` payload via `ServicePointRepository.findFirstByHospitalIdAndCode` so displays show "Counter 1", not the code.
- **`events/EventCatalog.kt`** — +`AnnouncementEnqueued` → **22 EMITTED** types.

Frontend/tooling:
- **`apps/web/src/app/(app)/announcements/page.tsx` + `workspace.tsx`** — dark display page: zone select (from `GET /queue/zones`), **now/next** from the queue snapshot + realtime `queue.called` enrichment, announcement history, and the **speech runner**: speaks `texts` in `languages` order at `repeatCount` (× volume/100), `priority = emergency` cancels the current utterance (runId bump makes stale callbacks no-op) and jumps the queue — emergency drops routine items mid-speech (test-asserted).
- **`apps/web/src/providers/RealtimeProvider.tsx`** — exports `REALTIME_EVENT` (the `'squamed:realtime'` CustomEvent name) so the page listens without string coupling.
- **`apps/web/src/app/(app)/announcements/workspace.test.tsx`** — 6 vitest tests with a `FakeUtterance` + `speechSynthesis` stub: render, zone filtering (all-zone passes any selection, foreign dropped), repeat, emergency cancel + drop, manual utterance, history.
- **`apps/web/scripts/lib/scan-fixtures.mjs`** — `/announcements` route + `ANNOUNCEMENT_ZONES` 2-zone fixture + `**/api/v1/queue/zones` mock before the catch-all → axe + VRT cover the page; new VRT baseline `announcements.png` (VRT 11/11).
- **`docs/contracts/openapi-v1.json`** regenerated + **`packages/api-client/src/generated/schema.d.ts`** stamped (sha256 `80bdd2c5…`) — same PR as the surface change.
- **PIT** `@Test voiceAnnouncementsJourney` (QueueEngineTests) + catalog-count asserts 21→22.
- **`scripts/demo-01.07-voice-announcements.ps1`** — 28-check exit demo (pwsh, app on :8080). Rides the docs PR (untracked at feature-PR merge), §9.

## 3. Contracts delivered
- **DB**: `V58__voice_announcements.sql` — two hospital-scoped tables with rollback notes; no changes to existing tables.
- **API**: `GET/POST /api/v1/queue/zones`, `GET/POST /api/v1/queue/announcement-rules`, `POST /api/v1/queue/announcements`.
- **Events**: +`announcement.enqueued` — EventCatalog **22 EMITTED** (PIT-asserted); payload omits nulls (`zoneId`/`zoneName` absent = every zone).
- **Metrics**: none new.
- **Permissions**: **zero new codes** — zones/rules/enqueue ride `queue:manage:queue` (writes) / `queue:read:queue` (reads), stated in the V58 header.
- **Contract artifacts**: OpenAPI snapshot + codegen stamp in the same PR; PIT `openApiSnapshotMatchesLiveContract` green.

## 4. Gates 1–12
1. PASS — all named contents implemented: TTS en/hi (SpeechSynthesis runner), repeat/priority rules (rule table + emergency doubling), audio zone mapping (`display_zone` bound to counters, zone-aware payloads).
2. PASS — CI on branch: green run **44099053847** (7/7 jobs, no `--admin`); first run 44096078575 failed Integration gates — `voiceAnnouncementsJourney` WS assert `WS frame carries the called token` raced Kafka consumer lag (the first call's pre-connect `announcement.enqueued` was replayed after the WS opened, so the first matching frame carried `token1`, not `token2`); fix `da940e3` scopes the WS wait to the called token (`mustContain` param), journey re-passed locally then on CI (§7).
3. PASS — types: `npx turbo typecheck` clean (29/29 gate incl. lint+test green after the turbo.json fix, §7).
4. PASS — lint/format: `ktlintFormat` clean before push; web ESLint 0 errors.
5. PASS — tests: web vitest **94/94** (88 pre-existing + 6 new); turbo **29/29**; PIT **65/65** on CI (64 pre-existing + `voiceAnnouncementsJourney`, green in run 44099053847); coverage chain citation = CI's Integration gates job run 44099053847 (identical `integrationTest test jacocoTestReport jacocoTestCoverageVerification` command; local full-chain re-run skipped per 01.03/01.04/01.05 precedent, §9).
6. PASS — accessibility: `/announcements` in SCAN_ROUTES → axe-scanned clean: **zero serious/critical** (only the 4 pre-existing moderates on `/` and `/login`, unchanged).
7. PASS — VRT: **11/11** against committed baselines on the production build (chromium, serial, 2% threshold); new `/announcements` baseline (`announcements.png`) created via `--update-snapshots=missing` and verified in compare mode.
8. PASS — contracts: OpenAPI snapshot + api-client stamp regenerated in the same PR; EventCatalog 22 EMITTED types asserted in PIT.
9. PASS — data: V58 with rollback note; tenancy enforced (zones/rules/enqueue hospital-scoped; foreign zoneId → 400; PIT cross-tenant denials); **RLS stays the 00.04/ADR-13 chunk-level seam** (§9).
10. PASS — audit: zone/rule creation and manual enqueue are POSTs → `AuditAspect` audit rows; call-time announcement emission rides the audited `queue.called` write path.
11. PASS — docs: this report; ledger row 66 + merge-log follow the PR in the standard flow (docs PR; report drafted with "row 65" before the ledger row number was confirmed — corrected here, §7).
12. PASS — exit demo: `scripts/demo-01.07-voice-announcements.ps1` **28/28** (§10).

## 5. Tests
- **PIT**: `voiceAnnouncementsJourney` — fresh hospitals → rule (`repeat 3, en,hi`, zone-scoped) + zone (`Counter 1`, volume 90) on receptionist tokens → entry + call → outbox `announcement.enqueued` asserts: rule repeat 3, zone resolved (id + name), counter from service-point name, volume 90, `texts.en`/`texts.hi` carry the token → `queue.called` enrichment (`servicePointName`, `priority`) → **emergency call doubles repeat 3→6** → manual enqueue defaults (repeat 2, zone volume applied, texts echoed) → no-rule hospital defaults (repeat 2, en+hi, volume 80, **key-absence assert** `!payload.has("zoneId")`, counter = queue type) → WS broadcast frame delivered to the WS client → cross-tenant: foreign zone list empty, foreign rule/enqueue into our zone 400 → validation guards: zone name required, volume 150 → 400, repeat 11 → 400, blank text → 400, repeatCount 0 → 400.
- **Old tests**: catalog-count asserts updated 21→22 (~2 sites); queue tests otherwise untouched (the change is additive behind `call()`/`callNext()`).
- **Frontend**: web vitest 94/94 (6 new: render, zone filter, repeat, emergency cancel + routine-drop, manual, history) with a `FakeUtterance`/`speechSynthesis` stub; typecheck + build clean.
- **Coverage**: repo merged coverage **≥ 0.80 enforced** — the gate lives in CI's Integration gates job (identical chain, green on the branch head); local full-chain re-run skipped per precedent, the new journey passed locally standalone.

## 6. Accessibility
`/announcements` joined SCAN_ROUTES this section, so the page is axe-scanned and visually baselined. The zone select is a labelled house `Select`; now/next and history are text + badge content (token, counter, priority), not color-alone; the speech runner is driven by realtime events with a visible history trail (no-audio fallback is inherent — the page still displays everything it speaks). Gate 6 findings: zero serious/critical; only the 4 pre-existing moderates on `/` and `/login` (unchanged from earlier sections).

## 7. Issues found during build + fixes
- **TS6053 `File '.next/types/app/.../page.ts' not found` flaked the turbo gate** — root cause verified: turbo ran `@squamed/web#build` (next build wipes/regenerates `.next/types` during typegen) **in parallel** with `@squamed/web#typecheck` (tsc reading a stale file list); it only bit when `web#build` was a cache miss (a new route in this PR). Fix: `turbo.json` `typecheck.dependsOn` `["^build"]` → `["build", "^build"]` (serialize). turbo.json had no diff vs main and CI runs the same `npx turbo typecheck lint test` — CI-safe (§9).
- **Duplicate `FakeUtterance` identifier** (type alias + class in the new test) — removed the alias; direct `tsc --noEmit` caught what the parallel race had masked.
- **Emergency-speech test contradicted the design** — the test initially asserted the routine item stayed in now/next after the emergency jump; the locked design **drops** routine items mid-speech. Test corrected to expect the drop.
- **Docker Desktop daemon flapped twice** — first `bootRun` died with `Connection to localhost:5433 refused` (containers down). Recovery per runbook: relaunch Docker Desktop → `docker info` stable → `docker compose start` → `pg_isready` healthy → rebootRun. Second boot went green.
- **Demo script first run: `entry added with token` FAIL → 403 cascade** — root cause verified manually and in the app log (`Permission denied ... patient:write:patient`): **RECEPTIONIST lacks patient-create**. Fix in the demo script (not the backend): patients created via **admin** with explicit `hospitalId`; entries/rules/zones/enqueue stay on receptionist tokens. Re-run: 28/28.
- **VRT `--update-snapshots=missing` reports a failure when writing a new baseline** (informational "A snapshot doesn't exist … writing actual" + red summary) — the compare run is the gate: 11/11 green.
- **CI run 1 (44096078575 on `3e27c56`) failed Integration gates — `voiceAnnouncementsJourney` WS assert `WS frame carries the called token`** — root cause: Kafka consumer lag replays the first call's pre-connect `announcement.enqueued` after the WS session opens, so `awaitEventFrame`'s first type-match was the stale frame (carrying `token1`), not the second call's. Fix `da940e3`: `awaitEventFrame` in QueueEngineTests gained a `mustContain` param and the journey waits for the frame carrying `token2` (the redundant substring assert folded into the wait). Journey re-passed locally (Docker recovered per runbook) and CI re-ran green (44099053847, §7 = honest-record of the failure).
- **Report drafted with "ledger row 65" before the row number was confirmed** — 01.07 is ledger row **66** (row 65 is 01.06, still PLANNED); all mentions corrected in this finalization.

## 8. External blockers
None new. In-browser SpeechSynthesis covers TTS without an external voice service (§9); a cloud TTS engine (e.g. Azure neural voices) is a pilot-phase decision, same bucket as "Regional language #1 = pilot decision" in the chunk table. E1/E2/E3 unchanged from the planning ledger.

## 9. Deviations from chunk file
- **TTS engine = browser SpeechSynthesis, not a cloud voice service** — the chunk line says "TTS"; v1 ships the deterministic offline path (the display page speaks `payload.texts` via `speechSynthesis`, volumes/levels applied). A cloud neural-voice engine (higher fidelity, more languages) is deferred as a pilot decision, consistent with the chunk table's "Regional language #1 = pilot decision" risk note.
- **Regional languages: only `en` + `hi` templates exist** — `languages` is a comma-ordered column so more languages are a data/template addition, not a schema change; unknown codes are skipped by the runner.
- **No sidebar entry for `/announcements`** — the display page is a ward/TV-style screen, deliberately not added to the workspace `navItems` (adding it would churn every VRT baseline); reachable by URL. Recorded here per the no-silent-deviation rule.
- **Zone filtering is client-side** — `announcement.enqueued` is broadcast to all subscribers; the display page filters by selected zone (all-zone events pass any selection, foreign-zone events dropped). Server-side per-zone fan-out is deferred until a real multi-zone deployment needs it.
- **turbo.json change ships in this PR** — the typecheck serialization fix touches a root file previously identical to main; CI runs the same command, so the fix is CI-safe, but it is recorded as a deviation from "only 01.07 surface changes".
- **Demo script rides the docs PR, not the feature PR** — untracked when the feature PR merges (same pattern as 01.05).
- **Local full coverage-chain re-run skipped** — the chain is cited from CI's Integration gates job (identical command, green on the branch head); the new journey passed locally standalone.

## 10. Exit-demo slice
`pwsh scripts/demo-01.07-voice-announcements.ps1` against the app on :8080 (compose dev stack up): admin login → two fresh hospitals `VA Demo $s`/`VA F $s` + one receptionist each (foreign receptionist for tenancy) → service point `Counter 1` → **audio zone** (`Main Display`, bound to SP1, volume 90) → **rule** (repeat 3, `en,hi`, zone-scoped) → patient + entry (patients via admin, §7) → **call** → outbox `announcement.enqueued` (source `announcement-service`, entity `queue-entry`) with rule repeat 3, zone id+name, counter "Counter 1", volume 90, en/hi texts carrying the token → `queue.called` carries `servicePointName` + `priority` → **emergency call doubles repeat 3→6** → manual enqueue (default repeat 2, zone volume, texts echoed) → no-rule hospital defaults (repeat 2, en+hi, volume 80, **no zoneId key**, counter = queue type) → tenant isolation (foreign zone list empty; foreign rule/enqueue into our zone 400 ×2) → validation guards ×5 (zone name, volume 150, repeat 11, blank text, repeatCount 0). **28 passed, 0 failed**, live 2026-11-17.

**Merge record**: PR #52 squash-merged as `e18525a` after green CI (run 44099053847, 7/7 jobs, no `--admin`), 2026-11-17; prior main was `ac5454b`.
