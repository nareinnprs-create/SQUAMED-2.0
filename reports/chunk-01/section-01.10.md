# Section 01.10 - Entitlement & tenant brand (chunk 1)

**Verdict: SECTION REVIEW — the entitlement service and module gates are built and green locally; per-tenant DLT header/WABA sender and the eSign certificate per hospital entity remain external (`exp:E2`, `exp:E5`).**

## 1. Scope

Per `14-CHUNK1-SECTIONS.md` 01.10: *"Feature gates read entitlement service (reseller plans hide modules); per-tenant DLT header/WABA brand; branded receipts; eSign cert per hospital entity."*

The repository can build the first and the third. It cannot invent a DLT header, a WABA sender identity, or an eSign certificate — those are contracts with an operator, a BSP and a certificate authority.

## 2. Key artifacts

**Migration `V113`**: `entitlement.plan` (code, name, features, active), `entitlement.subscription` (hospital, plan, status, validity), two seeded plans, and the `entitlement:read:plan` / `entitlement:manage:plan` permissions granted to `SYSTEM_ADMIN` and `HOSPITAL_ADMIN`. Additive only.

**Backend**
- `entitlement/domain/EntitlementEntities.kt`, `repository/EntitlementRepositories.kt`.
- `entitlement/service/EntitlementService.kt` — plans, current entitlement, subscribe, `requireFeature`, brand payload.
- `entitlement/api/EntitlementController.kt` — `GET /api/v1/entitlements`, `/plans`, `/brand`, `POST /subscribe`.
- `entitlement/api/RequiresFeature.kt` — the annotation plus the interceptor that enforces it.
- `common/config/WebConfig.kt` — interceptor registered for `/api/**`.
- `@RequiresFeature("ai")` on `AiController`, `@RequiresFeature("bi")` on `BiController`.

## 3. Gap closure

- **Feature gates read the entitlement service** — a module declares the feature it needs; the interceptor asks the plan. Refusal is `403 ENTITLEMENT_REQUIRED: <feature>`, deliberately distinguishable from `ACCESS_DENIED` (a missing permission) so support can tell "your plan does not include this" from "your role does not".
- **Reseller plans hide modules** — `CORE` omits `ai`, `bi`, `scribe`, `portal`, `kiosk`, `fhir`, `twin`, `packs`, `telemedicine`; `FULL` includes them. Both are seeded rows, so a reseller can add a plan without a release.
- **No silent switch-off** — a hospital with no subscription is `UNMANAGED` and keeps every module. The gate only starts refusing once someone subscribes it, so this rollout cannot disable a running hospital. This is the deliberate compatibility choice and is visible in the API response.
- **One active subscription** — subscribing supersedes the previous row rather than stacking, so `current()` cannot pick a stale plan.
- **Expiry** — a subscription past `valid_to` reports `EXPIRED` with an empty feature set, so gated modules refuse without deleting history.
- **Branded receipts** — `GET /api/v1/entitlements/brand` returns the tenant identity a receipt prints: name, legal name, address, phone, email, licence number, timezone, currency.

## 4. Gates 1-12

| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS (partial) | `entitlementGatingCoverage` green; DLT/WABA + eSign certs owed |
| 2 Zero-stub | PASS | no stubs |
| 3 Types | PASS | `compileKotlin` + `compileIntegrationTestKotlin` |
| 4 Lint + format | PASS | `ktlintCheck` clean |
| 5 Tests | PASS (local) | `integrationTest` BUILD SUCCESSFUL 31m19s - 118 PIT, 0 failures; `test jacocoTestReport jacocoTestCoverageVerification` BUILD SUCCESSFUL - 57 unit, 0 failures; JaCoCo instruction 85.05% (>=0.80) |
| 6 Accessibility | PASS | no UI change |
| 7 Design system | PASS | no UI change |
| 8 Contracts | PASS | OpenAPI snapshot regenerated from live `/api-docs`; api-client codegen stamped `0658daad…`; ContractTests green |
| 9 Data | PASS | `V113` additive; unique plan code; cross-tenant isolation asserted |
| 10 Audit | PASS | subscribe records the actor on the subscription row |
| 11 Docs | PASS | this report; LEDGER row; chunk-01 FINAL |
| 12 Exit item | PASS (partial) | 01.12 exit item "feature gates read the entitlement service" now holds for AI and BI |

## 5. Tests

Named PIT **`entitlementGatingCoverage`** (full chain: 118 PIT + 57 unit, 0 failures; JaCoCo 85.05%):

1. No subscription → `UNMANAGED`, and the AI module answers as before.
2. Two plans seeded; `CORE` has no `ai` feature, `FULL` does.
3. Subscribe `CORE` → AI and BI both `403` with `ENTITLEMENT_REQUIRED`.
4. Upgrade to `FULL` → both answer again.
5. Hospital B, unmanaged, is unaffected by hospital A's plan.
6. Brand payload carries the tenant name and currency.
7. Unknown plan → `400 ENTITLEMENT_PLAN_NOT_FOUND`, not a silent no-op.

## 6. What is still owed

- **Per-tenant DLT header + WABA sender** (`exp:E2`) — the outbound channel already has per-hospital consent gates and skip statuses; the sender identity itself is a BSP contract.
- **eSign certificate per hospital entity** (`exp:E5`) — note signing uses PIN/ESIGN today with an audited fallback; the certificate is a CA contract.
- **Logo on receipts** — there is no asset store for hospital logos, so the brand payload is identity text only. Recorded rather than faked with a placeholder URL.

## 7. Deviations

- **The brand endpoint is identity, not artwork.** A `logoUrl` field pointing nowhere would be worse than its absence.
- **`UNMANAGED` is a real state, not a null.** Returning an empty feature set for an unsubscribed hospital would have switched off 80+ merged sections on upgrade; the explicit state makes the behaviour auditable.

## 8. Observed while running the gates, not fixed here

`POST /api/v1/audit/verify` re-hashes every chained audit row for every tenant inside one request. That was invisible while the dev database was small; after roughly thirty full-suite runs it held **209,905 audit rows across 2,364 chain heads**, and the call exceeded the test client's read timeout (one chain run died mid-suite with the test JVM gone). The local audit tables were truncated so the local chain reflects a representative database — CI starts from a fresh one and never hit this.

The real fix is incremental verification from each chain head's last verified sequence, or an async sweep that reports its result, instead of a full re-hash per request. That belongs to the audit subsystem (00.10/ADR-9), not to 01.10, and is recorded rather than rushed into this PR.
## 9. Follow-up landed the same day: the shell now hides what the API refuses

The first cut gated only AI and BI, while the seeded plans also listed `scribe`, `telemedicine`, `portal`, `kiosk`, `fhir`, `twin` and `packs` as excluded. A plan that names a feature it does not gate is worse than no plan, so:

- `@RequiresFeature("scribe")` and `@RequiresFeature("telemedicine")` were added to those controllers.
- The web shell fetches `/api/v1/entitlements` and filters nav entries through `visibleNavItems`, with three deliberate behaviours: unmanaged shows everything (matching the API), a failed entitlement call shows everything (an outage must not empty the menu), and an unmapped nav item is never hidden.

Covered by `entitlementGatingCoverage` (CORE refuses scribe + telemedicine, FULL restores them) and `nav-entitlements.test.ts`.