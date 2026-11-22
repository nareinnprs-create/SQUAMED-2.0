# Section 03.03 — Tax engine (chunk 3)

**Gate-run assessment of the CURRENT tree** @ PR #86 (chunks 0–2 + 03.01/03.02 merged + this PR). Verdict: **COMPLETE → GATE-12 PASS (partial: chunk-3 exit demo slice owed at 03.07)**. CI evidence: **run 44973751492 — all 7 gates green**. Named PIT `taxEngineCoverage` green in the same run.

## 1. Scope
Per `chunk-03-revenue-cycle.md` §3: exempt healthcare services vs goods (GST 5%/12%), HSN codes, GSTIN per hospital entity, e-invoicing threshold flow, credit notes with an approval chain. Refund workflow (money out) belongs to 03.06 collections.

## 2. Key artifacts
**Migration `V74__tax_engine.sql`** (additive; rollback note inside):
- `billing.hospital_tax_profile` — GSTIN, state code, GST-registered flag, e-invoice threshold (one per hospital).
- `billing.charge` += `tax_category` (`EXEMPT_SERVICE|GOODS_GST_5|GOODS_GST_12`) + `hsn_code`; `billing.invoice` += `e_invoice_required`.
- `billing.tax_line` — per invoice (HSN, category, taxable, rate, tax, exempt flag); `billing.credit_note` — amount, reason, issuer, status.
- Permission `billing:approve:creditnote` → BILLING_SUPERVISOR + SYSTEM_ADMIN.

**Behaviour:**
- **Classification:** manual charges default by category — `pharmacy/goods` → 5%, `device(s)` → 12%, else exempt; explicit `taxCategory`/`hsnCode` override; event-captured lab/imaging charges are exempt with SAC 9993.
- **Tax at bill time:** lines grouped by (HSN, category) into tax lines; `tax = round(taxable × rate / 100, 2)`; invoice `taxQar` = sum; `total = subtotal − discount + tax`.
- **E-invoicing:** invoice flagged `eInvoiceRequired` at/above the hospital threshold.
- **Credit notes:** supervisor-gated, reason mandatory, capped at the invoice total; the note reduces the invoice total and appears on the invoice detail; finalized lines are never edited.
- OpenAPI snapshot regenerated (`2b2a4a59…`), codegen stamped; `eInvoiceRequired` carries an explicit JSON name (Jackson bean-naming edge).

## 3. Gap closure
- ✔ Exempt/GST 5%/12% classification + HSN/SAC
- ✔ Tax lines per invoice with rounding
- ✔ GSTIN + e-invoice threshold per hospital
- ✔ Supervisor-gated credit notes with reasons
- ✔ 40-case tax correctness table in the PIT
- ✔ PIT 03.03: `taxEngineCoverage` PASS

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | PIT covers all three categories, mixed bills, threshold, credit notes, 40-case table |
| 2 Zero-stub | PASS | CI branch run 44973751492 |
| 3 Types | PASS | CI branch run 44973751492 |
| 4 Lint + format | PASS | CI branch run 44973751492 |
| 5 Tests | PASS | CI branch run 44973751492; named PIT below |
| 6 Accessibility | PASS | CI branch run 44973751492 (web untouched) |
| 7 Design system | PASS | CI branch run 44973751492 |
| 8 Contracts | PASS | Snapshot + codegen committed; parity green |
| 9 Data | PASS | V74 additive + rollback note; one tax profile per hospital; tax lines immutable with the invoice |
| 10 Audit | PASS | Credit notes record issuer + reason; tax lines carry the rate/taxable provenance |
| 11 Docs | PASS | This report; LEDGER row 03.03 COMPLETE; tracker updated |
| 12 Exit item | PASS (partial) | Tax/credit-note slice of the 03.07 exit demo |

## 5. Tests
Named PIT **`taxEngineCoverage`** (new): exempt consult → 0% line with SAC 9993; pharmacy → 5%/HSN 3004; device → 12%/HSN 9018; mixed bill 350 subtotal + 11 tax = 361 with three tax lines; 1500 invoice flags `eInvoiceRequired`; staff credit note 403, blank reason 400 `CREDIT_NOTE_REASON_REQUIRED`, over-total 400 `CREDIT_NOTE_EXCEEDS_INVOICE`, supervisor note 50 → `CN-…`, invoice total 55, listed; **40-case table** (3 categories × quantities 1-4 × varied units, HALF_UP rounding) all match expected tax and total.

Regressions green: `billingEngineCoverage`, `chargeCaptureLedgerCoverage`, `financialAndOperationsCoverage`, `fullPatientRevenueJourney`.

## 6. Accessibility
Web surface untouched by this PR; repo-wide axe gate in CI.

## 7. Dual review — adversarial pass
1. **Discount vs tax:** tax is computed on the pre-discount line totals and the discount reduces the subtotal — a documented simplification; pre-known discounts (e.g., package deals) can move to a discounted-taxable model later.
2. **Rounding:** HALF_UP to 2 decimals per tax line (not per invoice) — matches Indian invoice practice; the 40-case table exercises fractional units.
3. **Threshold:** `eInvoiceRequired` is a flag, not a submission — IRP integration is out of scope (noted).
4. **Credit notes** never mutate lines; the invoice total is reduced and the note is listed; over-total and non-supervisor attempts are rejected.
5. **Event-captured charges** are exempt by default (lab/imaging) — pharmacy events do not exist yet; when they do, the consumer must classify goods.
6. **Tax profile edits** apply to future invoices only (existing tax lines are stored).
7. **Cross-test interference:** tax profiles are hospital-scoped; the PIT uses its own hospital/patients/codes.

## 9. Deviations from contract
- No IRP/e-invoice submission (flag only; adapter with the tax filing pack).
- Refunds (money out) in 03.06; credit notes ship here.
- Tax rates are constants (0/5/12) — slab changes need a migration or a rate table (noted).

## 8. Session trail
- `eInvoiceRequired` was absent from the JSON until an explicit `@get:JsonProperty` (Jackson bean-naming edge with the `eI…` prefix) — fixed.
- Docker Desktop died twice; compose restarted, postgres healthy.
