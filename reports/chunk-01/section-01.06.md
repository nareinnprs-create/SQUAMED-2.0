# Section 01.06 — TV display app (chunk 1)

**Full build pass** on branch `feat/01.06-tv-display` (off merged main `2f4842d`). The chunk line — **counter-wise display: current/next tokens, offline mode, reconnect banner** — is implemented end-to-end: a whole-hospital `GET /api/v1/queue/display-board` endpoint that groups today's live queue entries per counter (now-serving + priority-ranked next list, department names resolved server-side, patient identifiers never serialized), and a dark `/display` TV page with cache-first paint from localStorage, an offline banner, and refetch on realtime reconnect / `queue.*` events / 60 s poll. CI green on branch (run **44130030493**); PR #54 squash-merged as **30d83c1** (no `--admin`); merge-log row in the docs PR (ledger row 65).

## 1. Scope
Per `14-CHUNK1-SECTIONS.md`: counter-wise display — current/next tokens, offline mode, reconnect banner. Ride-on: the 01.02 queue engine (statuses, priority ranks, entry counter snapshots) and the 01.07 realtime bridge (`useRealtime`, `REALTIME_EVENT`) — no new infrastructure.

## 2. Built (current tree)
Backend:
- **`receptionqueue/repository/QueueEntryRepository.kt`** — `findByHospitalIdAndStatusInAndQueueDate` (null-tolerant `(:hid is null or …)`, statuses + today filter, **no queueType filter** — the whole-hospital sweep).
- **`receptionqueue/service/QueueService.kt`** — `displayBoard(hospitalId)`: entries across every queue type for today, `servicePointId != null` grouped per counter (**unassigned entries omitted**), `current` = the called/in-progress entry with the max `calledAt`, `next` = waiting/postponed entries sorted by **per-queueType priority ranks** (config-driven `priorityRanks`, fallback `rankMaps[queueType]?.size ?: Int.MAX_VALUE`, then `createdAt`) capped at `DISPLAY_NEXT_CAP = 5`; counter name from the entry's `servicePointName` snapshot (falls back to the id); **department names resolved server-side** via `DepartmentRepository.findByHospitalId` (cross-module injection is house style — `QueueService` already injects `ConfigDocumentService`) so receptionist tokens get dept headers without holding `hospital-config:read:department`; counters sorted by name; **patient names/ids never leave the service** (public screens).
- **`receptionqueue/api/Dtos.kt` + `QueueController.kt`** — `DisplayTokenView(tokenNumber, queueType, priority, calledAt)`, `CounterBoardView(servicePointId, servicePointName, departmentId, departmentName, current, next)`, `DisplayBoardView(generatedAt, counters)`; `GET /api/v1/queue/display-board` with `@RequiresPermission("queue:read:queue")` readOnly, hospital pinned from the principal.

Frontend/tooling:
- **`apps/web/src/app/(app)/display/page.tsx` + `workspace.tsx`** — dark TV page (`PageHeader` + per-counter cards: counter name, department name, **NOW SERVING** token, emergency marker via house `StatusIndicator`, **NEXT** list with priorities); `StatusIndicator` header state (`Live` / `Reconnecting…` / `Offline — showing last board`). **Offline mode**: localStorage cache `sq.display-board.v1` (`{savedAt, board}`) — cache-first paint, cache kept fresh on every successful fetch, fetch failure keeps the board and shows the **offline/reconnect banner** with the saved-at time. **Reconnect**: refetch on mount, on realtime reconnect (`useRealtime().connected` false→true), on any `queue.*` realtime event (`REALTIME_EVENT` listener), and a 60 s safety poll.
- **`apps/web/src/app/(app)/display/workspace.test.tsx`** — 8 vitest tests: `queue.*`-frame detection, cache round-trip + malformed-entry tolerance, grouped render (dept name, priority order, emergency marker), empty-board state, offline banner + cache-first paint, reconnect message, refetch on reconnect, refetch on `queue.called` only.
- **`apps/web/src/test-support/react-dom-test.tsx`** — `Mounted.rerender` helper (3 lines) for the reconnect transition test.
- **`apps/web/scripts/lib/scan-fixtures.mjs`** — `/display` route + `DISPLAY_BOARD` 2-counter fixture + `**/api/v1/queue/display-board` mock → axe + VRT cover the page; new VRT baseline `display.png` (VRT 12/12).
- **`docs/contracts/openapi-v1.json`** regenerated + **`packages/api-client/src/generated/schema.d.ts`** stamped (sha256 `298bf787…`) — same PR as the surface change.
- **PIT** `@Test tvDisplayBoardJourney` (QueueEngineTests).
- **`scripts/demo-01.06-tv-display.ps1`** — 20-check exit demo (pwsh, app on :8080). Rides the docs PR (untracked at feature-PR merge), §9.

## 3. Contracts delivered
- **DB**: none — the board reads existing `queue.queue_entry` rows (counter/dept snapshots stamped at entry creation since 01.02/01.07); no migration in this section.
- **API**: `GET /api/v1/queue/display-board` (hospital-scoped from the principal; no queueType/counter params — one payload serves the whole waiting room).
- **Events**: none new — EventCatalog stays **22 EMITTED** (PIT-asserted); the display refetches on the existing `queue.*` events.
- **Metrics**: none new.
- **Permissions**: **zero new codes** — the board rides `queue:read:queue` (held by RECEPTIONIST since V54).
- **Privacy**: the display payload has no patient names/ids (DTO shape lacks the fields; PIT asserts key absence + id absence).
- **Contract artifacts**: OpenAPI snapshot + codegen stamp in the same PR; PIT `openApiSnapshotMatchesLiveContract` green.

## 4. Gates 1–12
1. PASS — all named contents implemented: counter-wise display (current/next per counter), offline mode (localStorage cache + banner), reconnect banner (fetch-failure banner + reconnect refetch).
2. PASS — CI on branch: green run **44130030493** (7/7 jobs, no `--admin`) — single first-run green, no retries.
3. PASS — types: `npx turbo typecheck lint` clean (9/9 tasks green).
4. PASS — lint/format: `ktlintFormat` clean before push; web ESLint 0 errors.
5. PASS — tests: web vitest **102/102** (94 pre-existing + 8 new); turbo 9/9; PIT **66/66** on CI (first-run green; `tvDisplayBoardJourney` PASSED; green locally standalone); coverage chain citation = CI's Integration gates job run 44130030493 (identical `integrationTest test jacocoTestReport jacocoTestCoverageVerification` command; local full-chain re-run skipped per 01.03–01.07 precedent, §9).
6. PASS — accessibility: `/display` in SCAN_ROUTES → axe-scanned clean: **zero serious/critical** (only the 4 pre-existing moderates on `/` and `/login`, unchanged). One contrast finding fixed during the build (§7).
7. PASS — VRT: **12/12** against committed baselines on the production build (chromium, serial, 2% threshold); new `/display` baseline (`display.png`) created via `--update-snapshots=missing` and verified in compare mode.
8. PASS — contracts: OpenAPI snapshot + api-client stamp regenerated in the same PR; EventCatalog 22 EMITTED types asserted in PIT.
9. PASS — data: no schema change; tenancy enforced (board is hospital-pinned from the principal; PIT cross-tenant board empty); **RLS stays the 00.04/ADR-13 chunk-level seam** (§9).
10. PASS — audit: the board is a read-only GET (no audit row required — nothing mutates); all displayed state rides the already-audited queue write paths.
11. PASS — docs: this report; ledger row 65 + merge-log follow the PR in the standard flow (docs PR).
12. PASS — exit demo: `scripts/demo-01.06-tv-display.ps1` **20/20** (§10).

## 5. Tests
- **PIT**: `tvDisplayBoardJourney` — fresh hospitals → department (admin; RECEPTIONIST holds no hospital-config write/read) → two counters on receptionist tokens → five tokens (routine + urgent + emergency on Counter 1, routine on Counter 2, one **unassigned**) → `call-next` serves the **emergency token first** → `GET /display-board`: exactly 2 counters (unassigned omitted), sorted by name, dept name/id resolved server-side, Counter 1 `current` = called emergency token, `next` = [urgent, routine] (**priority-ranked**), Counter 2 `next` = its token with no `current` → **privacy asserts**: no `patientName`/`patientId` keys on any token view, no patient ids anywhere in the payload, unassigned token absent entirely → cross-tenant: foreign display sees an empty board.
- **Old tests**: none touched (the change is additive: one read endpoint + one repo method; `board()` and the call flows are untouched).
- **Frontend**: web vitest 102/102 (8 new, listed in §2); typecheck + lint + build clean; prod build rebuilt **uncached** before the axe re-scan (§7).
- **Coverage**: repo merged coverage **≥ 0.80 enforced** — the gate lives in CI's Integration gates job (identical chain, green on the branch head); local full-chain re-run skipped per precedent, the new journey passed locally standalone.

## 6. Accessibility
`/display` joined SCAN_ROUTES this section, so the page is axe-scanned and visually baselined. The board is text-forward (token numbers, counter and department names as text, priorities as text next to tokens — not color-alone; the emergency state uses the house `StatusIndicator` component, whose colors pass axe). The offline/reconnect state is announced in the banner text, not color alone. Gate 6 finding during the build: one serious `color-contrast` on the first-pass emergency label — fixed (§7); final scan: zero serious/critical, 4 pre-existing moderates on `/` and `/login` unchanged.

## 7. Issues found during build + fixes
- **PIT first run NPE: `called["servicePointId"]` is null** — the `call-next` response is a `BoardEntry`, which does not serialize `servicePointId` (the counter stamp lives on the entity). Fix in the test (not the API): the counter stamp is asserted via the board (`current` on Counter 1 = the called token) — the response shape is unchanged. Journey re-passed.
- **Docker Desktop daemon flapped twice during this build** (Linux engine pipe vanished mid-build; the first `bootRun` died against a down Postgres — HikariPool retry loop, app exited). Recovery per runbook both times: relaunch Docker Desktop → `docker info` stable → `docker compose start` → `pg_isready` healthy → rebootRun. Second boot green; integration journey + app demo ran against the recovered stack.
- **Axe serious `color-contrast` on `/display`** — the emergency "NOW SERVING" marker used `color: var(--sq-danger, #ef4444)`; `--sq-danger` is **not a theme token** (the house token is `--sq-critical`), so the raw `#ef4444` fallback rendered at 4.4:1 on the dark panel. Fix: replaced the raw colored div with the house `StatusIndicator tone="critical"` (theme-approved, axe-safe). Prod build rebuilt uncached before the re-scan per the standing rule; re-scan clean; the 12/12 compare run verified the new look within the 2% VRT threshold (no baseline churn).
- **VRT `--update-snapshots=missing` reports a false failure when writing the new baseline** (known wart from 01.07) — the compare run is the gate: 12/12 green.
- **No demo-script failures** — 20/20 on the first run against the recovered stack (patients via admin per the 01.07 lesson; board asserts mirror the PIT).

## 8. External blockers
None new. The **24 h hardware soak on a real Android TV box is owed to the hardware lab (E10) at chunk exit (01.12)** — the E10 purchase list (TV box, thermal printer, kiosk tablet, barcode scanner; `23-BUSINESS-OPS-CHECKLIST.md`) is bound to the chunk-6 procurement window, so v1 ships the web display with the offline/reconnect behavior, and the soak is recorded as owed (§9). E1–E4 unchanged from the planning ledger.

## 9. Deviations from chunk file
- **v1 = web display app; the Android TV hardware soak is deferred** — the chunk line says "TV display app"; v1 ships the `/display` web page (fullscreen-able, offline + reconnect) and the display-board API. The 24 h soak on real TV hardware is explicitly recorded as **owed to E10 / chunk exit (01.12)** — the hardware lab does not exist yet (purchase list bound to the chunk-6 procurement window).
- **No sidebar entry for `/display`** — the display page is a waiting-room TV screen, deliberately not added to the workspace `navItems` (adding it would churn every VRT baseline); reachable by URL. Recorded here per the no-silent-deviation rule.
- **Unassigned entries are omitted from the board** — a token without a counter snapshot has no place to render; the counter assignment happens at entry creation (reception workspace flow). If a future flow creates unassigned tokens that must display, they will need a "general" counter or an unassigned tray (deferred until a deployment needs it).
- **Next list capped at 5** (`DISPLAY_NEXT_CAP`) — longer queues show the first 5 by priority; a scrollable full list is a v2 display decision.
- **Offline cache is device-local localStorage** — per-TV state, not server sync; a TV that never reaches the API after first load shows the last fetched board with the saved-at time.
- **Demo script rides the docs PR, not the feature PR** — untracked when the feature PR merges (same pattern as 01.05/01.07).
- **Local full coverage-chain re-run skipped** — the chain is cited from CI's Integration gates job (identical command, green on the branch head); the new journey passed locally standalone.

## 10. Exit-demo slice
`pwsh scripts/demo-01.06-tv-display.ps1` against the app on :8080 (compose dev stack up): admin login → two fresh hospitals `TV Demo $s`/`TV F $s` + one receptionist each (foreign receptionist for tenancy) → department (admin) → two counters → five tokens (routine/urgent/emergency on Counter 1, routine on Counter 2, unassigned; patients via admin) → **`call-next` serves the emergency token first** → `GET /display-board` on the receptionist token: 2 counters (unassigned omitted), name-sorted, **department name resolved server-side**, Counter 1 current = emergency token, next priority-ranked (urgent before routine), Counter 2 next = its token with no current → **privacy**: no patient names/ids anywhere in the payload, unassigned token absent → cross-tenant: foreign board empty → **auth: board without token 401**. **20 passed, 0 failed**, live 2026-11-18.

**Merge record**: PR #54 squash-merged as `30d83c1833f2ab56f9dfe9ef2a967249f181f248` after green CI (run 44130030493, 7/7 jobs, no `--admin`), 2026-11-18; prior main was `2f4842d`.
