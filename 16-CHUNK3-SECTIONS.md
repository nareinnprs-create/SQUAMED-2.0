# CHUNK 3 — SECTION GRAPH (revenue cycle)
Ledger source for wave 4 (may overlap chunk 2 by one sprint, 8-agent capacity). Est. in **agent-plan weeks** (~6 wk total). Contract: `chunk-03-revenue-cycle.md`.

| # | Section | Contents | Depends on | Est. | Notes |
|---|---|---|---|---|---|
| 03.01 ⛔ | Charge capture ledger | Event-fed unified ledger (chunks 1–2 events), manual entry audited + reason | chunk 1 exit | 1.0 | |
| 03.02 ⛔ | Billing engine | Rate cards per payer class (general/CGHS/ESIC/TPA/corporate/PM-JAY), versioned packages (version pinned at order time), estimates, deposits, role-gated discounts, professional-fee split, consumables, consult-receipt migration | 03.01 | 1.5 | Dual review (money) |
| 03.03 | Tax engine (India) | Exempt services vs GST 5%/12% goods, HSN, GSTIN per entity, e-invoicing threshold flow, credit notes, refund approval chain; 40-case table | 03.02 | 1.0 | |
| 03.04 ⛔ | Insurance / TPA v1 | Payer registry, policy capture, cashless pre-auth workflow, claim assembly adapters (TPA/PM-JAY/CGHS golden files, byte-stable), status tracking, queries, denial reason codes, settlement + bank recon | 03.02 | 1.5 | ⛔ external: payer adapter list = schedule risk |
| 03.05 | HIM coding + integrity engine | ICD-10 coding worklist on signed encounters, coder queries (task engine), audit sampling; leakage rules: missing charges, duplicates, unbilled orders, doc-gaps, coding inconsistency; daily digest + tasks; <5% FP | 03.02 | 1.0 | CS-12 hook |
| 03.06 | Collections + GL export | Cash/UPI/card, shift cash-up (variance → security event), payment gateway integration, recon report, refunds; daily GL journal export (Tally/Zoho-ready) | 03.02 | 1.0 | ⛔ external: gateway/PSP selection |
| 03.07 ⛔ | **Chunk exit** | Demo scenario, tax table green, golden-file byte stability, integrity replay day, integer-paise property tests, billing parallel-run prep, dual review (money paths) | all | 0.5 | |

**Lanes (≈6 wk):** (03.01→03.02→03.03/03.04/03.05/03.06) — 03.02 is the bottleneck, three sections branch after it · exit last. Raw sum 7.5 wk.
