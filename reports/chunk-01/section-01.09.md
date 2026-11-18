# Section 01.09 — Emergency fast-path + downtime pack v1 (chunk 1)

**Full build pass** on branch `build/01.09-emergency-fastpath-downtime` (off merged main `0537bd7`). The chunk line — **triage-lite tags boost the live queue; printed reception/queue fallback (downtime pack v1); token/check-in re-entry tool (CS-09)** — is implemented end-to-end: `POST /api/v1/queue/entries/{queueType}/{tokenNumber}/triage-lite` (`red`→emergency, `yellow`→urgent, `green`→no change, tag validated before mutation; red fires the new `CriticalQueueEvent` wire event `queue.urgent_arrival` + `queue.bumped` for displays), and a new downtime module (`POST /api/v1/downtime/windows` one-open-per-hospital, `PATCH /windows/{id}/close` stamping `endedAt`, `POST /reentries` that re-enters paper tokens **preserving their written numbers** and re-stamps paper check-ins via the visit registrar). `/emergency` and `/downtime` web workspaces (nav entries after Queue) complete the slice. Zero new permission codes — everything rides `queue:manage:queue` / `queue:read:queue`. CI green on branch (run **44219419744**); PR **#58** squash-merged as **`24193e1`** (no `--admin`); ledger row 68.

## 1. Scope
Per `14-CHUNK1-SECTIONS.md`: triage-lite tags boost queue priority; printed reception/queue fallback + token/check-in re-entry tool. Depends on 01.03 (reception/visit registrar) and the 01.02 queue engine. Contract reading: the **full ED module (registrations, ESI triage, trauma workflow, disposition) is chunk 4** — it already exists in `com.squamed.core.emergency` (built during the baseline, §7 of the gate-run report). 01.09 is the **front-office fast-path**: a reception-side triage tag on the queue entry itself, plus the CS-09 downtime drill tooling (the drill itself is the 01.12 exit-week activity; v1 delivers the tools the drill uses).

## 2. Built
- `backend/.../receptionqueue/api/QueueController.kt` — `POST /entries/{queueType}/{tokenNumber}/triage-lite` (`queue:manage:queue`).
- `backend/.../receptionqueue/service/QueueService.kt` — `triageLite` (tag set `red`/`yellow`/`green` validated **before** any mutation — `TRIAGE_TAG_REQUIRED` / `TRIAGE_TAG_INVALID` 400; unknown token `QUEUE_ENTRY_NOT_FOUND` 400; `red`→`emergency`, `yellow`→`urgent`, `green`→unchanged); `reenterToken` (preserves the paper token number verbatim, duplicate → `DOWNTIME_TOKEN_EXISTS` 409, notes `"downtime re-entry"`, wait estimate computed before save); `publishBumped` extracted so triage and bump share the display notification.
- `backend/.../events/EventCatalog.kt` — `CriticalQueueEvent` (wire event `queue.urgent_arrival`), catalog **22→23 EMITTED**.
- New module `backend/.../downtime/` (domain `DowntimeLog`, repository, `DowntimeService`, `DowntimeController`, DTOs): `openWindow` (reason required 400 `DOWNTIME_REASON_REQUIRED`; **one open window per hospital** → `DOWNTIME_ALREADY_OPEN` 409), `listWindows`, `closeWindow` (stamps `endedAt`; reclose → `DOWNTIME_NOT_OPEN` 409), `reenter` (takes `tokens[]` + `visitIds[]`; visits re-stamped through `VisitRegistrar.checkIn` → `checked-in`).
- `backend/.../db/migration/V60__downtime_pack.sql` — `reception.downtime_log` (hospital_id NOT NULL, reason, started_at/ended_at) + rollback note.
- `backend/.../db/migration/V33__emergency.sql` — comment-only **rollback-note header** added (the gate-9 gap from the gate-run report).
- `apps/web/src/app/(app)/emergency/` — `/emergency` workspace: live consult board + triage-lite tag actions; `apps/web/src/app/(app)/downtime/` — `/downtime` workspace: window open/close lifecycle, paper token/check-in re-entry, printable pack (`window.print()`); both wired into `navItems` as `Emergency` + `Downtime Pack` after Queue.
- `apps/web/scripts/lib/scan-fixtures.mjs` — `/emergency` + `/downtime` routes, `EMERGENCY_BOARD` + `DOWNTIME_WINDOWS` fixtures (queue-board fixture shared with `/queue`).
- 12 new web vitest (6 emergency + 6 downtime); VRT baselines `emergency.png` + `downtime.png`.
- PIT `EmergencyFastPathTests` (`PlatformIntegrationTests.kt` ~L8872–9104); catalog-count asserts updated to 23 (L5198/L5822).
- Contract: `docs/contracts/openapi-v1.json` regenerated from the running app (`:8080/api-docs`, 326,608 bytes); `schema.d.ts` stamped (sha256 `bae28d3e…e054355f`).

## 3. Data/API/event contracts delivered
- Schema: `V60` → `reception.downtime_log` (+ rollback note). No queue-table change (priority column already exists since 01.02).
- API: `POST /api/v1/queue/entries/{queueType}/{tokenNumber}/triage-lite`; `POST|GET /api/v1/downtime/windows`, `PATCH /api/v1/downtime/windows/{id}/close`, `POST /api/v1/downtime/reentries`. `POST /api/v1/reception/visits` returns `data.visit` (`VisitCreateResult`) — the re-entry journey consumes the envelope, not the DTO itself.
- Events: `queue.urgent_arrival` (new `CriticalQueueEvent`, EMITTED) + `queue.bumped` (re-used, `publishBumped` extracted) on red. Catalog count 23 asserted in the PIT.
- Permissions: **zero new codes** — window/re-entry/triage writes ride `queue:manage:queue`, reads `queue:read:queue` (RECEPTIONIST holds both since V54; downtime is front-office ops per V60 header).

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | Full loop green in PIT `emergencyFastPathAndDowntimePack` + demo 31/31: triage validation → red/yellow/green boosts → board re-ranks → call-next pulls red then yellow → red fires `urgent_arrival` + `bumped` → downtime window lifecycle → paper tokens re-entered with numbers preserved → paper check-in re-stamped → close/reclose guarded |
| 2 Zero-stub | PASS | CI run 44219419744 gate green; local grep over all 19 changed source files clean (`mock` substring ban, `// test-fixture` allowlist) |
| 3 Types | PASS | Backend `:test` green; web typecheck green (run 44219419744 + local) |
| 4 Lint + format | PASS | `ktlintFormat` clean before push; web lint 3 pre-existing warnings, no errors |
| 5 Tests | PASS | PIT **68/68** incl. `EmergencyFastPathTests` (green run 44219419744); web **120/120**; merged coverage ≥0.80 enforced (coverage gate green, run 44219419744) |
| 6 Accessibility | PASS | axe zero serious/critical on `/emergency` + `/downtime` (both in SCAN_ROUTES); 4 pre-existing moderates on `/` and `/login` unchanged |
| 7 Design system | PASS | VRT **15/15** (run 2 = plain-compare gate; run 1 wrote the two new baselines); house primitives only |
| 8 Contracts | PASS | OpenAPI snapshot regenerated from the running app + `schema.d.ts` stamped sha256 `bae28d3e…`; `CriticalQueueEvent` cataloged EMITTED; PIT catalog-count asserts updated 22→23 |
| 9 Data | PASS | `V60` carries a rollback note; `V33` gains its missing rollback-note header (dev-DB checksum repair, §9); hospital_id NOT NULL on the new table |
| 10 Audit | PASS | `AuditAspect` wraps all controller methods (reads included, per 00.16), so downtime open/close/reenter and triage-lite writes are audited like every other controller write |
| 11 Docs | PASS | This report; LEDGER row 68 COMPLETE; demo script committed (rides this docs PR) |
| 12 Exit item | PASS | Scripted slice **31/31** (`scripts/demo-01.09-emergency-fastpath-downtime.ps1`, §10). The full paper-mode hospital drill (printed pack on paper, staff walkthrough) remains the 01.12 CS-09 exit activity — v1 ships the tooling it consumes |

## 5. Tests
PIT **68/68** (green run 44219419744) incl. `EmergencyFastPathTests.emergencyFastPathAndDowntimePack` (fresh hospital per run): token sequence C-01/C-02 + routine board; `TRIAGE_TAG_REQUIRED` / `TRIAGE_TAG_INVALID` 400 / unknown token 400; yellow→urgent, green unchanged, red→emergency; board re-rank (red < yellow < routine); call-next pulls red then yellow; `CriticalQueueEvent` cataloged EMITTED; `queue.urgent_arrival` + `queue.bumped` outbox rows carrying the token; role-less user 403 on triage-lite; `DOWNTIME_REASON_REQUIRED` 400; open window + `DOWNTIME_ALREADY_OPEN` 409; one window listed; paper re-entry (DT-01/DT-02 preserved verbatim, untagged→routine, tagged→urgent); duplicate `DOWNTIME_TOKEN_EXISTS` 409; visit create → `registered` → re-stamp → `checked-in`; close stamps `endedAt` + `DOWNTIME_NOT_OPEN` 409; board unchanged in size after re-entry (nothing lost). Web: 120/120 (12 new). Turbo typecheck/lint/test green.

## 6. Accessibility
Both pages joined SCAN_ROUTES this section, so they are axe-scanned and visually baselined. Final scan: zero serious/critical on `/emergency` and `/downtime`; the 4 pre-existing moderates on `/` and `/login` are unchanged. Priority/status differences render as `StatusIndicator` text, not color alone; the printable pack is a normal DOM surface (print CSS only, `window.print()` behind a labelled button).

## 7. Issues found during build + fixes
- **CI run 44218732219 FAILED (Integration gates) — fixed, re-run green as 44219419744.** `emergencyFastPathAndDowntimePack` NPE at `PlatformIntegrationTests.kt:9087` — the paper-visit step read `visit["id"]` but `POST /api/v1/reception/visits` returns the `VisitCreateResult` envelope, so `data()` yields `{"visit": {...}}` and `["id"]` is null. The demo script made the identical mistake (3 FAILs in its first run: kiosk-403 assumption + `.data.visit`). Fix = consumer-side only: `data(...)["visit"]` in the PIT (commit `65bbfce`); the demo script's equivalent fix landed pre-PR. No service change.
- **Demo-script assumption bug: KIOSK as the triage 403 negative case.** V59 (01.08) granted KIOSK the **whole `queue` domain** because its own check-in endpoint needs `queue:manage:queue` — so a kiosk user legitimately can triage (verified live: kiosk got past authorization to a 400 `QUEUE_ENTRY_NOT_FOUND`, not 403). The permission boundary is instead a **role-less user** (`register` without `roles`), which is exactly what the PIT used (`noRoleToken()`); only the demo script was wrong. Demo now registers a role-less user for the 403 check.
- **Docker Desktop daemon flapped (8th time this build cycle)** — recovered per runbook (relaunch → `docker info` → compose start → `pg_isready` → OpenSearch :9200 → bootRun → health UP). Local-only; CI unaffected.
- **VRT new-baseline wart (known from 01.06–01.08)**: first plain-compare run wrote `emergency.png` + `downtime.png` and reported 2 failures (missing at compare time); second run is the gate — **15/15**.

## 8. External blockers
None new. E10 hardware is not implicated (no device surface here). The full ED/trauma depth is chunk 4 scope (07.05 "Full ED & trauma" extends it later); CS-09 drill proper is the 01.12 exit week.

## 9. Deviations from chunk file
- **Triage-lite lives in receptionqueue, not the emergency module** — the chunk file names the emergency section, but the tag is a queue-entry attribute and the boost must affect board ranking + call-next, which are `QueueService` concerns. Full ED keeps its own module; the fast-path rides the queue substrate (`queue:manage:queue`). Recorded per the no-silent-deviation rule.
- **KIOSK holds `queue:manage:queue` (by V59, for its check-in endpoint), so kiosk is not the triage negative case** — the boundary is a role-less user. The V59 broad-grant is the existing 01.08 design (least-privilege trimming of the KIOSK queue grant is deferred; noted as a candidate for a later hardening pass).
- **`POST /reception/visits` envelope consumed as `data.visit`** — `VisitCreateResult` wraps visit + queue entry; both the PIT and the demo originally read `data` directly. Consumer-side fix only.
- **V33 comment-only header + dev-DB checksum repair** — adding the rollback-note header changed V33's checksum; the dev DB's `flyway_schema_history` row was repaired to `-1648095783` (fresh DBs compute from the file — consistent everywhere; same rule as incident I-2: never hand-apply a placeholder checksum).
- **Demo ran pre-merge against the working tree** — the app booted from sources byte-identical to merged main `24193e1` except the PIT test line (`65bbfce`, test-code only); CI then proved the same code 68/68.
- **Demo script rides this docs PR** — untracked when the feature PR merged (same pattern as 01.05/01.06/01.07/01.08).
- **Local full coverage-chain re-run skipped** — the chain is cited from CI's Integration gates job (identical command, green on the branch head), per 01.06–01.08 precedent.

## 10. Exit-demo slice
`pwsh scripts/demo-01.09-emergency-fastpath-downtime.ps1` against the app on :8080 (compose dev stack up): admin login → fresh hospital → receptionist + kiosk users → 3 walk-ins queued C-01..C-03 → triage guards (`TRIAGE_TAG_REQUIRED`, `TRIAGE_TAG_INVALID`) → yellow boosts to urgent, green leaves routine, red boosts to emergency → board re-ranks red<yellow<routine → call-next pulls red then yellow → `CriticalQueueEvent` cataloged EMITTED, `queue.urgent_arrival` + `queue.bumped` in the outbox → role-less user 403 on triage-lite → downtime open-without-reason 400 → window opens → second open `DOWNTIME_ALREADY_OPEN` → window listed → 2 paper patients → `POST /reentries` re-enters both paper tokens (DT-01 number preserved, untagged→routine) → duplicate `DOWNTIME_TOKEN_EXISTS` 409 → paper visit starts `registered` → check-in re-stamps to `checked-in` → close stamps `endedAt` → reclose `DOWNTIME_NOT_OPEN` → board nothing-lost check. **31 passed, 0 failed**, live 2026-11-18.

**Merge record**: PR **#58** squash-merged as `24193e1` after green CI (run 44219419744, 7/7 jobs, no `--admin`), 2026-11-18; prior main was `0537bd7`. First CI attempt 44218732219 failed Integration gates (PIT envelope NPE); fix `65bbfce` re-run green.
