# Section 03.06 — Collections + GL export (chunk 3)

**Gate-run assessment of the CURRENT tree** @ PR #88 (chunks 0–2 + 03.01-03.03/03.05 merged + this PR). Verdict: **COMPLETE → GATE-12 PASS (partial: chunk-3 exit demo slice owed at 03.07)**. CI evidence: **run 44979984861 — all 7 gates green**. Named PIT `collectionsAndGlCoverage` green in the same run.

## 1. Scope
Per `chunk-03-revenue-cycle.md` §7: counter collection modes (cash/UPI/card), shift-wise cash-up, reconciliation report, refunds. §8: summarized GL journal export per day/book (export-ready; full GL is chunk 6+).

## 2. Key artifacts
**Migration `V76__collections_gl.sql`** (additive; rollback note inside):
- `billing.shift_cashup` — cashier shift with opening float, cash collected, counted cash, variance, `OPEN|CLOSED`.
- `billing.recon_statement` — method + period, statement vs recorded totals, variance, `MATCHED|VARIANCE`.
- `billing.refund` — payment/invoice refs, amount, reason, issuer.

**Behaviour:**
- **Cash-up:** one open shift per cashier; close → `expected = float + cash payments by the cashier since open`, variance = counted − expected; |variance| > 50 raises a high task (`relatedType=CASHUP`); double close rejected.
- **Reconciliation:** statement total vs recorded payments per method/period; `MATCHED` only at zero variance.
- **Refunds:** supervisor-gated, reason mandatory, capped at the payment; payment → `refunded`, invoice paid/due/status recompute.
- **GL export:** summarized double-entry lines per day — payments (debit `CASH_<METHOD>_IN`, credit `RECEIVABLE`), invoices (debit `RECEIVABLE`, credit `REVENUE` + `TAX_PAYABLE`), refunds (debit `REVENUE_REFUND`, credit the method account); totals balance.
- OpenAPI snapshot regenerated (`cc91af05…`), codegen stamped.

## 3. Gap closure
- ✔ Shift cash-up with variance detection + task
- ✔ Reconciliation statements
- ✔ Supervisor-gated refunds
- ✔ Summarized GL journal export that balances
- ✔ PIT 03.06: `collectionsAndGlCoverage` PASS

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | PIT covers cash-up lifecycle, variance task, recon matched/variance, refund gates, journal balance |
| 2 Zero-stub | PASS | CI branch run 44979984861 |
| 3 Types | PASS | CI branch run 44979984861 |
| 4 Lint + format | PASS | CI branch run 44979984861 |
| 5 Tests | PASS | CI branch run 44979984861; named PIT below |
| 6 Accessibility | PASS | CI branch run 44979984861 (web untouched) |
| 7 Design system | PASS | CI branch run 44979984861 |
| 8 Contracts | PASS | Snapshot + codegen committed; parity green |
| 9 Data | PASS | V76 additive + rollback note; one open shift per cashier; refunds capped |
| 10 Audit | PASS | Cash-up closure, refund issuer/reason, recon statements all persist actor + timestamps |
| 11 Docs | PASS | This report; LEDGER row 03.06 COMPLETE; tracker updated |
| 12 Exit item | PASS (partial) | Collections/GL slice of the 03.07 exit demo |

## 5. Tests
Named PIT **`collectionsAndGlCoverage`** (new, isolated hospital): cash-up float 200 + 100 collected, counted 300 → variance 0 CLOSED; second open rejected; double close rejected; second shift short −90 raises a CASHUP task; recon cash 200 → MATCHED, UPI 5 vs 0 → VARIANCE; staff refund 403, blank reason 400 `REFUND_REASON_REQUIRED`, over-payment 400 `REFUND_EXCEEDS_PAYMENT`, supervisor 40 → payment refunded, invoice 60 paid/40 due partial, refund listed; journal export cash debit + revenue credit + 40 refund debit and **debits = credits**.

Regressions green: `financialAndOperationsCoverage`, `fullPatientRevenueJourney`, `billingEngineCoverage`, `taxEngineCoverage`.

## 6. Accessibility
Web surface untouched; repo-wide axe gate in CI.

## 7. Dual review — adversarial pass
1. **Cash-up scope** is per cashier (receivedBy) inside the shift window — a cashier who collects before opening the shift loses it from the expected total; the workflow is open-then-collect (PIT proves the ordering).
2. **Recon** is hospital+method+period; the PIT isolates its hospital so other tests' cash cannot pollute statements.
3. **Refunds** reduce the invoice paid and re-open the due; a refund on a fully-paid invoice flips it back to partial (PIT).
4. **Journal balance** holds for payments/invoices/refunds; deposits and credit notes are not posted to the journal (they adjust the ledger) — documented limit of the summary export.
5. **Tolerance** is a constant (50); per-hospital config is future work.
6. **PSP integration** (E4) is not built: UPI/card are recorded manually and reconciled against statements.
7. **Cross-test interference** was real and is fixed by the isolated hospital (recon is hospital-wide).

## 9. Deviations from contract
- Payment-gateway/UPI PSP integration is the E4 procurement item — not built.
- Summarized journal, not a full GL (contract: full GL chunk 6+).
- Deposits/credit notes are not journal-posted (summary scope).

## 8. Session trail
- First PIT run: collected 0 because the payment preceded the shift open — reordered the legs.
- Second: recon recorded 300 (other tests' cash in the shared hospital) — moved the PIT to an isolated hospital.
- Docker Desktop died twice; compose restarted, postgres healthy.
