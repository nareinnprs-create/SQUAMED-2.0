# Section 00.09 — Event fabric

## 1. Scope
Per chunk-00 item 8: transactional outbox, dispatcher, Redis pub/sub, WebSocket gateway with per-user scope filter, replay, DLQ + integration error queue. The baseline report (run 43548016617) failed gates 1/8/12 with the named gap: outbox (publisher sent direct Kafka, catch→log→drop), dispatcher, Redis relay, replay CLI, DLQ + integration error queue, and no scripted slice for the outbox/replay/DLQ journey. This section closes that named gap on the 00.02 envelope-v1 schema + schema-registry harness.

## 2. Built (current tree)
- **`V48__event_fabric.sql`** — `events.event_outbox` (envelope columns incl. `envelope_version`, `actor_type/actor_id`, `correlation_id`, `causation_id`, `recipient_ids` JSONB, `status` CHECK PENDING/DISPATCHING/SENT/FAILED, `attempts/max_attempts/next_attempt_at/last_error`, UNIQUE `event_id`), `events.event_cursor` (per-consumer unique), `events.integration_error` (`hospital_id` FK, `status` OPEN/REPLAYED/RESOLVED, `replay_count/last_replay_error`), permission seed `events:admin` → SYSTEM_ADMIN. `hospital_id` is a nullable FK on both tenant-bearing tables: system-originated broadcasts (seed/bootstrap events) carry no hospital — WS frames still filter by the *session's* hospital, so no cross-hospital leak path.
- **`DomainEvent` envelope v1 completion** — now carries the full chunk-00 field list: `eventId, eventType, occurredAt, source, entityType/entityId, hospitalId, payload, recipientIds, version, actorType/actorId, correlationId, causationId`; `DomainEventPublisher` stamps actor from `SecurityContext` (`system` + MDC fallback for scheduled/MLLP paths), correlation/causation from MDC, and **writes the outbox row inside the caller's transaction** (atomic with the state change; direct-Kafka catch→log→drop removed).
- **`EventDispatcher`** (`@Scheduled`) — drains PENDING rows: `DISPATCHING` → Kafka `squamed.domain-events` + per-type topic (`EventCatalog.perTypeTopic` = `squamed.<wire-name>` for `task.`/`notification.` prefixes) → Redis pub/sub `squamed.events` → `SENT` + `dispatched_at` → cursor advance. Failure path: attempts+1, exponential backoff `1s << attempts` capped 60s, `max_attempts` 8 → `FAILED` (DLQ); stale `DISPATCHING` rows (>5 min) requeued. Fix recorded: `repository.save()` on a detached entity does not propagate the new `@Version` — every save return is reassigned (`row = outboxRepository.save(row)`), else the second save in the same tick throws `ObjectOptimisticLockingFailureException` and the row never leaves DISPATCHING.
- **`RealtimeEventBridge`** — Redis pub/sub listener owns **all** `event.received` WS frames (Kafka listener no longer pushes WS), filtering by recipient ids and hospital scope; `RealtimeSessionRegistry` per-user sessions; handler closes anonymous WS with POLICY_VIOLATION (JWT enforced at the handler, `/ws` + `/realtime` remain permitAll at the security layer like `/health`).
- **`EventCatalog`** — the chunk-00 EVENTS v1 set: 12 PascalCase names ↔ dot-case wire names with EMITTED/PLANNED status, served at `GET /api/v1/events/catalog` (public — name/status only, no payload data).
- **Integration error queue** — `IntegrationErrorService` records HL7 failures (REST API + MLLP inbound: malformed ADT is acked `AE` *and* persisted), replay re-runs the stored payload, `replayCount` counts the original failure + replays, a failed replay writes `last_replay_error` (row persists, count grows — history not overwritten).
- **`EventOpsService` + `EventAdminController`** — `GET /api/v1/admin/events/outbox`, `POST /api/v1/admin/events/outbox/replay` (FAILED rows → PENDING), `GET /api/v1/admin/events/errors`, `POST /api/v1/admin/events/errors/{id}/replay`, `GET /api/v1/admin/events/cursors`; guarded by `events:admin` (`PermissionAspect` 403 for non-admins — demo asserts doctor denied).
- `patient.updated` now emitted on demographics change; `notification.dispatched` event emitted by the notification bridge.
- Contract: `docs/contracts/openapi-v1.json` snapshot (293,681 bytes, sha256 `80a22839…`) + stamped `packages/api-client/src/generated/schema.d.ts` regenerated in the same PR per `docs/development/contract-codegen.md`; `npm run build` 8/8 green.
- `scripts/demo-00.09-events.ps1` — exit-demo slice (14 checks, pwsh, port 8081).

## 3. Contracts delivered
- `GET /api/v1/events/catalog` — public event-type catalog (12 v1 types, status).
- `GET /api/v1/admin/events/outbox?status=` — outbox inspection; `POST /api/v1/admin/events/outbox/replay` (`OutboxReplayRequest`) — DLQ replay.
- `GET /api/v1/admin/events/errors` — integration error queue; `POST /api/v1/admin/events/errors/{id}/replay`.
- `GET /api/v1/admin/events/cursors` — consumer cursor tracking.
- WS: `/ws` (existing) + `/realtime` alias; envelope v1 JSON schema (00.02) now actually validated by the PIT harness against real outbox payloads.
- Snapshot + stamped codegen landed together; `openApiSnapshotMatchesLiveContract` green (drift would fail the integration gate).

## 4. Gates
1. PASS — zero real stubs; zero-stub grep clean on the events package + demo script (run 43641056792).
2. PASS — run 43641056792 (PR #21, squash 26d5b38; no `--admin`).
3. PASS — Detect source trees job green.
4. PASS — ktlint + unit + ArchUnit (Backend gates job) green; local `ktlintFormat` BUILD SUCCESSFUL.
5. PASS — Backend gates job (unit suite) green.
6. PASS — Accessibility + performance gates green (no UI in this section).
7. PASS — Frontend gates green; stamped client pins the snapshot hash; `npm run build` 8/8.
8. PASS — Integration gates job green: all **48** PIT (47 retained + new `eventFabricCoverage`) + merged coverage ≥ 0.80.
9. PASS — V48 applied (Flyway validate green in bootRun logs on merged main + CI); outbox/cursor/integration-error tables with envelope columns, status CHECKs, UNIQUE `event_id`; no tenant NOT NULL on `hospital_id` (system-originated broadcasts) but WS delivery filters by session hospital — cross-hospital frame leak asserted absent in PIT.
10. PASS — audit surface: `AuditAspect` pointcut covers all `*Controller.*` except AuthController, so `EventAdminController` replay/ops writes produce audit rows automatically.
11. PASS — this report; ledger row 42 + merge-log #21 + totals updated in the same docs batch.
12. PASS — scripted live slice `scripts/demo-00.09-events.ps1`: **14/14 on 8081** (merged main 26d5b38; pwsh), 2026-11-15; first proven pre-merge on the branch (14/14).

## 5. Tests
- PIT (new, 48th): `EventFabricTests.eventFabricCoverage` — two hospitals + doctor each per run (dev DB persists rows): real WS clients connect (`/realtime`, JWT handshake), assignee receives the `task.created` frame, cross-hospital doctor does not; outbox row SENT with stamped envelope v1 (version/actor/correlation validated against the 00.02 JSON schema); malformed MLLP ADT acked AE + integration error row; replay increments `replayCount` and persists the failure; consumer cursors tracked (`dispatcher`, `notification-bridge`); catalog lists the 12 v1 types with `NotificationDispatched` EMITTED; doctor denied event ops 403.
- PIT (retained): 48 tests total green in CI run 43641056792 (Contract 7, Coverage 20, EventFabric 1, Journey 5, Phase2Inpatient 7, Security 7, TenantIsolation 1).
- Web: stamped client regenerated; `npm run build` 8/8 green.

## 6. Accessibility
N/A — no UI. Gate 6 green repo-wide (run 43641056792).

## 7. Issues found during build
- **`/auth/me` shape bug (3 PIT sites + demo script)**: tests read `data.user.userId`; the API returns `data.userId` directly — the silently-wrong null assigneeId meant `TaskService` had no recipients → **no `task.created` event at all**, which surfaced as "WS frame missing" and cost a full debug loop. Fixed at the read sites, not the API.
- **Kotlin prohibition**: `private class` nested inside an `inner class` (test) is illegal — `RealtimeCollector` hoisted to the outer test class.
- **SecurityConfig gap**: `/realtime` WS handshake 401 (only `/ws` was permitted) and `GET /events/catalog` 401 (catalog is public by design) — both added to permitAll; the WS handler still enforces JWT itself.
- **JPA optimistic-lock pitfall (the substantive one)**: the dispatcher's double save (`DISPATCHING` then `SENT`) rethrew `ObjectOptimisticLockingFailureException` every tick — `save()` merges the detached entity and never propagates the new `@Version` back to the caller's instance. Kafka/Redis delivery *succeeded* while rows stayed stuck DISPATCHING. Fixed by reassigning every save return; comment left at the site as the house pattern for any future double-save code.
- **Contract snapshot drift**: new `/events/*` + `/admin/events/*` paths failed `openApiSnapshotMatchesLiveContract` until the snapshot + codegen regen (293,681 bytes, sha256 `80a22839…`).

## 8. External blockers
None for the fabric itself — outbox, dispatcher, relay, DLQ, replay and ops API are all internal capabilities. The ledger's `exp:E1` (live ABDM linkage, owed from 00.07 §8) remains open and untouched by this section; chunk-file downstream legs (task auto-assignment → 00.11, notification channels → 00.12) are their own sections.

## 9. Deviations from chunk file
- **Replay CLI → admin API**: chunk names a "replay CLI"; shipped as `POST /api/v1/admin/events/outbox/replay` + `POST /api/v1/admin/events/errors/{id}/replay` (scriptable — the demo drives replay over HTTP; an HTTP op is auditable by the existing AuditAspect, a CLI would bypass it).
- **Wire naming**: chunk-00 catalog names are PascalCase; Kafka wire names are dot-case (`task.created`) bridged by `EventCatalog.perTypeTopic` (only `task.`/`notification.` prefixes get per-type topics; the main listener consumes only `squamed.domain-events`).
- **Empty-recipient broadcast**: events with no explicit recipients (e.g. `notification.dispatched`) broadcast to all sessions of the source hospital rather than being dropped — visibility over silence; per-channel opt-outs land with 00.12's notification design.
- **`replayCount` semantics**: counts the original failure + every replay (not replays only); each failed replay updates `last_replay_error` on the same row (history via count + last error, not per-attempt rows).
- **Dispatcher single-instance shortcut**: no claim lease / SKIP LOCKED — safe for the single dev app instance; `shortcut:` comment at `EventDispatcher.send`, must gain claim semantics before >1 replica.
- **Pre-existing leak flagged, not fixed here**: in-app notification delivery honors `recipientIds` but a cross-hospital in-app notification path via recipient ids exists in the 00.12-owned notification bridge — owned by 00.12, recorded here so it is not silently passed.

## 10. Exit-demo slice
`pwsh scripts/demo-00.09-events.ps1` against the running app (8081, merged main 26d5b38): admin login → two hospitals → doctors registered → WS sessions connected → task created → u1 receives `task.created` frame → cross-hospital `task.assigned` filtered from u3 → outbox row SENT with stamped envelope v1 → malformed ADT acked AE → integration error recorded → replay increments attempts, failure persists → consumer cursors tracked → catalog lists v1 set (12) → doctor denied event ops. **14 passed, 0 failed**, live 2026-11-15 (both pre-merge branch and post-merge main). PIT suite green in CI run 43641056792.
