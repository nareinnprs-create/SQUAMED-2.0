# Chunk 04 PIT tracker

Seed frame: 00.00. Keep one line per PIT.

| ID | Description | Status | Run | Notes |
|----|-------------|--------|-----|-------|
| 04.01 | admissionBedManagementCoverage | PASS | 44989139607 | COMPLETE | isolation/gender allocation rejections; admission occupies bed + created/admitted/bed-assigned events; plan-discharge; transfer rule + trace; early discharge 409 DISCHARGE_CHECKLIST_INCOMPLETE; summary ICD guard + double-sign 400; pharmacy+bill legs; discharge → bed cleaning + HOUSEKEEPING task; unsettled invoice 409 DISCHARGE_BILL_UNSETTLED; 8 catalog entries EMITTED |
| 04.02 | emarNursingCoreCoverage | PASS | 44991976858 | COMPLETE | BD insulin → 2 doses 12h apart, high-alert, drugCode; wrong patient/drug barcodes 409 MAR_BARCODE_MISMATCH; no/self checker 400 HIGH_ALERT_SECOND_CHECK_REQUIRED → valid checker administered+verified; PRN without reason 400 → recorded; omission bad code/blank reason 400 → PATIENT_REFUSED omitted + MAR task; DoseScheduled/Administered/Omitted emitted (catalog 37) |
