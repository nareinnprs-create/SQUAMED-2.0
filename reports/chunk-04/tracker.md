# Chunk 04 PIT tracker

Seed frame: 00.00. Keep one line per PIT.

| ID | Description | Status | Run | Notes |
|----|-------------|--------|-----|-------|
| 04.01 | admissionBedManagementCoverage | PASS | 44989139607 | COMPLETE | isolation/gender allocation rejections; admission occupies bed + created/admitted/bed-assigned events; plan-discharge; transfer rule + trace; early discharge 409 DISCHARGE_CHECKLIST_INCOMPLETE; summary ICD guard + double-sign 400; pharmacy+bill legs; discharge → bed cleaning + HOUSEKEEPING task; unsettled invoice 409 DISCHARGE_BILL_UNSETTLED; 8 catalog entries EMITTED |
| 04.02 | emarNursingCoreCoverage | PASS | 44991976858 | COMPLETE | BD insulin → 2 doses 12h apart, high-alert, drugCode; wrong patient/drug barcodes 409 MAR_BARCODE_MISMATCH; no/self checker 400 HIGH_ALERT_SECOND_CHECK_REQUIRED → valid checker administered+verified; PRN without reason 400 → recorded; omission bad code/blank reason 400 → PATIENT_REFUSED omitted + MAR task; DoseScheduled/Administered/Omitted emitted (catalog 37) |
| 04.03 | nursingAssessmentsCareplansRisksCoverage | PASS | 45090008029 | COMPLETE | assessment template fields stored/listed; assessment echoes fields + provenance; missing field 400 ASSESSMENT_FIELD_REQUIRED; unknown template 400; care-plan template applied (problem/goal/interventions) + CarePlanUpdated on completion; falls scale 1 low / 4 moderate / 8 high; score −1 400 RISK_SCORE_OUT_OF_RANGE; manual level kept without a scale; catalog 38 |
| 04.05 | icuChartingCoverage | PASS | 45093632237 | COMPLETE | backdated rows → ≥3 hourly flowsheet buckets (latest vitals, SIMV vent row, carried infusion, per-hour in/out); 24h totals 1500/900/net 600; SOFA 2/2/2/1/2/3 = 12; alert open → icu.critical_alert event → acknowledged; ThresholdAlertRaised cataloged (39) |
| 04.06 | surgicalSafetyAndChargesCoverage | PASS | 45144865455 | COMPLETE | WHO seed 5/4/4 idempotent; consumable → ledger PROCEDURE/CH-01 via chargeable_performed; SSI flag → SSI task; consent withdrawal 400 SURGERY_CONSENT_WITHDRAWN; baseline surgical PIT updated to consent + WHO gates; V82 admin consent grant |
