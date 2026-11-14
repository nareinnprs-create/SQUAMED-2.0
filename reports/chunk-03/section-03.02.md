# Section 03.02 — Billing engine — gate-run report

Static read-only assessment. Baseline commit `886c51c`, CI run `43548016617` (all 6 CI gates green; merged instruction coverage 80.13%). No code, ledger, chunk file, or `.gate/` content changed by this report.

## 1. Scope
Chunk file §03.02: rate cards per payer class (general/CGHS/ESIC/TPA/corporate/PM-JAY), versioned packages (pinned at order time), estimates, deposits, role-gated discounts, professional-fee split, consumables, consult-receipt migration. Present at baseline: flat tariff CRUD + open discount param + hard-zero tax inside charge/invoice computation. **Most named engine contents absent.**

## 2. Built
- `backend/squamed-core/src/main/kotlin/com/squamed/core/billing/service/BillingService.kt` — charge/invoice totals incl. `discountQar` and tax fields (tax hard-coded `BigDecimal.ZERO`).
- `backend/squamed-core/src/main/kotlin/com/squamed/core/billing/api/BillingController.kt` — tariff + charge/invoice endpoints.
- `V16__billing.sql` — `tariff_item` (flat price list only).

## 3. Data/API/event contracts delivered
- Tables: `tariff_item` (item/price; no payer-class dimension, no versioning).
- API: tariff CRUD + charge/invoice endpoints; permission `billing:read`, `billing:write:invoice`.
- Events: none.

## 4. Gates 1–12
| # | Gate | Result | Evidence |
|---|---|---|---|
| 1 | Functional completeness | **FAIL (PARTIAL)** | Present: tariff CRUD, discount param, invoice totals. Missing: payer-class rate cards, versioned packages, estimates, deposits, role-gated discounts, professional-fee split, consumables, consult-receipt migration. |
| 2 | Zero-stub scan | PASS | CI run 43548016617 (gate 2 green); SC-9 allowlist. |
| 3 | Types | PASS | CI run 43548016617 (gate 3 green). |
| 4 | Lint + format | PASS | CI run 43548016617 (gate 4 green). |
| 5 | Tests | PASS | CI run 43548016617 (gate 5 green); merged instruction coverage 80.13% ≥ 0.80. |
| 6 | Accessibility | PASS | CI run 43548016617 (gate 6 green). Caveat: billing page not in axe `SCAN_ROUTES`. |
| 7 | Design system | PASS | CI run 43548016617 (gate 7 green). |
| 8 | Contracts | **FAIL** | Schema-registry harness not yet wired (00.02 scope). |
| 9 | Data | **FAIL** | RLS cross-tenant suite ABSENT until 00.04 (ADR-13) — tenant-scoped billing tables. |
| 10 | Audit | PASS | `AuditAspect.kt:22-23` intercepts all controller writes; tariff/charge mutations audited via pointcut. |
| 11 | Docs | PASS | This report re-checks chunk file §03.02; ledger untouched; gate-parity green in run 43548016617. |
| 12 | Exit item | PASS | Named PIT slices cover tariff/invoice journey (see §5), green in run 43548016617. |

## 5. Tests
- PIT: `financialAndOperationsCoverage` :3005-3300 (tariff CRUD, charge, invoice issue/list/detail/cancel); `fullPatientRevenueJourney` (end-to-end money flow). Green in run 43548016617.
- Unit: none dedicated.
- Coverage: merged instruction 80.13%.

## 6. Accessibility
axe green in CI; billing page outside `SCAN_ROUTES` — not page-axe-verified.

## 7. Issues
- `discountQar` is an open, unvalidated request param (money-integrity risk; chunk file demands role-gated discounts) — **open**.
- Tax hard-coded `BigDecimal.ZERO` pending 03.03 tax engine — open by design (03.03 unbuilt).
- No rate-card/package versioning → order-time price pinning impossible — open.

## 8. External blockers
None (dependencies 03.03 tax engine are internal).

## 9. Deviations from chunk file
None logged; chunk file not edited.

## 10. Exit-demo slice
`gradlew.bat :squamed-core:integrationTest --tests "*PlatformIntegrationTests*financialAndOperationsCoverage*"` — last run green in CI run 43548016617.
