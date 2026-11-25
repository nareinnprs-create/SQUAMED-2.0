## 07.04 — Full financials

General ledger, AP/AR, bank reconciliation, cost centres, budgets, fixed-asset depreciation, GST packs and month close over the operational billing ledger.

### Schema (`V100`, additive)
- `finance.gl_account`, `cost_centre`, `journal_entry`, `journal_line`.
- `finance.ap_invoice`, `ap_payment`; `bank_account`, `bank_statement_line`.
- `finance.fixed_asset`, `depreciation_run`; `budget`, `period_close`, `gst_return`.
- Permissions `finance:read|write|close:finance`; role `FINANCE_MANAGER`.

### Backend (`/api/v1/finance`)
- **GL:** standard chart per hospital, manual balanced journals, idempotent operational posting (invoice → AR/revenue/GST, payment → cash/AR, refund → AR/cash, AP invoice/payment, asset capitalization).
- **AP:** invoice + part-payment lifecycle with overpay guard.
- **Bank:** accounts, statement import, exact-amount ±3-day auto-match.
- **Budgets:** upsert + budget-vs-actual.
- **Assets:** create + monthly depreciation (capped at cost, run-once) + accumulated depreciation.
- **Reports:** trial balance, P&L, balance sheet, AR aging, flat 3-month forecast.
- **GST:** period pack (taxable, CGST/SGST split, payload) + file-once.
- **Close:** posts operations + depreciation, checks trial balance and GL-vs-operational revenue/tax variance (≤0.01), close-once.

### Evidence
- New PIT `fullFinancialsCoverage`: 1000 + 5% GST invoice, payment 1050, refund 50, AP 300/100; 5 idempotent postings; TB balanced (AR 50, cash 900); budget variance 200; asset 12,000/12 → 1,000 depreciation; P&L −300; BS balanced; aging 50; forecast 3 months; GST 1,000/25/25 file-once 409; bank match 1/1; close variance 0.00 close-once 409; invalid period 400; cross-tenant TB 0.
- OpenAPI snapshot regenerated (`09fad159…`), codegen stamped; api-client 5/5.

### Deviations
- Single cash & bank control account (no per-bank GL accounts).
- GST split assumes intra-state (CGST/SGST half, IGST 0); inter-state determination not modelled.
- Credit notes are not auto-posted to GL (manual journal); close excludes them.
- No inventory/COGS automation; AP invoices post to Supplies.
- Forecast is a flat run-rate, not a statistical model.
- AP has no three-way match/GRN link; bank matching is exact amount ±3 days.
- No multi-entity consolidation (chunk 8).
