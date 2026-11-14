# Section 03.01 — Charge capture ledger — gate-run report

Static read-only assessment. Baseline commit `886c51c`, CI run `43548016617` (all 6 CI gates green; merged instruction coverage 80.13% per SC-10). No code, ledger, chunk file, or `.gate/` content changed by this report.

## 1. Scope
Chunk file (`16-CHUNK3-SECTIONS.md` §03.01): event-fed unified ledger (chunks 1–2 events), manual entry audited + reason. Present at baseline: manual charge capture chain (tariff → charge → invoice → payment) with audited controller writes. **Not present: the event-fed ledger** — charges enter only via manual POST; no consumption of chunk-1/2 events.

## 2. Built
- `backend/squamed-core/src/main/kotlin/com/squamed/core/billing/service/BillingService.kt` — charge/invoice/payment domain logic, manual status transitions.
- `backend/squamed-core/src/main/kotlin/com/squamed/core/billing/api/BillingController.kt` — 10 REST endpoints.
- `backend/squamed-core/src/main/kotlin/com/squamed/core/billing/domain/` + dtos — billing tables mapped.
- `V16__billing.sql` — `billing.tariff_item`, `charge`, `invoice`, `invoice_line`, `payment` (NUMERIC(12,2), unique `invoice_number`); `V18__*.sql` adds invoice_line audit columns.

## 3. Data/API/event contracts delivered
- Tables: tariff_item / charge / invoice / invoice_line / payment; invoice numbers `INV-*`, unique.
- API: 10 endpoints under billing controller; permissions `billing:read`, `billing:write:invoice`, `billing:collect:payment`.
- Events: **none emitted** (ledger is not event-fed).

## 4. Gates 1–12
| # | Gate | Result | Evidence |
|---|---|---|---|
| 1 | Functional completeness | **FAIL (PARTIAL)** | Present: tariff/charge/invoice/payment chain works live (PIT green). Missing vs chunk file: event-fed ledger from chunks 1–2 events; audited manual entry **with reason** capture (charges are bare manual POSTs, status strings `posted`/`invoiced`). |
| 2 | Zero-stub scan | PASS | CI run 43548016617 (gate 2 green); allowlist per SC-9. |
| 3 | Types | PASS | CI run 43548016617 (gate 3 green; backend-gates). |
| 4 | Lint + format | PASS | CI run 43548016617 (gate 4 green). |
| 5 | Tests | PASS | CI run 43548016617 (gate 5 green); merged instruction coverage 80.13% ≥ 0.80 (SC-10). |
| 6 | Accessibility | PASS | CI run 43548016617 (gate 6 green). Caveat: billing page (838 LOC) is **not** in axe `SCAN_ROUTES` (`apps/web/scripts/lib/scan-fixtures.mjs` — only `/`, `/login`, `/appointments`, `/patients`, `/dashboard`); page-level axe coverage gap. |
| 7 | Design system | PASS | CI run 43548016617 (gate 7 green; Lighthouse in same job). |
| 8 | Contracts | **FAIL** | Schema-registry harness not yet wired (00.02 scope); backend-gates green covers only what the merged suite contains (GATES.md note). |
| 9 | Data | **FAIL** | RLS cross-tenant suite ABSENT until 00.04 (ADR-13) — billing tables are tenant-scoped. |
| 10 | Audit | PASS | `AuditAspect.kt:22-23` pointcut `execution(* com.squamed.core..*Controller.*(..)) && !AuthController` intercepts all billing writes; audit reads asserted in JourneyTests :882-885. Named `auditTrailWritesForModuleActions` test absent from current tree (noted in §7). |
| 11 | Docs | PASS | This report re-checks chunk file §03.01; ledger untouched by this read-only run; gate-parity green in run 43548016617. |
| 12 | Exit item | PASS | Named PIT slice `fullPatientRevenueJourney` green in run 43548016617. |

## 5. Tests
- PIT: `fullPatientRevenueJourney` (tariff → charge → invoice `INV-*` → CASH payment → completed → paid → due 0; payment slice :635-693); `financialAndOperationsCoverage` :3005-3300 (tariff CRUD, charge, invoice issue/list/detail/cancel — payment NOT in this test); audit-read assertions JourneyTests :882-885. Green in run 43548016617.
- Unit: none dedicated to billing.
- Coverage: merged instruction 80.13% (38,068/47,507, branch 52%).

## 6. Accessibility
axe: green in CI, but billing page outside `SCAN_ROUTES` — keyboard/focus paths for billing UI not axe-verified at page level.

## 7. Issues
- Event-fed ledger absent (root: chunk-1/2 event bus not yet consumed by billing) — **open**.
- Statuses are free strings (`posted`/`invoiced`), not a guarded state machine — open.
- `auditTrailWritesForModuleActions` named test absent; audit reads verified inside PIT only — open (BASELINE-AUDIT gap item).
- Ledger PIT flakiness (13 failures) root-caused to seed setup, fixed in PR #11 (`ensureSeed`) — **fixed**.

## 8. External blockers
None for this slice (event feed is an internal dependency, not vendor-gated).

## 9. Deviations from chunk file
None logged; chunk file not edited.

## 10. Exit-demo slice
`gradlew.bat :squamed-core:integrationTest --tests "*PlatformIntegrationTests*fullPatientRevenueJourney*"` — last run green in CI run 43548016617.
