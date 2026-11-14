# Section 00.09 — Event fabric

## 1. Scope
Transactional outbox, dispatcher, Redis pub/sub, WebSocket gateway (per-user scope filter), replay CLI, DLQ + integration error queue.

## 2. Built (current tree)
- `events/DomainEvent.kt`, `events/DomainEventPublisher.kt` — event model; direct `KafkaTemplate` send to `squamed.domain-events` + `squamed.task.*` / `squamed.notification.*`, catch→log→drop on failure.
- `events/DomainEventListener.kt` — Spring event bridge.
- `events/gateway/RealtimeGateway.kt`, `RealtimeSessionRegistry.kt`, `RealtimeWebSocketHandler.kt`, `WebSocketConfig.kt` — WebSocket push, per-user session registry (`sessionsFor(userId)` scope filter).

## 3. Contracts delivered
- Domain event envelope (in-code `DomainEvent`), Kafka topics, `/ws` per-user WS push (PIT drives a real WS client).
- NOT delivered: transactional outbox (zero grep hits), dispatcher, Redis pub/sub relay, replay CLI, DLQ + integration error queue.

## 4. Gates
1. FAIL — named contents missing: outbox, dispatcher, Redis pub/sub, replay CLI, DLQ; direct Kafka send deviates from the chunk-00 contract (outbox→Redis→WS, Kafka out of scope).
2. PASS — CI run 43548016617 (gate 2 green).
3. PASS — CI run 43548016617 (gate 3 green).
4. PASS — CI run 43548016617 (gate 4 green).
5. PASS — CI run 43548016617 (gate 5 green); WS realtime covered by named PIT test below.
6. PASS — CI run 43548016617 (gate 6 green) (no dedicated UI).
7. PASS — CI run 43548016617 (gate 7 green).
8. FAIL — schema-registry harness not yet wired (00.02 scope); event envelope v1 JSON schema ABSENT (this is its owning section pair).
9. PASS — no DB tables added by this section (Kafka topics via `infrastructure/docker/kafka/create-topics.sh`).
10. PASS — no write paths in this module; events are emitted by other modules' audited controllers.
11. PASS — this report; chunk file accuracy note: contract wording (outbox) vs built (direct Kafka) is flagged above, not silently passed.
12. FAIL — no scripted slice for outbox/replay/DLQ journey; only WS push is PIT-covered.

## 5. Tests
PIT: `authRefreshLogoutMfaAndRealtimeCoverage` (real StandardWebSocketClient connects, receives frame), journey tests assert downstream effects of published events — green, run 43548016617. Coverage ≥0.80 merged enforced.

## 6. Accessibility
N/A — no UI. Gate 6 green repo-wide (run 43548016617).

## 7. Issues found during build
Baseline section — see BASELINE-AUDIT.md §2.1 events row and cross-decision note (catch→log→drop undermines event-fed sections). Verified current tree: publisher UNCHANGED (direct Kafka, no outbox); WS gateway is the one solid part.

## 8. External blockers
None.

## 9. Deviations from chunk file
Zero SC-logged deviations, but the built design (direct Kafka) materially differs from the chunk-00 contract (outbox→Redis pub/sub→WS). This needs either a build or a chunk-file amendment — currently neither.

## 10. Exit-demo slice
Partial only: PIT `authRefreshLogoutMfaAndRealtimeCoverage` (WS frame received by scoped user). Outbox/replay/DLQ slice not scripted.
