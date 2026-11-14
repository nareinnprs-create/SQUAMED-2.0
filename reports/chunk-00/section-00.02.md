# Section 00.02 — Contract foundation

## 1. Scope
OpenAPI style guide, error envelope, pagination, idempotency middleware, event envelope v1 JSON schema, API-client codegen pipeline, schema-registry test harness. Contract layer for all later sections.

## 2. Built (current tree)
- `packages/api-client/src/client.ts` — axios client, Bearer injection, 401→onSessionExpired, envelope-unwrapping get/getList/post/put/patch/delete, extractError.
- `packages/api-client/src/index.ts` — public exports.
- `packages/api-client/src/client.test.ts` — client behavior tests.
- `packages/schemas/src/common.ts` — shared zod enums (gender, blood group, status).
- `packages/schemas/src/patient.ts` + `patient.test.ts` — patient schema + tests.
- `packages/domain-types/src/common.ts` + `patient.ts` + `index.ts` — shared ApiResponse/ApiError/Paginated and patient types.
- Backend: `common` ApiResponse envelope + pagination honored across controllers (PIT-verified).

## 3. Data/API/event contracts delivered
- Error envelope (ApiResponse/ApiError) and Paginated envelope — delivered and consumed PIT-wide (`validationErrorsUseCleanContract`, `errorResponsesFollowContract`).
- OpenAPI surface exposed and asserted (`openApiExposesAllModuleSurfaces`).
- NOT delivered: idempotency middleware (zero hits repo-wide), event envelope v1 JSON schema (zero hits), API-client codegen pipeline (client is hand-written), schema-registry test harness (zero hits).

## 4. Gates
1. FAIL — named contents missing: idempotency middleware, event envelope v1 schema, codegen pipeline, schema-registry harness.
2. PASS — CI run 43548016617 (gate 2 green).
3. PASS — CI run 43548016617 (gate 3 green).
4. PASS — CI run 43548016617 (gate 4 green).
5. PASS — CI run 43548016617 (gate 5 green); named tests below.
6. PASS — CI run 43548016617 (gate 6 green) (no new UI from this section).
7. PASS — CI run 43548016617 (gate 7 green) (no UI from this section).
8. FAIL — schema-registry harness not yet wired (00.02 scope); OpenAPI + envelope contracts covered only by PIT assertions.
9. PASS — no tables introduced by this section; envelope/pagination contracts are data-free.
10. PASS — no write paths added (audit interceptor has nothing to intercept here).
11. PASS — this report; chunk file (04 §00.02) contents enumerated honestly above.
12. FAIL — no scripted exit-demo slice for contract foundation yet.

## 5. Tests
- `packages/api-client/src/client.test.ts`, `packages/schemas/src/patient.test.ts` (frontend-gates green, run 43548016617).
- PIT: `openApiExposesAllModuleSurfaces`, `errorResponsesFollowContract`, `validationErrorsUseCleanContract` (integration-gates green, run 43548016617).
- Coverage: merged unit+PIT ≥0.80 enforced in integration-gates (SC-10); no per-package number available statically.

## 6. Accessibility
N/A — no UI in this section. Gate 6 green repo-wide (run 43548016617).

## 7. Issues found during build
Baseline section — see BASELINE-AUDIT.md §2.2 rows (api-client/schemas/domain-types all PARTIAL). Verified in current tree: still no idempotency/envelope/codegen/schema-registry wiring; nothing from wiring PRs changed this.

## 8. External blockers
None.

## 9. Deviations from chunk file
Zero formal SC deviations; named contents (idempotency middleware, event envelope v1, codegen pipeline, schema-registry harness) are unbuilt, not re-scoped.

## 10. Exit-demo slice
Not scripted. Closest repro: PIT `openApiExposesAllModuleSurfaces` + `errorResponsesFollowContract` under integration-gates (run 43548016617 green).
