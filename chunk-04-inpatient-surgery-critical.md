# CHUNK 04 — INPATIENT, SURGERY & CRITICAL CARE
**Status:** PLANNED · **Window:** Nov 2027 → Mar 2028 (16 wk) · **Depends on:** chunks 1–3 exit
**Stack entering:** Device gateway layer (vendor monitors via vendor gateways/HL7), blood bank module

## GOAL
**Full hospital live:** the IPD + OT + ICU spine — admission to discharge — on the same platform, same patient context, same billing engine.

## IN SCOPE
1. **Admission & bed management:** admission request (from ER/OPD/schedule), bed allocation (class, isolation, gender-mix rules), transfers with full trace, expected-discharge planning, bed board (live status: occupied/reserved/cleaning/available), discharge checklist workflow (pharmacy returns, final bill from chunk 3, summary signature) — discharge is a **Temporal-free state machine** for now (Temporal enters chunk 6 only if justified).
2. **Nursing:** assessment forms (configurable), care plans (problem → intervention → goal, templates), **eMAR** (scheduled doses from dispensed meds; barcode patient+drug per §5; PRN with reason; omission codes; high-alert double-check per §5 rule 3), vitals flowsheets (manual + device), nursing notes, shift handover generator (SBAR), task lists per shift, risk assessments (falls/pressure ulcer scores, configurable scales).
3. **ICU/critical care:** ICU flowsheets (1–2 h granularity), ventilation/infusion **charting** (manual + device-fed where integrated), fluid balance (auto-aggregated inputs/outputs), severity scores (SOFA/GCS calculators — informational, not prescriptive), critical alerts routing.
4. **Device integration v1:** patient monitors + ventiltors via vendor gateways → HL7 v2/normalized JSON → device gateway service → vitals streams to flowsheets. Alarm policy: device alarms stay on-device; Squamed ingests for charting + trend alerting on **configured thresholds only** (no clinical alarm certification claims in v1 — documented limitation).
5. **OT/surgery:** surgical schedule (theatre + surgeon + anaesthetist + equipment), pre-op checklist + fitness/consent (chunk 0 consent engine), **WHO Surgical Safety Checklist** enforced at sign-in/time-out/sign-out, intra-op record (procedure, staff, implants with batch/UDI, consumables → auto charge events), post-op orders, PACU handover, surgical site infection follow-up flag to IPC stub.
6. **Anaesthesia:** pre-anaesthesia evaluation, anaesthesia record (timeline charting incl. device vitals), recovery (PACU scoring, Aldrete), post-op handover.
7. **Blood bank:** donor registry + deferral rules, screening (group/ttI/NAT placeholder; NAT integration → chunk 7), inventory (component-wise, expiry), crossmatch request → reservation → issue with two-identifier + wristband barcode check, transfusion reaction reporting, traceability donor→recipient (regulatory report format).
8. **IPD billing activation:** room charges by bed-hour, nursing procedure charges, package application for surgical cases, pre-auth for IPD (chunk 3 engine), interim bills.
9. **Dietary-lite:** diet orders on eMAR-adjacent schedule, kitchen manifest per meal, allergy-aware (full food-service ops → chunk 7).
10. **Discharge:** checklist, discharge summary (structured, ICD-coded, e-signed, med-rec section), follow-up booking, patient instructions (print + app), final bill → settlement (chunk 3) → bed release → housekeeping task.

## OUT OF SCOPE
Full ED module (triage-lite exists since chunk 1; full ED resus workflows chunk 6 decision), CSSD (chunk 6), ICU decision-support AI (chunk 5), BMS/IoT facilities.

## DATA MODEL (key additions)
`admission(+status), bed_allocation, transfer_log, care_plan(+elements), mar_schedule, mar_administration(±omission), flowsheet_observation(stream-backed), fluid_balance, risk_score, ot_case(+checklist states), intraop_record, implant_log, anaesthesia_record, pacu_score, blood_component(donor-linked), crossmatch_request, transfusion_event(+reaction), diet_order, discharge_checklist, discharge_summary(signed)`

## APIS
- `/admissions`, `/admissions/{id}:transfer|plan-discharge|discharge`
- `/beds:board|allocate|mark-cleaning`
- `/nursing/{admissionId}/mar|flowsheets|handover`
- `/ot/schedule`, `/ot/cases/{id}:checklist|record|close`
- `/anaesthesia/{caseId}:evaluate|record|pacu`
- `/bloodbank/donors|inventory|crossmatch|issue`
- `/devices/stream:subscribe` (WS per bed)

## EVENTS
`AdmissionCreated, PatientAdmitted, BedAssigned, PatientTransferred, CarePlanUpdated, DoseScheduled/Administered/Omitted, VitalsIngested, ThresholdAlertRaised, SurgeryScheduled, ChecklistStageCompleted, ProcedureCompleted, ImplantRecorded, PACUDischargeReady, CrossmatchRequested/Issued, TransfusionReactionReported, DietOrderPlaced, DischargePlanned, PatientDischarged, DischargeSummarySigned, HousekeepingTaskCreated`

## PERMISSIONS (adds)
Ward nurse: eMAR/flowsheets (own assignment). Charge nurse: bed ops. Anaesthetist/surgeon: scoped to own cases. Blood bank tech: issue (2-person on issue). Dietician: diet orders.

## SAFETY
- eMAR: barcode mismatch = hard block; omission requires reason + notification to prescribing doctor.
- High-alert double-check enforced (§5 rule 3); insulin/anticoagulant dose change requires fresh order version.
- Crossmatch: patient identity re-verified at bedside at issue; wrong-wristband = BLOCK.
- Bed allocation enforces isolation flags (contact/droplet/airborne) from IPC stub.
- Discharge summary cannot finalize without med-rec review (task).
- Alarm/threshold config changes audited + signed by clinical lead role.

## TESTS
- eMAR: 200-dose simulated ward day incl. late/omitted/high-alert — zero silent omissions, all audited.
- Device sim: 24 h monitor stream for 8 beds (dropped packets, duplicates) → exactly-once charting, alert latency < 10 s.
- Blood bank: full donor→crossmatch→issue→reaction trace regenerated from event log (event-sourced proof).
- Discharge E2E incl. final bill accuracy vs manual rate-card calc.

## EXIT CRITERIA (demo scenario, live)
ER arrival → triage-lite → admission → bed assigned → nursing assessment + care plan → eMAR doses with one high-alert double-check → monitor vitals streaming to ICU flowsheet → fluid balance totals → OT case with WHO checklist all three stages → implants recorded → PACU → discharge checklist → signed coded summary → final bill with package pricing → settlement → bed auto-released → housekeeping task → blood bank crossmatch round-trip shown → bed board reflects the whole day.

## ROLLOUT / ROLLBACK
Ward-by-ward rollout at pilot (one ward → ICU → OT), each with 1-week shadow run. Rollback per ward: paper MAR/flowsheets + re-entry; eMAR data frozen read-only.

## OPEN DECISIONS
- Monitor vendor/gateway models at pilot (interface specs week 1 — biggest lead-time risk of chunk).
- Blood bank regulatory reporting format (state blood bank authority).
