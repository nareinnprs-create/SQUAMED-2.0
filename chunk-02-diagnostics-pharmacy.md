# CHUNK 02 — DIAGNOSTICS & PHARMACY
**Status:** PLANNED · **Window:** May → Aug 2027 (14 wk) · **Depends on:** chunk 01 exit
**Stack entering:** Mirth Connect (HL7 v2), Orthanc (DICOM) + OHIF viewer, drug interaction dataset
**Baseline (SC-6):** `master` prior build has a lab module (orders/results/permissions) and pharmacy module (stock/dispensation) — thinner than this contract; sections enter gates on existing code only after the chunk-0 baseline audit; analyzer intake vs D19 (Mirth vs built-in HL7) decided before 02.01.

## GOAL
Orders written in chunk 1 become real: **order → sample → result → validated report** and **prescribe → dispense → administer-ready medication record** with safety checking.

## IN SCOPE
1. **Lab LIS (full path):** order acceptance → phlebotomy worklist → barcode sample labeling → accessioning → analyzer intake (HL7 v2 via Mirth; ASTM bridging adapter for common analysers) → result entry (manual + instrument) → delta checks → validation (pathologist sign) → **critical result escalation** (SLA task, ack trail, §5 rule 5) → report PDF (branded, reference ranges by age/sex) → patient delivery (counter/app/notification) → TAT analytics. Departments: biochemistry, haematology, serology, microbiology (worklist + qualitative results; full micro ID/AST tracking → chunk 7), cytology/histopathology (slide workflow lite; full → chunk 7).
2. **QC:** Westgard-rules-based run QC for quantitative assays, QC failure blocks result release for that run.
3. **RIS-lite + imaging:** modality worklist (DICOM MWL via Orthanc), study completion, radiologist worklist, **reporting editor with structured templates + ICD/SNOMED coding**, critical findings escalation (same SLA machinery), study attachment to Patient 360. **PACS = Orthanc; viewer = OHIF embedded. Do not build DICOM storage or rendering.**
4. **Radiology billing hooks:** performed-study events emit charge-relevant events (consumed fully in chunk 3).
5. **Pharmacy:** drug master activation (§3 brand→generic), formulary per hospital, indents (ward stock), dispensing against eRx (partial dispensing, substitutions with audit), batch/expiry tracking, FEFO picking, stock ledger, min-level reorder alerts, cold-chain flag (fridge items logged with temperature excursions from chunk 4 device layer), controlled-substance register (Schedule H1/narcotics log per India rules), returns/wastage.
6. **Medication safety engine:** interaction checks (open dataset + rules, severity `BLOCK/WARN/INFO` per §5 rule 4), allergy cross-check (ingredient-level mapping), duplicate therapy, max-dose rules (weight-based for pediatrics), renal-dose adjustment flag (with eGFR from labs when present). **Override reasons mandatory and KPI-tracked.**
7. **eMAR groundwork:** administered/dose records created by nursing (full nursing workflow is chunk 4; IPD medication chart arrives there — this chunk ships the dispense-side correctness).
8. **Inventory linkage:** consumption events to stock ledger; reorder proposals (human-approved PO stub — full procurement is chunk 6).

## OUT OF SCOPE
Billing beyond charge-event emission (chunk 3), IPD/OT, blood bank (chunk 4), device vitals (chunk 4), AI report drafting (chunk 5).

## DATA MODEL (key additions)
`lab_order_item(status machine), specimen(barcode, chain-of-custody), accession, analyzer_message_log, result(quantitative/qualitative, validated_by), result_amendment(reason), qc_run, reference_range, report_document, rad_order, study(orthanc refs), radiology_report(signed), drug(brand, generic, atc, ingredients), formulary_entry, stock_item(batch, expiry), stock_ledger, indent, dispense(±partial), controlled_register, interaction_rule, dose_rule, temperature_log`

## APIS
- `/labs/orders/{id}:collect|accession|enter-results|validate|reject`
- `/labs/specimens/{barcode}`, `/labs/reports/{id}:publish`
- `/radiology/worklist`, `/studies/{id}:complete|report:sign`
- `/pharmacy/indents`, `/pharmacy/dispense`, `/pharmacy/stock:adjust`, `/pharmacy/controlled:log`
- `/medication-safety:check` (interaction+allergy+dose, invoked by eRx and dispensing)
- Device/analyzer: Mirth channels config, not public API.

## EVENTS
`SampleCollected, Accessioned, ResultEntered, ResultValidated, CriticalResultDetected, CriticalResultAcknowledged, ReportPublished, StudyCompleted, RadiologyReportSigned, PrescriptionDispensed, StockLowDetected, BatchExpiring, ControlledSubstanceDispensed, ChargeablePerformed (lab/imaging)`

## PERMISSIONS (adds)
Pharmacist: dispense, stock (no price edit). Phlebotomist/lab-tech: collect/accession/enter (no validate). Pathologist/radiologist: validate/sign. Billing: read charge events.

## SAFETY
- Specimen chain-of-custody: every status change identifies who/when/where; mislabel = reject-and-recollect flow, never edit-in-place.
- Critical result unacked > SLA → auto-escalate to dept head + CMO dashboard line (§5 rule 5).
- QC failure hard-blocks release (no override path — rerun only).
- Dispensing requires two-identifier patient check; high-alert drugs require second pharmacist confirmation.
- Radiology report amendments versioned like clinical notes.

## TESTS
- Analyzer sim: 1,000 HL7 v2 messages incl. malformed → zero silent drops, all quarantined to integration error queue with alert.
- Critical-result drill: sim critical K+ → task → ack → escalation path timed against SLA.
- Interaction engine: eval set of 200 pairs incl. Indian brand combos — ≥98% classification accuracy on eval set, 0 false `BLOCK` on safe combos.
- FEFO + expiry: near-expiry batch never dispensed when valid batch exists (BLOCK).

## EXIT CRITERIA (demo scenario)
Doctor (chunk 1) orders CBC + chest X-ray + one interacting drug pair → phlebotomist barcodes sample → analyzer feed enters results → one critical value triggers SLA-tracked escalation with ack → pathologist validates → report delivered to patient app/counter → radiologist opens study in OHIF from modality worklist, signs structured report → pharmacy dispenses eRx with interaction `WARN` (reason captured) → stock ledger + controlled register updated → TAT dashboard shows the day.

## ROLLOUT / ROLLBACK
Lab parallel-run 2 weeks (paper + LIS); pharmacy parallel stock count. Rollback: LIS read-only + paper reports; pharmacy ledger is source of truth from go-live (no rollback without stock recount).

## OPEN DECISIONS
- Analyzer vendor list at pilot (drives Mirth channel configs — request interface specs week 1).
- OHIF version pinning + Orthanc storage sizing per §10.
