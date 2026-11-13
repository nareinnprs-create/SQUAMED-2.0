# CHUNK 03 — REVENUE CYCLE
**Status:** PLANNED · **Window:** Aug → Nov 2027 (12 wk) · **Depends on:** chunk 01 exit (can overlap chunk 2 by one sprint with team of 8)
**Stack entering:** Tax engine config, TPA payer adapters

## GOAL
**Clinical activity becomes compliant money:** bill → tax → claim → settlement, with leakage detection live.

## IN SCOPE
1. **Charge capture:** unified ledger fed by events from chunks 1–2 (`ChargeablePerformed`, consults, procedures, room-time) + manual charge entry (audited, reason mandatory).
2. **Billing engine:** rate cards per payer class (general, CGHS/ESIC/TPA-tied, corporate, PM-JAY package codes), packages (surgical/diagnostic bundles with versioning — a package version applies at order time, not bill time), estimates, deposits & advances, discounts (role-gated, reason-captured), professional fee split (hospital vs doctor share config), consumables, consultation fee (chunk 1 receipt migrates into this engine).
3. **Tax engine (India):** service lines flagged exempt (healthcare services) vs goods (pharmacy/devices, GST 5%/12% slabs), HSN codes, GSTIN per hospital entity, e-invoicing threshold flow, credit notes, refund workflows with approval chain.
4. **Insurance/TPA v1:** payer registry, policy/eligibility capture, **cashless pre-auth workflow** (request → approval/quota tracking), claim assembly per payer adapter (TPA portal formats; PM-JAY package claim path; CGHS formats), claim status tracking, query management, denial recording with reason codes, settlement & reconciliation against bank.
5. **HIM coding:** ICD-10 coding worklist on signed encounters/discharges, coder queries to clinicians (task engine), coding audit sampling.
6. **Revenue integrity engine (blueprint §26, now real):** rules on the event ledger vs bills — missing charges (performed-but-unbilled), duplicates, unbilled orders, docs-gaps (unsigned note → uncoded), coding inconsistency (code vs note findings mismatch flag). Daily digest + task creation.
7. **Collections & recon:** counter collection modes (cash/UPI/card), shift-wise cash-up, payment gateway integration (UPI/card), reconciliation report, refunds.
8. **Financial posting:** summarized GL journal entries per day/book (export-ready for Tally/ZOHO Books — **do not build a full GL; blueprint §37 full GL is chunk 6+ scope decision**).

## OUT OF SCOPE
Full GL/AP/AR/budget → chunk 7. Group finance consolidation + multi-currency/FX → chunk 8. Pharmacy GST e-way bills → chunk 7 (tax filing packs).

## DATA MODEL (key additions)
`charge_ledger_entry(source event ref), rate_card, package(+version), bill(+lines, status), estimate, deposit, discount(authorized_by, reason), tax_line(hsn, slab, exempt flag), credit_note, payer, policy, preauth_request(status), claim(+status history), denial(reason code), coding_assignment, coder_query, integrity_finding(resolved_by), payment, shift_cashup, recon_statement, gl_journal(export)`

## APIS
- `/billing/estimates|bills|credit-notes`, `/bills/{id}:finalize|settle`
- `/insurance/policies`, `/insurance/preauth`, `/claims:assemble|submit|update-status`, `/claims/{id}/denials`
- `/him/coding-queue`, `/him/queries`
- `/integrity/findings:triage`
- `/payments:collect|reconcile`, `/finance/journal:export`

## EVENTS
`ChargeCaptured, EstimateCreated, BillFinalized, BillSettled, DepositTaken, DiscountApproved, CreditNoteIssued, PreauthRequested/Approved/Rejected, ClaimSubmitted, ClaimQueried, ClaimDenied, ClaimSettled, CodingCompleted, IntegrityFindingDetected/Resolved, PaymentReconciled`

## PERMISSIONS (adds)
Billing clerk: bill/settle within limits. Billing supervisor: discounts >X%, credit notes, refunds. Coder: coding queue. Auditor: all read + integrity triage view. Doctor: coder queries inbox.

## SAFETY / COMPLIANCE
- Financial integrity: bill lines immutable after finalize; corrections = credit note (audit chain).
- Discount authorization matrix enforced; cash drawer variance > threshold → task + security event.
- Claim assembly rejects on unsigned encounter (hook from chunk 1) or uncoded discharge.
- DPDP: patient financial data minimization in exports.

## TESTS
- Tax correctness table: 40 cases (exempt service, GST pharmacy line, mixed bill, credit note, package pricing with version pin).
- Claim adapter golden files: one TPA format + PM-JAY package claim + CGHS format — byte-stable output against fixtures.
- Integrity rules: seeded leakage scenarios (unbilled dressing, duplicate charge, unsigned-note bill block) all detected with <5% false-positive on replay day.
- Money math: integer-paise arithmetic property tests (no float), rounding rules documented.

## EXIT CRITERIA (demo scenario)
OPD day + labs + pharmacy from chunks 1–2 → charges auto-captured → bill with correct exempt/tax split → insurance patient: pre-auth approved → claim assembled per adapter → simulated denial (missing doc) → query task → resolved → resubmitted → settled & reconciled → integrity engine flags one unbilled charge → supervisor resolves with audit → daily GL journal exported → cash-up variance report clean.

## ROLLOUT / ROLLBACK
Billing parallel-run with existing system 2 weeks; rate cards loaded and reconciled before cut-over. Rollback: billing freezes to receipt-only mode; clinical flow unaffected (billing is downstream — architecturally isolated for exactly this reason).

## OPEN DECISIONS
- Payment gateway + UPI PSP selection.
- Pilot payer list (which TPAs first — adapter effort per payer is the schedule risk of this chunk).
