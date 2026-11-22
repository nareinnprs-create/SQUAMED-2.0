# Chunk 03 PIT tracker

Seed frame: 00.00. Keep one line per PIT.

| ID | Description | Status | Run | Notes |
|----|-------------|--------|-----|-------|
| 03.01 | chargeCaptureLedgerCoverage | PASS | 44801191143 | COMPLETE | tariff-priced event capture (lab 75.0 / imaging 250.0), sourceEventId set, entryMode EVENT, replay idempotent (same row, 2 charges only); manual charge blank reason 400 CHARGE_REASON_REQUIRED → MANUAL + reason + poster; billing-ledger cursor |
| 03.02 | billingEngineCoverage | PASS | 44970294522 | COMPLETE | TPA card 100→120 (base 100 + card ref), general fallback 90; package v1 pins 500 at capture, v2 prices new capture 600 and v1 stays 500; estimate 250 EST-… + list; deposit 200 applied to 100 invoice → paid/due 0, re-apply 400 DEPOSIT_NOT_HELD; discount no reason 400, 5% staff ok, 50% staff 400 DISCOUNT_APPROVAL_REQUIRED → supervisor total 500 |
