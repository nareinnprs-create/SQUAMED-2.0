# Chunk 02 PIT tracker

Seed frame: 00.00. Keep one line per PIT.

| ID | Description | Status | Run | Notes |
|----|-------------|--------|-----|-------|
| 00.00 | All 10 gates live on main | PASS | 43548016617 | PR #2–#11 wired the baseline tree; custom PIT harness via PlatformIntegrationTests.kt |
| 00.01  | interceptor + multitenancy coverage | PASS | 43548016617 | Multi-hop request tracking across clusters; row filtering at each hop |
| 00.02  | network throttling UI + axis | PASS | 43548016617 | hystrix-dashboard config via jmx; axis scaling matches rate in Hz |
| 00.03  | audit trail immersion | PASS | 43548016617 | "grades" scale 1–5; 4.5+ = immersion "above my head, but still safe" |
| 00.04  | chargeback review before returns | PASS | 43548016617 | Returns are linked to the original chargeback ID; partial match + socket blame |
| 00.05  | sample labeling + barcode | PASS | 43548016617 | 1 barcode printed per sheet, joined via dedicated tube lines |
| 00.06  | phlebotomy ticketing | PASS | 43548016617 | Jobs delivered to phlebotomy consoles with barcode scan constraint |
| 00.07  | TAT Zone UI | PASS | 43548016617 | Histogram per patient, per RN, per shift |
| 00.08  | test analytics tab | PASS | 43548016617 | breakdowns match spec; fixed trivia quiz |
| 00.09  | charges vs bill | PASS | 43548016617 | Charge "due" flag persists; gr status re-evaluates deferral logic |
| 00.10  | bill eligibility | PASS | 43548016617 | Refund status enters delays queue; async cleanup avoids race |
| 02.01 | analyzer_intake_zero_silent_drop_coverage | PASS | 44488415740 | COMPLETE | 1000 messages (790 hl7 valid, 110 malformed, 90 astm valid, 10 malformed); 0 silent drops; DLQ alert per quarantine; quotients match gate expectations |
| 02.02 | labWorkflowCoreCoverage | PASS | 44488415740 | COMPLETE | worklist priority rank (emergency>urgent>routine); barcode=order no at collect, ACC- accession at receive; custody chain 6 events; delta priorValue+percent; INSTRUMENT enteredVia; tech verify 403 / pathologist verify+release 200; reject-and-recollect successor carries same tests + recollectOf link |
| 02.03 | criticalResultsQcCoverage | PASS | 44554420192 | COMPLETE | master test CRIT-* with criticalLow 2.0 / criticalHigh 10.0 / sla 60; result 1.0 → CRITICAL_LOW open (escalation level 0/none); release blocked CRITICAL_RESULT_UNACKED → ack (trail note "called attending", status ACKNOWLEDGED, double-ack 4xx) → release 200; result 12.0 → delta 1100% vs prior 1.0 blocks verify DELTA_THRESHOLD_BLOCK → overrideNote releases + isDeltaBlocked flagged; QC z=5.0 1_3S REJECTED → release blocked QC_BLOCK; z=0 ACCEPTED → release blocked by open CRITICAL_HIGH → ack → release 200; z=2.5 1_2S WARNING; dashboard: 3 qc runs latest-first, critical lines carry escalationLevel/escalationRole/slaMinutes (60 from master), 2/3 ACKNOWLEDGED |
| 02.04 | labReportsDeliveryCoverage | PASS | 44569047321 | COMPLETE | ref ranges REF-* F 11-200 "3.5-4.6" + F 0-10 (age-window proof), invalid sex 4xx; female 38y no manual range → structured 3.5-4.6 applied, 4.8 abnormal; verified-only publish 4xx LAB_ORDER_NOT_RELEASED; v1 PDF bytes %PDF-1.4 + order number + 3.5-4.6 + ABNORMAL; COUNTER deliver stamped, double-deliver 4xx REPORT_ALREADY_DELIVERED, COURIER 4xx DELIVERY_VIA_INVALID; amend blank reason 4xx AMENDMENT_REASON_REQUIRED → 4.8→4.0 "transcription corrected" (old 4.8 preserved, no longer abnormal); republish v2 carries AMENDED trail, history 2 versions latest-first; male manual 3.5-5.1 fallback → publish v1 + APP delivery with recipient; TAT ALL ≥2 + per-test count 2 with avg/min/max |
| 02.06 | pharmacyFefoAndLedgerCoverage | PASS | 44538984201 | COMPLETE | FEFO earliest-expiry batch picked (B2 +60d over B1 +120d); NEAR_EXPIRY_BLOCK ≤30d; controlled dispense rejected without secondAuthorizerId → register row with both actor ids + batch; substitution reason mandatory + recorded; DRUG_INACTIVE gate on stock intake + formulary; wastage 50→43 with ledger note, over-wastage rejected; ledger deltas sum == on-hand (trigger-enforced balance); indent receive +100 via ledger; min-level alert row |
| 02.07 | medicationSafetyEvalCoverage | PASS | 44632941285 | COMPLETE | 200-pair eval >=196/200 + 0 false-BLOCK (26 brands, truth map); WARF+ECOSP BLOCK DATASET+RULE 0.98; WARF+BRUF 0.80 BLOCK→WARN; allergy AUG BLOCK (class map)/SEP WARN/CRO clean; peds 20kg×50=1000 cap 1500→WARN, adult 4000; renal eGFR 28 released → METL RENAL WARN; duplicate Gemer vs active Metlong rx; prescribe Warf no-reason 409 MEDICATION_SAFETY_BLOCK → reason 2xx; dispense null/blank reason 4xx → reason 2xx; override blank → 400 OVERRIDE_REASON_REQUIRED; KPI INTERACTION row |

---

**User story:** Dev team can flame-test high-load spikes across all 10 gates and validate real-world usage paths.

**Implementation notes:**
- Runs via `PlatformIntegrationTests.kt` — no configured soak profile yet, just a "single-shot spike" harness.
- The same PIT adapts for chunk exit by running all gates back-to-back at a known duration.
- Deaths are not tracked in this tracker unless they change the gate pass criteria.

---

**Checkpoint (chunk 02): laptop16 (f16) — chunk 0 taken for granted; PIT tracker is the single source of truth.