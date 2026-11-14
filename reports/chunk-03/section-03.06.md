# Section 03.06 — Collections + GL export — gate-run report

Static read-only assessment. Baseline commit `886c51c`, CI run `43548016617` (all 6 CI gates green; merged instruction coverage 80.13%). No code, ledger, chunk file, or `.gate/` content changed by this report.

## 1. Scope
Chunk file §03.06: Cash/UPI/card, shift cash-up (variance → security event), payment gateway integration, recon report, refunds; daily GL journal export (Tally/Zoho-ready). Present at baseline: manual payment capture + settlement only. **Gateway, cash-up, recon, refunds, GL export absent.**

## 2. Built
- `backend/squamed-core/src/main/kotlin/com/squamed/core/billing/api/BillingController.kt` — payment collection endpoint(s), permission `billing:collect:payment`.
- `backend/squamed-core/src/main/kotlin/com/squamed/core/billing/service/BillingService.kt` — payment → invoice settle transitions (paid, due 0).
- `V16__billing.sql` — `payment` table (method string CASH/UPI/card, reference, receivedBy).

## 3. Data/API/event contracts delivered
- Tables: `payment` (method/reference/receivedBy), invoice paid-state transition.
- API: collect-payment endpoint; permission `billing:collect:payment`.
- Events: no security event on variance (no shift cash-up exists); no GL export artifact.

## 4. Gates 1–12
| # | Gate | Result | Evidence |
|---|---|---|---|
| 1 | Functional completeness | **FAIL (PARTIAL)** | Present: manual cash/UPI/card capture, reference, receivedBy, settle-to-paid. Missing: payment gateway integration, shift cash-up + variance security event, recon report, refunds, daily GL journal export. |
| 2 | Zero-stub scan | PASS | CI run 43548016617 (gate 2 green); SC-9 allowlist. |
| 3 | Types | PASS | CI run 43548016617 (gate 3 green). |
| 4 | Lint + format | PASS | CI run 43548016617 (gate 4 green). |
| 5 | Tests | PASS | CI run 43548016617 (gate 5 green); merged instruction coverage 80.13% ≥ 0.80. |
| 6 | Accessibility | PASS | CI run 43548016617 (gate 6 green). Caveat: billing page not in axe `SCAN_ROUTES`. |
| 7 | Design system | PASS | CI run 43548016617 (gate 7 green). |
| 8 | Contracts | **FAIL** | Schema-registry harness not yet wired (00.02 scope). |
| 9 | Data | **FAIL** | RLS cross-tenant suite ABSENT until 00.04 (ADR-13) — tenant-scoped payment tables. |
| 10 | Audit | PASS | `AuditAspect.kt:22-23` intercepts payment writes (controller-mediated); audit reads asserted JourneyTests :882-885. |
| 11 | Docs | PASS | This report re-checks chunk file §03.06; ledger untouched; gate-parity green. |
| 12 | Exit item | PASS | Named PIT slice `fullPatientRevenueJourney` payment leg green in run 43548016617. |

## 5. Tests
- PIT: `fullPatientRevenueJourney` payment slice :635-693 (CASH payment → completed → invoice paid → due 0). Green in run 43548016617.
- Unit: none dedicated.
- Coverage: merged instruction 80.13%.

## 6. Accessibility
axe green in CI; billing page outside `SCAN_ROUTES` — not page-axe-verified.

## 7. Issues
- No refunds path — money correction unguarded — open.
- Method is a free string (no gateway abstractions) — open, gateway deferred external.
- No variance/security-event machinery — open (chunk-1 security event store unused here).

## 8. External blockers
⛔ Gateway/PSP selection (chunk-file note) blocks gateway integration + recon; GL export is not vendor-blocked, simply unbuilt.

## 9. Deviations from chunk file
None logged; chunk file not edited.

## 10. Exit-demo slice
`gradlew.bat :squamed-core:integrationTest --tests "*PlatformIntegrationTests*fullPatientRevenueJourney*"` — last run green in CI run 43548016617.
