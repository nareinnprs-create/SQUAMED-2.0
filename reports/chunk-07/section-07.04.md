# Section 07.04 — Full financials (chunk 7)

**Verdict: SECTION REVIEW → local gates green (CI run pending).** General ledger, AP/AR, bank reconciliation, cost centres, budgets, fixed-asset depreciation, GST packs and month close over the operational billing ledger.

## 1. Scope
Per `chunk-07-full-depth.md` item 4 / `20-CHUNK7-SECTIONS.md` 07.04: GL, AP, AR, cash/bank recon, cost centres, budget vs actual, forecast, asset accounting, financial reporting, GST return filing packs, audit integration; month-close simulation ties the trial balance to the operational ledger.

## 2. Key artifacts
**Migration `V100`**: `finance.gl_account`, `cost_centre`, `journal_entry`, `journal_line`, `ap_invoice`, `ap_payment`, `bank_account`, `bank_statement_line`, `fixed_asset`, `depreciation_run`, `budget`, `period_close`, `gst_return`; `finance:read|write|close:finance` + `FINANCE_MANAGER`.

**Backend**
- `finance/domain/FinanceEntities.kt`, `repository/FinanceRepositories.kt`, `service/FinanceService.kt`, `api/FinanceController.kt` (29 endpoints under `/api/v1/finance`).
- `billing` repositories gained two period queries (all payments, tax lines by invoice period).

## 3. Gap closure
- ✔ Standard chart of accounts seeded per hospital; manual journals.
- ✔ Operational posting: invoices → AR/revenue/GST, payments → cash/AR, refunds → AR/cash, AP invoices/payments, asset capitalization.
- ✔ AP invoice + part-payment lifecycle with overpay guard.
- ✔ Bank accounts + statement import + exact-amount ±3-day auto-match.
- ✔ Cost centres, budgets, budget-vs-actual.
- ✔ Fixed assets with monthly depreciation run (capped at cost) and accumulated depreciation.
- ✔ Trial balance, P&L, balance sheet, AR aging, flat run-rate forecast.
- ✔ GST return pack per period (taxable value, CGST/SGST split, payload) + file-once.
- ✔ Month close: posts operations + depreciation, checks trial balance and GL-vs-operational revenue/tax variance (≤0.01), close-once.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | `fullFinancialsCoverage` green |
| 2 Zero-stub | PASS | new files grep clean; CI |
| 3 Types | PASS | `compileKotlin` + `compileIntegrationTestKotlin` |
| 4 Lint + format | PASS | `ktlintCheck` clean |
| 5 Tests | PASS (local) | full chain BUILD SUCCESSFUL 15m15s — 104 PIT methods + 56 unit tests, 0 failures; JaCoCo instruction 84.71% (>=0.80) |
| 6 Accessibility | PASS | No UI change |
| 7 Design system | PASS | No UI change |
| 8 Contracts | PASS | snapshot regenerated from live `/api-docs`; codegen stamped `09fad159…` |
| 9 Data | PASS | V100 additive; cross-tenant GL assertion |
| 10 Audit | PASS | Controller-mediated writes; journals carry poster |
| 11 Docs | PASS | This report; LEDGER; tracker |
| 12 Exit item | PASS (partial) | 07.11 month-close slice |

## 5. Tests
Full suite: `integrationTest test jacocoTestReport jacocoTestCoverageVerification` — BUILD SUCCESSFUL, 104 PIT methods + 56 unit tests, 0 failures, JaCoCo instruction 84.71%.

Named PIT **`fullFinancialsCoverage`**:
- Chart seed idempotent (13 accounts); taxable invoice 1000 + 5% GST = 1050; payment 1050; refund 50; AP invoice 300 + part payment 100.
- Post-operations posts 5 entries and is idempotent; trial balance balances; AR = 50, cash = 900.
- Budget vs actual: supplies actual 300, budget 500, variance 200.
- Asset 12,000 / 12 months → depreciation 1,000; P&L revenue 1,000, expense 1,300, net −300; balance sheet balances.
- AR aging 50 current; forecast 3 months at 1,000 revenue.
- GST pack taxable 1,000, CGST 25, SGST 25; file-once 409.
- Bank auto-match 1 matched / 1 unmatched.
- Month close CLOSED variance 0.00; close-once 409; invalid period 400; cross-tenant trial balance 0.

## 6. Accessibility
No UI change.

## 7. Dual review — adversarial pass
1. **Double entry** — every journal must balance before save; posting is idempotent per (sourceType, sourceId).
2. **Money safety** — AP overpay blocked; refunds ride the existing capped billing path; close variance blocks on >0.01.
3. **Close integrity** — trial balance must balance; close-once; operational vs GL revenue/tax reconciliation.
4. **Depreciation** — monthly charge capped at remaining cost; run-once per period; accumulated never exceeds cost.
5. **GST** — FILED returns are immutable (409); payload carries GSTIN + HSN rows.
6. **Bank** — matching is exact amount within ±3 days and each journal entry matches once.
7. **Tenant** — every query is hospital-scoped; cross-tenant report returns empty.

## 8. Deviations from contract
- Single cash & bank control account (no per-bank GL accounts).
- GST split assumes intra-state (CGST/SGST half, IGST 0); inter-state determination is not modelled.
- Credit notes are not auto-posted to GL (manual journal); month close does not include them.
- No inventory/COGS automation; AP invoices post to Supplies expense.
- Forecast is a flat 3-month run-rate, not a statistical model.
- AP has no three-way match/GRN link; bank matching is exact amount ±3 days.
- No multi-entity consolidation (chunk 8).

## 9. Session trail
- Local Docker compose restarted after an engine stop; named PIT green after adding forecast + AR aging.
- Full backend chain green: 104 PIT methods + 56 unit tests, JaCoCo 84.71%.
