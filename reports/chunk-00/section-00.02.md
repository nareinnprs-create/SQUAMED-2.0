# Section 00.02 — Contract foundation

## 1. Scope
OpenAPI style guide, error envelope, pagination, idempotency middleware, event envelope v1 JSON schema, API-client codegen pipeline, schema-registry test harness. Contract layer for all later sections.

## 2. Built (current tree)
- Pre-existing (wiring PRs): `packages/api-client/src/client.ts` + `index.ts` + `client.test.ts`; `packages/schemas` common/patient; `packages/domain-types`; backend ApiResponse envelope + pagination (PIT-verified).
- Built in PR #14 (commit 9a4f063, gate-fix e9900b6, merged cc251e4):
  - `backend/.../common/idempotency/IdempotencyFilter.kt` + `IdempotencyKeyRecord(.Repository)` + `V41__idempotency_keys.sql` — store-on-first-completion; replay verbatim; in-flight retry → 409 `IDEMPOTENCY_CONFLICT`; key+payload-hash mismatch → 409; 5xx never stored. Ordered after the security chain.
  - `backend/.../events/SchemaRegistry.kt` + `schemas/event-envelope-v1.schema.json` (draft 2020-12, `additionalProperties: false`) — wire schema for `squamed.domain-events`; breaking change = new version, never edit v1.
  - `docs/contracts/openapi-v1.json` (committed snapshot of live `/api-docs`) + `docs/development/contract-codegen.md` + `docs/api/openapi-style-guide.md`.
  - `packages/api-client/scripts/stamp-schema.mjs` + generated `schema.d.ts` (committed, sha256-stamped; `.gitignore` negation) + `schema.test.ts` stamp/surface drift checks.
  - `scripts/demo-00.02-contract-foundation.ps1` — gate-12 exit-demo slice.

## 3. Data/API/event contracts delivered
- Error envelope (ApiResponse/ApiError) and Paginated envelope — delivered and consumed PIT-wide (`validationErrorsUseCleanContract`, `errorResponsesFollowContract`).
- OpenAPI surface exposed, committed, and drift-pinned (`openApiSnapshotMatchesLiveContract`, `openApiExposesAllModuleSurfaces`).
- Idempotency-Key middleware (store/replay/conflict semantics above).
- Event envelope v1 JSON schema published and validated against the real `DomainEvent`.
- API-client codegen pipeline: snapshot → openapi-typescript → stamped schema.d.ts, drift fails typecheck/tests.
- Schema-registry test harness: `SchemaRegistry` (networknt V202012) exercised by PIT.

## 4. Gates
1. PASS — all 7 named contents built (PR #14); scope of chunk file 04 §00.02 fully covered.
2. PASS — CI run 43557721294 (backend gates: ktlint + test + ArchUnit).
3. PASS — CI run 43557721294 (gate parity).
4. PASS — CI run 43557721294 (typecheck + lint + test, frontend).
5. PASS — CI run 43557721294 (integration gates; PIT 41/41 incl. 5 new/updated ContractTests).
6. PASS — no UI from this section; a11y/perf gates green repo-wide.
7. PASS — same.
8. PASS — schema-registry harness wired: `eventEnvelopeV1ValidatesPublishedShape` proves real `DomainEvent` serialization validates against `event-envelope-v1.schema.json`.
9. PASS — `V41__idempotency_keys.sql` adds idempotency_keys with FK to users + unique constraint; Flyway applied clean (dev DB at v41).
10. PASS — filter stores responses only; no domain write path added; replays are served by the filter before controllers so the audit aspect does not double-log them (design decision, recorded here).
11. PASS — this report + ledger row reflect what is; chunk file enumerated honestly.
12. PASS — `scripts/demo-00.02-contract-foundation.ps1` ran green (5/5 checks) against a live 8081 instance on 2026-11-14: login, first POST creates, same-key retry replays identical patient id, same-key different-payload → 409 `IDEMPOTENCY_CONFLICT`, snapshot sha256 stamp integrity.

## 5. Tests
- Unit: `IdempotencyFilterTest` 6 tests (pass-through, store, replay-no-chain, conflict, 5xx-not-stored, independent keys) — green locally + gate 2/5 (run 43557721294).
- PIT (integration-gates, run 43557721294): `idempotentRetryReplaysSameResponseWithoutSideEffect`, `sameIdempotencyKeyDifferentPayloadConflicts`, `openApiSnapshotMatchesLiveContract`, `openApiExposesAllModuleSurfaces`, `errorResponsesFollowContract`, `eventEnvelopeV1ValidatesPublishedShape` — full suite 41/41.
- Frontend: `packages/api-client/src/generated/schema.test.ts` (stamp + surface drift) — vitest 5/5, typecheck, eslint green.
- Coverage: merged instruction coverage ≥0.80 enforced (SC-10) in integration-gates.

## 6. Accessibility
N/A — no UI in this section. Gate 6 green repo-wide (run 43557721294).

## 7. Issues found during build
- Zero-stub gate tripped on Spring's `MockHttpServletRequest/Response/FilterChain` in the new unit test (case-insensitive `mock` scan; SC-9 allowlist covers only `mockk`). Fixed with the gate's own `// test-fixture` tagging mechanism — types aliased `TestChain/TestRequest/TestResponse`, 3 tagged import lines. `.gate/` and ci.yml untouched (agent-read-only; no SC amendment needed).
- PIT test mapper lacked JSR-310 → `Instant occurredAt` serialization threw; fixed by registering `JavaTimeModule` + ISO dates (mirrors production Boot mapper).
- Full PIT + bootRun concurrently share the fixed MLLP port → false `MLLP listener running == false` failure; run sequentially (operational note, no code change).

## 8. External blockers
None. (B2 stays OPEN per SC-12 — does not block 00.02.)

## 9. Deviations from chunk file
None. All named contents built as specified.

## 10. Exit-demo slice
`scripts/demo-00.02-contract-foundation.ps1` — green 5/5 (2026-11-14, live 8081): replay identity, conflict semantics, snapshot stamp. CI twin: PIT ContractTests (run 43557721294).
