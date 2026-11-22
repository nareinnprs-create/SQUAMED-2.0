# Chunk 03 PIT tracker

Seed frame: 00.00. Keep one line per PIT.

| ID | Description | Status | Run | Notes |
|----|-------------|--------|-----|-------|
| 03.01 | chargeCaptureLedgerCoverage | PASS | 44801191143 | COMPLETE | tariff-priced event capture (lab 75.0 / imaging 250.0), sourceEventId set, entryMode EVENT, replay idempotent (same row, 2 charges only); manual charge blank reason 400 CHARGE_REASON_REQUIRED → MANUAL + reason + poster; billing-ledger cursor |
| 03.02 | billingEngineCoverage | PASS | 44970294522 | COMPLETE | TPA card 100→120 (base 100 + card ref), general fallback 90; package v1 pins 500 at capture, v2 prices new capture 600 and v1 stays 500; estimate 250 EST-… + list; deposit 200 applied to 100 invoice → paid/due 0, re-apply 400 DEPOSIT_NOT_HELD; discount no reason 400, 5% staff ok, 50% staff 400 DISCOUNT_APPROVAL_REQUIRED → supervisor total 500 |
| 03.03 | taxEngineCoverage | PASS | 44973751492 | COMPLETE | exempt consult 0%/SAC 9993; pharmacy 5%/HSN 3004; device 12%/9018; mixed 350+11=361 with 3 tax lines; 1500 flags eInvoiceRequired; credit note staff 403, blank 400, over-total 400, supervisor 50 → CN-… invoice 55; 40-case tax table with HALF_UP rounding all green |
| 03.05 | himCodingAndIntegrityCoverage | PASS | 44976991927 | COMPLETE | completed encounters on the coding queue with diagnosis counts; ICD R05+J18.9 + SNOMED assigned, re-code 400 CODING_ALREADY_ASSIGNED, queue assigned, 100% audit sample; coder query → task (ENCOUNTER) → doctor answer → ANSWERED, double answer 400; scan unbilledHours=0 → ≥3 unbilled + ≥1 duplicate + ≥1 uncoded, re-scan 0 new; triage blank 400 → RESOLVED with note, double triage 400; finding task exists |
