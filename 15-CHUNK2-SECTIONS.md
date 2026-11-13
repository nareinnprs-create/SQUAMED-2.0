# CHUNK 2 — SECTION GRAPH (diagnostics & pharmacy)
Ledger source for wave 3. Est. in **agent-plan weeks** (~7 wk total). Contract: `chunk-02-diagnostics-pharmacy.md`.

| # | Section | Contents | Depends on | Est. | Notes |
|---|---|---|---|---|---|
| 02.01 ⛔ | Analyzer intake (Mirth) | HL7 v2 channels, ASTM bridge adapter, `analyzer_message_log`, quarantine + alert (zero silent drops), ACK policy, version-controlled channel configs, 1k-message sim harness | chunk 1 exit | 1.5 | ⛔ external: analyzer interface specs week 1 |
| 02.02 ⛔ | Lab workflow core | Order acceptance → phlebotomy worklist → barcode labeling → accessioning (chain-of-custody) → result entry (manual+instrument) → delta checks → pathologist validation; reject-and-recollect (never edit-in-place) | chunk 1 exit | 1.5 | Parallel with 02.01 |
| 02.03 | Critical results + QC | SLA escalation machinery, ack trail, dept head + CMO dashboard line; Westgard QC runs hard-block release | 02.02 | 1.0 | CS-05 |
| 02.04 | Lab reports + delivery | Branded PDF, age/sex reference ranges, amendments versioned, delivery counter/app/notification, TAT analytics | 02.02 | 1.0 | |
| 02.05 | RIS-lite + PACS | Orthanc MWL provider, study completion, radiologist worklist, structured report editor + coding, OHIF embedded (scoped tokens), critical-findings escalation, Patient 360 attach | chunk 1 exit | 1.5 | Buy-list integration, not builds |
| 02.06 ⛔ | Pharmacy stock + dispensing | Drug master activation, formulary, indents, dispensing (partial, substitution audited), batch/expiry, FEFO (BLOCK near-expiry), stock ledger (trigger-enforced balance), min-level alerts, controlled register, returns/wastage | chunk 1 exit | 1.5 | Parallel with 02.01 |
| 02.07 ⛔ | Medication safety engine | Interaction checks (dual-source, provenance + confidence, BLOCK only ≥ threshold), allergy ingredient-level mapping, duplicate therapy, max-dose (peds weight), renal flag (eGFR); override reason KPIs; 200-pair eval ≥98%, 0 false-BLOCK | 02.06 | 1.0 | Dual review (safety-critical) |
| 02.08 | Charge events + inventory linkage + eMAR groundwork | `ChargeablePerformed` emission, consumption → stock ledger, reorder proposals (human-approved), dispense-side correctness for chunk-4 eMAR | 02.06 | 0.5 | |
| 02.09 ⛔ | **Chunk exit** | Demo scenario, analyzer sim, critical-result drill timed vs SLA, interaction eval gate, FEFO test, lab parallel-run prep | all | 0.5 | |

**Lanes (≈7 wk):** (02.01) · (02.02→02.03→02.04) · (02.06→02.07→02.08) · (02.05) · exit last. Raw sum 10.0 wk over 4 lanes.
