# CHUNK 4 — SECTION GRAPH (inpatient, surgery & critical care)
Ledger source for wave 5. Est. in **agent-plan weeks** (~8 wk total). Contract: `chunk-04-inpatient-surgery-critical.md`.

| # | Section | Contents | Depends on | Est. | Notes |
|---|---|---|---|---|---|
| 04.01 ⛔ | Admission + bed management | Admission requests (ER/OPD/scheduled), allocation rules (class/isolation/gender-mix), transfers with trace, expected-discharge planning, live bed board, exclusion constraint on allocations | chunk 3 exit | 1.0 | |
| 04.02 ⛔ | eMAR + nursing core | MAR schedule from dispensed meds (chunk 2), barcode patient+drug administration (mismatch = hard block), PRN with reason, omission codes + prescriber notification, high-alert double-check, nursing notes, per-shift task lists | chunk 3 exit | 1.5 | CS-04/06; dual review |
| 04.03 | Assessments + care plans + risk scores | Configurable assessment forms, problem→intervention→goal plan templates, falls/pressure scores, discharge-checklist start | 04.01 | 1.0 | |
| 04.04 ⛔ | Device integration v1 | Vendor gateways → HL7 v2/normalized JSON → stream service → flowsheets; threshold alerts (config, clinical-lead signed); on-device alarms stay on-device (documented limitation); 24 h / 8-bed sim exactly-once, alert <10 s | chunk 3 exit | 1.5 | ⛔ external: monitor gateway specs = biggest lead-time risk |
| 04.05 | ICU charting | 1–2 h flowsheets, ventilation/infusion charting, auto-aggregated fluid balance, SOFA/GCS informational calculators, critical-alert routing | 04.04 | 1.0 | |
| 04.06 | OT + anaesthesia | Surgical schedule, WHO checklist enforced at 3 stages, intra-op record, implants (batch/UDI), consumable auto-charges, post-op orders, PACU/Aldrete, anaesthesia timeline record (device-fed) | 04.01, 04.04 | 1.5 | |
| 04.07 | Blood bank | Donor registry + deferral, screening, component inventory (expiry), crossmatch→reservation→issue with bedside wristband check (BLOCK on mismatch), reaction reporting, donor→recipient traceability report | chunk 3 exit | 1.0 | CS-10; event-sourced trace proof |
| 04.08 | IPD billing + dietary + discharge | Bed-hour room charges, nursing procedure charges, surgical packages, interim bills; diet orders + kitchen manifest (allergy-aware); discharge checklist → coded signed summary (med-rec gate) → follow-up → final bill → settlement → bed release → housekeeping task | 04.02, 03 | 1.0 | CS-11 |
| 04.09 ⛔ | **Chunk exit** | Live demo, 200-dose eMAR sim, device sim, blood trace event-log proof, discharge bill accuracy check, ward-by-ward rollout prep, dual review | all | 0.5 | |

**Lanes (≈8 wk):** (04.01→04.03) · (04.02→04.08) · (04.04→04.05→04.06) · (04.07) · exit last. Raw sum 10.0 wk over 4 lanes.
