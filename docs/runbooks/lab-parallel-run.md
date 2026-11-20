# Lab parallel-run + pharmacy stock parallel count (chunk-2 go-live prep)

Per `chunk-02-diagnostics-pharmacy.md` ROLLOUT/ROLLBACK: **lab runs paper + LIS in parallel for 2 weeks; pharmacy starts its stock count in parallel.** Rollback: LIS read-only + paper reports; the pharmacy ledger is the source of truth from go-live (no rollback without a stock recount).

## 1. Lab parallel run (2 weeks)

**Goal:** every lab order is processed twice — once on paper (existing process) and once in SQUAMED — and the two outputs are compared daily until they agree.

### Daily loop
1. **Order intake:** the ward/OPD paper requisition and the SQUAMED `POST /api/v1/lab/orders` are both created for the same patient. The LIS order number is written on the paper requisition (cross-reference both ways).
2. **Specimen:** the SQUAMED barcode label is affixed **in addition to** the existing paper label. Collect + receive in the LIS at the same time as the physical handoff (custody trail must reflect reality).
3. **Result entry:** the analyser result is entered in SQUAMED (manual or analyzer feed) **and** reported on paper. Critical values are phoned per the existing escalation policy; the SQUAMED critical result is acknowledged with the same note.
4. **Validation:** the pathologist validates/releases in SQUAMED and signs the paper report. Report PDFs are printed from SQUAMED and stapled behind the paper report — the paper report stays the legal record for the parallel period.
5. **End of day:** run `GET /api/v1/lab/tat?days=1` and compare against the paper TAT log; export the day's released orders and reconcile counts (ordered vs released vs amended).

### Daily reconciliation checklist
- [ ] Order counts match (paper requisitions vs SQUAMED orders)
- [ ] Every released result has a paper counterpart and vice versa
- [ ] Critical results: SQUAMED OPEN → ACKNOWLEDGED with the same ack time as the phone call
- [ ] Amendments: every paper correction has a SQUAMED amendment with a reason
- [ ] TAT: SQUAMED order→release minutes within ±15% of the paper log
- [ ] Quarantine/integration errors reviewed (`/api/v1/admin/events/errors?integration=analyzer`) — zero silent drops

### Exit criteria for the parallel period
- 10 consecutive working days with zero unexplained mismatches, or every mismatch root-caused and fixed.
- Zero critical results unacknowledged beyond SLA on any day.
- Analyzer feed: zero quarantined messages without an operator action logged.

### Rollback
LIS set read-only (stop order creation at the API), paper process continues as today; released PDFs already delivered remain valid. No data deletion.

## 2. Pharmacy parallel stock count

**Goal:** the SQUAMED stock ledger becomes the source of truth without a stock-out or an unexplained variance.

### Procedure
1. **Freeze:** at a quiet hour, stop dispensing in SQUAMED (dispense only against the paper ledger) and count every batch physically (drug, batch, expiry, quantity).
2. **Load:** create/update stock rows (`POST /api/v1/pharmacy/stock`, `PUT /api/v1/pharmacy/stock/{id}`) to the counted quantities; every adjustment carries a note. The ledger `INTAKE`/`ADJUST` rows are the audit trail.
3. **Parallel week:** dispense in SQUAMED **and** on the paper ledger; at end of day compare `quantityOnHand` per batch against the paper count. Variance > 0 triggers a recount and a ledger note.
4. **Controlled register:** every controlled dispense in SQUAMED records the second authorizer; reconcile against the statutory register daily.
5. **Expiry sweep:** batches within the 30-day near-expiry window are flagged (`NEAR_EXPIRY_BLOCK` on dispense) and physically segregated.

### Exit criteria
- 5 consecutive days with zero variance on all batches, or every variance explained by a documented ledger note.
- Controlled register reconciles 1:1 with the statutory register for the period.
- No dispense past the prescribed quantity (over-dispense guard) and no near-expiry dispense.

### Rollback
The ledger stays authoritative; rollback means reverting to the paper ledger and freezing SQUAMED writes, then a fresh physical count before any re-enable. Never roll back without a recount.

## 3. Sign-off

| Item | Owner | Evidence |
|---|---|---|
| Lab parallel run (2 weeks) | Lab manager + pathologist | Daily reconciliation sheets + TAT comparison |
| Pharmacy parallel count | Chief pharmacist | Variance log + controlled register reconciliation |
| Clinical acceptance of safety rules (interaction dataset, dose rules) | Clinical lead | 02.07 eval report + local rule review |
| Go-live decision | Owner (B2) | This runbook completed + `reports/chunk-02/FINAL.md` |
