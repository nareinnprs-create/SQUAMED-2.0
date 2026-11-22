# Billing parallel run (chunk-3 go-live prep)

Per `chunk-03-revenue-cycle.md` ROLLOUT/ROLLBACK: **billing runs in parallel with the existing system for 2 weeks; rate cards are loaded and reconciled before cut-over.** Rollback: billing freezes to receipt-only mode; the clinical flow is unaffected (billing is downstream by design).

## 1. Before the parallel window

1. **Rate cards:** load every billable code per payer class (`POST /api/v1/billing/rate-cards`) from the current price list; reconcile each against the existing system's charge master (line by line).
2. **Tax profile:** GSTIN, state code, e-invoice threshold per hospital entity (`POST /api/v1/billing/tax-profile`). Confirm exempt services (consult/lab/imaging) vs goods (pharmacy 5%, devices 12%) with the finance lead.
3. **Tariffs:** load the fallback tariff table for codes without a payer-class card.
4. **Users:** billing staff, billing supervisor (discounts >10%, credit notes, refunds), medical coders.
5. **Opening balances:** take deposits/advances from the existing system into SQUAMED as deposits (or note them as out-of-scope for the parallel period).

## 2. Daily loop (2 weeks)

1. Run the same day's billing in SQUAMED and the existing system.
2. Compare, per patient/encounter: charges captured (event + manual), invoice totals, tax split, discounts, payments.
3. **Charge capture check:** every lab release / imaging completion produces exactly one ledger row (no duplicates, no missing) — `GET /api/v1/billing/charges`.
4. **Tax check:** exempt vs GST lines match the finance expectation; e-invoice flags fire only above the threshold.
5. **Collections:** counter cash-up closes with zero variance (or a documented note); UPI/card statements reconcile (`POST /api/v1/billing/recon/statements`).
6. **Integrity:** run `POST /api/v1/integrity/scan` each morning; triage every finding with a note; the goal is a shrinking open list, not zero on day 1 (historical rows will flag).
7. **GL:** export the day's journal (`GET /api/v1/billing/journal/export`) and hand it to finance for comparison against the existing system's day book.

## 3. Exit criteria for the parallel period

- 10 consecutive working days where SQUAMED totals match the existing system within the agreed tolerance (target: zero unexplained variance).
- Every cash-up closes balanced or with a documented, approved variance.
- Every statement reconciles (`MATCHED`) or has a triaged variance finding.
- Integrity findings: no unresolved high-severity finding older than 48h.
- GL export accepted by finance for the full window (debits = credits every day).
- No billing-driven clinical downtime (billing is downstream; a billing failure must never block care).

## 4. Rollback

Freeze SQUAMED billing to receipt-only mode (no new invoices/refunds), keep the clinical flow running, and continue on the existing billing system. Deposits/refunds already taken are reconciled from the SQUAMED ledger before any re-enable.

## 5. Sign-off

| Item | Owner | Evidence |
|---|---|---|
| Rate cards + tax profile reconciled | Finance lead | Line-by-line reconciliation sheet |
| 2-week parallel run | Billing manager | Daily comparison + cash-up/recon logs |
| Integrity backlog triaged | Billing supervisor | Findings list + resolution notes |
| GL export accepted | Finance controller | Signed daily exports |
| Go-live decision | Owner (B2) | This runbook + `reports/chunk-03/FINAL.md` |
