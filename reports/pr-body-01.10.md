## What this is

01.10 was blocked on `exp:E2` (per-tenant DLT header / WABA sender) and `exp:E5` (eSign certificate per hospital entity). Neither can be produced here. The rest of the section — the part the chunk-1 exit criterion actually names, *"feature gates read the entitlement service"* — was buildable, so it is built and the section lands at **SECTION REVIEW**.

## What is built

| Deliverable | How |
|---|---|
| Plans + subscriptions | `V113`: `entitlement.plan` (feature set), `entitlement.subscription` (hospital, plan, validity), two seeded plans, `entitlement:read:plan` / `entitlement:manage:plan` |
| Feature gates read the service | `@RequiresFeature` on `AiController` (`ai`) and `BiController` (`bi`); the interceptor asks the plan and refuses with `403 ENTITLEMENT_REQUIRED: <feature>` |
| Reseller plans hide modules | `CORE` omits `ai`, `bi`, `scribe`, `portal`, `kiosk`, `fhir`, `twin`, `packs`, `telemedicine`; `FULL` includes them. Adding a plan is a row, not a release |
| Branded receipts | `GET /api/v1/entitlements/brand` — tenant name, legal name, address, phone, email, licence, timezone, currency |
| Expiry | past `valid_to` reports `EXPIRED` with an empty feature set, so gated modules refuse without deleting history |

## The decision worth reviewing

**A hospital with no subscription is `UNMANAGED` and keeps every module.** The gate only starts refusing once someone subscribes a hospital. The alternative — an empty feature set for unsubscribed hospitals — would have switched off 80+ already-merged sections on the day this shipped. The state is explicit in the API response rather than implied by a null.

Second: refusal code is `ENTITLEMENT_REQUIRED`, deliberately distinct from `ACCESS_DENIED`. Support needs to tell "your plan does not include this" from "your role does not".

## Evidence

- `integrationTest`: **BUILD SUCCESSFUL 31m19s**, **118 PIT, 0 failures**.
- `test jacocoTestReport jacocoTestCoverageVerification`: BUILD SUCCESSFUL, **57 unit, 0 failures**, JaCoCo instruction **85.05%** (≥ 0.80).
- PIT `entitlementGatingCoverage`: unmanaged → AI answers; `CORE` → AI and BI both 403 with `ENTITLEMENT_REQUIRED`; `FULL` → both answer; hospital B unaffected by A's plan; brand carries the tenant name; unknown plan → `400 ENTITLEMENT_PLAN_NOT_FOUND`.
- OpenAPI regenerated from live `/api-docs`; api-client stamped `0658daad…`.

## Two things this PR does not do

- **No DLT/WABA sender identity and no eSign certificate.** Those are a BSP contract and a CA contract. Note signing keeps its PIN/ESIGN fallback.
- **No logo on receipts.** There is no asset store for hospital logos, so the brand payload is identity text only — recorded rather than faked with a placeholder URL.

## Environment note (not a code change)

The first two full-chain attempts died: once the test JVM vanished silently, once Gradle's `TestWorker.stop()` deadlocked on a full result queue after the daemon connection dropped. Root cause on the second was visible in a thread dump. Separately, `POST /api/v1/audit/verify` re-hashes every chained row for every tenant in one request; with 209,905 rows across 2,364 chain heads accumulated locally, it blew the client read timeout. Local audit tables were truncated so the local chain reflects a representative database (CI starts fresh). The incremental-verification fix belongs to the audit subsystem and is recorded in the section report, not smuggled into this PR.

Ledger: 01.10 PLANNED → SECTION REVIEW. Totals 66 COMPLETE / 1 IN PROGRESS / 12 SECTION REVIEW / 11 PLANNED.
