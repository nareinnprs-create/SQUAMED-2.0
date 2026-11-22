# Section 03.02 — Billing engine (chunk 3)

**Gate-run assessment of the CURRENT tree** @ PR #84 (chunks 0–2 + 03.01 merged + this PR). Verdict: **COMPLETE → GATE-12 PASS (partial: chunk-3 exit demo slice owed at 03.07)**. CI evidence: **run 44970294522 — all 7 gates green**. Named PIT `billingEngineCoverage` green in the same run.

## 1. Scope
Per `chunk-03-revenue-cycle.md` §2: rate cards per payer class, packages with versioning (a version applies at order/capture time, not bill time), estimates, deposits & advances, discounts (role-gated, reason-captured). Tax (03.03), insurance/TPA (03.04), professional-fee split, consumables, and the consultation-fee migration are later sections or already-covered paths.

## 2. Key artifacts
**Migration `V73__billing_engine.sql`** (additive; rollback note inside):
- `billing.rate_card` — hospital + payer class (`general|cghs|esic|tpa|corporate|pmjay`) + code + price; partial unique index keeps one active card per tuple.
- `billing.billing_package` + `billing.package_version` (`package_version` number, price, effective_from, unique per package); `billing.charge.package_version_id` pins the version at capture.
- `billing.invoice` += `payer_class`, `discount_reason`; `billing.invoice_line` += `base_price_qar`, `rate_card_id`.
- `billing.estimate` (items snapshot TEXT, total, expiry, status) and `billing.deposit` (held → applied to one invoice).
- Role `BILLING_SUPERVISOR` + permission `billing:approve:discount` (supervisor + SYSTEM_ADMIN).

**Behaviour:**
- **Rate cards at bill time:** issuing an invoice resolves the active card for (hospital, payer class, charge code); the line records the repriced unit, the captured `base_price_qar`, and the `rate_card_id`. No card → captured price.
- **Packages at capture time:** `POST /billing/charges` with `packageCode` resolves the active version, prices the line from it, stores `packageVersionId`; a later version never reprices the pinned charge.
- **Estimates:** create with an item snapshot + total + optional expiry; list by patient/hospital.
- **Deposits:** take (held, `billing:collect:payment`), apply `min(deposit, due)` to one invoice (status recomputed), re-apply rejected, patient mismatch rejected.
- **Discounts:** reason mandatory (`DISCOUNT_REASON_REQUIRED`); >10% of subtotal requires `billing:approve:discount` (`DISCOUNT_APPROVAL_REQUIRED` otherwise).
- OpenAPI snapshot regenerated (6 new paths; `b76b4879…`), codegen stamped.

## 3. Gap closure
- ✔ Payer-class rate cards with repricing at bill time
- ✔ Versioned packages pinned at capture
- ✔ Estimates
- ✔ Deposits applied to invoices
- ✔ Role-gated discounts with reasons
- ✔ PIT 03.02: `billingEngineCoverage` PASS

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | PIT covers repricing/fallback, package pinning, estimates, deposit application, discount gates |
| 2 Zero-stub | PASS | CI branch run 44970294522 |
| 3 Types | PASS | CI branch run 44970294522 |
| 4 Lint + format | PASS | CI branch run 44970294522 |
| 5 Tests | PASS | CI branch run 44970294522; named PIT below |
| 6 Accessibility | PASS | CI branch run 44970294522 (web untouched) |
| 7 Design system | PASS | CI branch run 44970294522 |
| 8 Contracts | PASS | Snapshot + codegen committed; parity green |
| 9 Data | PASS | V73 additive + rollback note; partial unique rate-card index; unique package version; tenant-scoped reads |
| 10 Audit | PASS | Discounts record reason + issuer; deposits record receiver + application; package pins are immutable |
| 11 Docs | PASS | This report; LEDGER row 03.02 COMPLETE; tracker updated |
| 12 Exit item | PASS (partial) | Billing slice of the 03.07 exit demo |

## 5. Tests
Named PIT **`billingEngineCoverage`** (new): TPA rate card reprices 100 → 120 (base 100 + card ref on the line) and general fallback keeps 90; package v1 pins 500, v2 prices a new capture 600 and the v1 charge remains 500; estimate total 250 with `EST-…` number and listing; deposit 200 applied to a 100 invoice → paid/due 0/applied, second apply 400 `DEPOSIT_NOT_HELD`; discount without reason 400 `DISCOUNT_REASON_REQUIRED`, 5% for staff with reason recorded, 50% blocked for staff (`DISCOUNT_APPROVAL_REQUIRED`) and approved by BILLING_SUPERVISOR with total 500.

Regressions green: `financialAndOperationsCoverage`, `fullPatientRevenueJourney`, `chargeCaptureLedgerCoverage`.

## 6. Accessibility
Web surface untouched by this PR; repo-wide axe gate in CI.

## 7. Dual review — adversarial pass
1. **Repricing at bill time** uses the invoice's payer class and the charge's captured code; the base price stays on the line so audits can see both numbers.
2. **Rate-card edits** update the active card in place (single active per tuple); historical invoice lines keep their `rate_card_id` and price — no retroactive repricing.
3. **Package pinning** is stored per charge; deleting/deactivating a version does not alter captured prices (FK restrict prevents orphaning).
4. **Discount threshold** is a service constant (10%) applied on the repriced subtotal; the approval check uses the evaluator (supervisor or admin).
5. **Deposit application** caps at the invoice due, recomputes status, and is single-use; a cancelled invoice cannot take a deposit.
6. **Estimate numbers** use a count-based sequence — collisions are possible only under concurrent creates; the number is display-only (the id is the key). Noted as a minor limit.
7. **Cross-test interference:** all new reads are patient/hospital scoped; the PIT uses its own patients and codes.

## 9. Deviations from contract
- Tax lines (03.03), insurance/TPA pre-auth and claims (03.04), professional-fee split config, and the estimate→invoice conversion workflow are later sections; consumables already ride charge categories and the consult fee is already a ledger charge.
- The discount threshold is a constant, not per-hospital config (rate-card admin UI is future work).

## 8. Session trail
- V73 first apply failed on `identity.role.display_name` (NOT NULL) — fixed the insert columns; the estimate table was created with a `jsonb` column that rejected a String parameter, so `items_json` moved to TEXT and the partially-applied table was dropped before re-running.
- Docker Desktop died twice during the run; compose restarted, postgres healthy.
- The feature commit briefly landed on the docs branch (branch not created first) — moved to `build/chunk-03-billing-engine`, docs branch reset to origin.
