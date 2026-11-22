# CHUNK 3 — FINAL REPORT (section 03.07 chunk exit)

## 1. Scope
Chunk-3 exit per `chunk-03-revenue-cycle.md` EXIT CRITERIA (L51), TESTS (L45-48), SAFETY/COMPLIANCE (L39-42), ROLLOUT/ROLLBACK (L54) and `03-BUILD-PROTOCOL.md` §7: the OPD-day-to-GL walk live, the chunk's named test evidence (tax table, integrity rules, money math), billing parallel-run prep, this report, and the sign-off block. **03.07 carries zero feature code by design.**

**Honest chunk state at exit:** 5 of 6 buildable sections COMPLETE (03.01, 03.02, 03.03, 03.05, 03.06). **03.04 Insurance/TPA v1 remains PLANNED — externally gated** on the pilot payer list (D6) and the payment-gateway procurement (E4); its pre-auth/claim legs are therefore absent from the exit demo and recorded as owed. **03.07 lands at SECTION REVIEW, never COMPLETE**: the 2-week billing parallel run is a deployment activity, and go-live (B2) is a human gate.

## 2. Built (this docs batch)
- `scripts/demo-03.07-chunk3-exit.ps1` — 28-check exit demo (full L51 scenario minus the insurance/claim legs).
- `docs/runbooks/billing-parallel-run.md` — rate-card/tax load, 2-week daily loop, exit criteria, rollback, sign-off.
- `reports/chunk-03/section-03.07.md` + this FINAL + LEDGER row 03.07 + tracker row.
- `reports/demo-03.07-live.log` — live transcript.

## 3. Chunk-3 section statuses
| Section | Status | Report |
|---|---|---|
| 03.01 Charge capture ledger | COMPLETE (PR #82) | `reports/chunk-03/section-03.01.md` |
| 03.02 Billing engine | COMPLETE (PR #84) | `reports/chunk-03/section-03.02.md` |
| 03.03 Tax engine (India) | COMPLETE (PR #86) | `reports/chunk-03/section-03.03.md` |
| 03.04 Insurance / TPA v1 | **PLANNED — externally gated `exp:E4` + D6 payer list** | — |
| 03.05 HIM coding + integrity | COMPLETE (PR #87) | `reports/chunk-03/section-03.05.md` |
| 03.06 Collections + GL export | COMPLETE (PR #88) | `reports/chunk-03/section-03.06.md` |
| 03.07 Chunk exit | **SECTION REVIEW** (parallel run + sign-off B2) | `reports/chunk-03/section-03.07.md` |

## 4. Chunk exit demo evidence
`pwsh scripts/demo-03.07-chunk3-exit.ps1` against the app on :8080: **28 passed, 0 failed**, live 2026-11-22 (transcript `reports/demo-03.07-live.log`). Scenario: admin + hospital + doctor/billing staff/supervisor/coder/lab tech/pathologist → patient + encounter → tax profile (GSTIN, threshold 5000) → **lab release auto-captures a ledger charge** (`EVENT`, tariff 80, exempt SAC 9993) → pharmacy goods charge defaults to 5% GST → **TPA bill reprices the lab line to 96** (subtotal 196), tax 5.00, total 201, two tax lines → deposit 100 applied (paid 100/due 101) → counter cash payment 101 → **cash-up float 50 + 101 = 151 counted, variance 0** → invoice paid/due 0 → **bank statement reconciles MATCHED** → completed encounter on the coding queue → ICD-10 R05/J18.9 assigned → **integrity scan flags the unbilled dressing** → supervisor resolves with a note → supervisor refund 1.0 with reason (staff 403) → **journal export has cash debit + revenue credit and debits = credits**.

**Script bugs fixed during the live run:** verify/release require the pathologist role (doctor 403); collect requires the lab technician.

## 5. Named test evidence (chunk TESTS L45-48)
| Contract test | Evidence |
|---|---|
| Tax correctness table: 40 cases (exempt, GST pharmacy, mixed, credit note, package pricing with version pin) | `taxEngineCoverage` (03.03): 40-case table + mixed bill + credit note + package-pin tax leg |
| Claim adapter golden files (TPA/PM-JAY/CGHS) | **Owed to 03.04** (payer list D6) |
| Integrity rules: seeded leakage scenarios detected with <5% false positives | `himCodingAndIntegrityCoverage` (03.05): unbilled, duplicate, uncoded; dedupe-stable re-scan |
| Money math: integer-paise arithmetic, rounding documented | BigDecimal throughout; tax HALF_UP per line (03.03 §7); no floats in the billing path |

## 6. Safety / compliance (L39-42) status
- **Bill lines immutable after finalize:** corrections are credit notes (supervisor-gated, reason mandatory) — 03.03.
- **Discount authorization matrix:** reason mandatory; >10% needs `billing:approve:discount` — 03.02.
- **Cash drawer variance:** >50 raises a high-priority task — 03.06.
- **Claim assembly rejects on unsigned encounter / uncoded discharge:** the hook exists via HIM coding (03.05); the claim assembler itself is 03.04.
- **DPDP minimization:** journal/export carry amounts and account codes, no patient identifiers beyond ids — noted.

## 7. Owed / tracked (not skipped)
| Item | Owner | Why it is not CI evidence |
|---|---|---|
| Insurance/TPA v1 (pre-auth, claim assembly, denial loop) | Product + payer list (D6) | Externally gated; adapter effort per payer |
| Payment gateway / UPI PSP integration | Procurement (E4) | External vendor onboarding |
| Billing parallel run (2 weeks) | Billing manager | Deployment activity; runbook + daily comparison |
| Rate cards + tax profile reconciliation | Finance lead | Human line-by-line check |
| Go-live sign-off | Owner (B2) | Human gate |

## 8. Gate 12 disposition
Chunk-3 GATE-12: **PASS (partial)** — all buildable sections carry CI evidence on merged main; 03.04 is externally gated and tracked; 03.07 itself is docs + demo (28/28 live). Chunk-4 (IPD/nursing/eMAR) consumes the dispense-side correctness from 02.08 and the charge ledger from 03.01.

## 9. Sign-off
| Role | Name | Date | Verdict |
|---|---|---|---|
| Build owner | — | — | Chunk-3 buildable sections COMPLETE; 03.04 gated; 03.07 SECTION REVIEW |
| Finance lead | — | — | Pending rate-card/tax reconciliation + parallel run |
| Owner (B2) | — | — | Pending go-live decision |
