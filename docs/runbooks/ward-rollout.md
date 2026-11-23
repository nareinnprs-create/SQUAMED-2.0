# Ward rollout + shadow run (chunk-4 go-live prep)

Chunk-4 ships the IPD/OT/ICU spine. Rollout is ward by ward, each with a **1-week shadow run**
before that ward drops paper. Shadow = the ward records everything in Squamed and on paper;
the paper copy stays the legal record until the ward's shadow exit is signed.

## 1. Before the shadow window

- Ward order is fixed: **pilot ward → ICU → OT** (one ward at a time; ICU after the pilot ward's
  eMAR/assessment flow is stable; OT last because consent + WHO checklist gate the knife).
- Load per ward: beds with type/class/isolation/gender policy, ward assignments, the active
  diet-order list, room tariffs (`ROOM-<bed type>` per hospital) and the ward's staff accounts.
- Confirm the ward has a working printer for MAR sheets, discharge summaries and patient
  instructions (the paper fallback needs the same output).
- Confirm the ICU monitor gateway feeds (04.04 device integration) or record the ward as
  **manual charting only** for the shadow week - never block the ward on a vendor gateway.

## 2. Daily loop (7 days per ward)

1. Morning: charge nurse reconciles the bed board against the ward's physical board
   (occupied / reserved / cleaning / available) and clears housekeeping tasks.
2. Every shift: nurses chart assessments, care plans, vitals, eMAR doses (barcode + high-alert
   double-check) in Squamed; the paper MAR is completed in parallel.
3. Every shift: the ward clerk compares Squamed eMAR against the paper MAR - dose, time, route,
   omission code, second checker. Any divergence is logged with the dose id.
4. ICU: flowsheet/fluid totals and SOFA are compared with the paper chart at handover; device-fed
   rows are compared against the monitor trend.
5. OT: WHO checklist stages, implants (lot/expiry) and consumable charges are compared with the
   paper case record and the charge sheet.
6. Discharge: every discharge runs the checklist (med-rec → signed coded summary → pharmacy
   returns → final bill) and the bed release + housekeeping task is checked on the board.
7. Evening: the ward lead files the day's divergence list (target: zero silent omissions, zero
   uncharged procedures, zero identity-check bypasses).

## 3. Exit criteria for a ward's shadow week

- eMAR: **zero silent omissions** across the week; every late/omitted dose carries a reason and
  the prescriber notification; every high-alert dose has a distinct second checker.
- Barcode blocks: every wrong-wristband/wrong-drug attempt was blocked (no override recorded).
- ICU: flowsheet hours reconcile with the paper chart (no dropped or duplicated hours); fluid
  totals match the paper 24 h totals; alerts ack'd with a clinician name.
- OT: every case has all three WHO stages checked before start/finish; implants carry lot + expiry;
  consumables produced the expected ledger charges.
- Discharge: every summary was signed after med-rec; every final bill matched the manual rate-card
  calculation; every bed went cleaning → available with a housekeeping task closed.
- Blood bank: every issue carried the two-identifier bedside check; traceability returns the full
  donor → grouping → crossmatch → issue → transfusion chain.
- Divergence list closed or explicitly accepted by the ward lead + clinical lead.

## 4. Rollback (per ward)

- Revert that ward to paper MAR/flowsheets; keep entering only admissions/discharges in Squamed.
- eMAR/flowsheet data for the ward is **frozen read-only** - no deletions, no edits; the rollback
  is a forward-only audit event.
- Re-entry: paper records from the rollback window are entered after the fix, marked as
  back-entered with the operator and timestamp.
- ICU and OT rollback separately (device feeds off / checklist on paper) without touching the
  other wards.

## 5. Sign-off

| Ward | Shadow window | Divergences closed | Ward lead | Clinical lead | Verdict |
|---|---|---|---|---|---|
| Pilot ward | - | - | - | - | Pending |
| ICU | - | - | - | - | Pending |
| OT | - | - | - | - | Pending |
