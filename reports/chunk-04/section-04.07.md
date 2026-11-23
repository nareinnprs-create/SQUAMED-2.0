# Section 04.07 — Blood bank (chunk 4)

**Gate-run assessment of the CURRENT tree** @ PR #99 (chunks 0–3 + 04.01-04.03/04.05/04.06 merged + this PR). Verdict: **COMPLETE → GATE-12 PASS (partial: chunk-4 exit demo slice owed at 04.09)**. CI evidence: **run 45155764190 — all 7 gates green**. Named PIT `bloodBankSafetyCoverage` green in the same run.

## 1. Scope
Per `chunk-04-inpatient-surgery-critical.md` §7: donor registry + deferral rules, screening hand-off (ttI/NAT placeholder), component inventory/expiry (baseline), crossmatch → reservation → issue with a two-identifier + wristband check, transfusion reaction reporting, donor→recipient traceability.

## 2. Key artifacts
**Migration `V83__blood_bank_safety.sql`** (additive; rollback note inside):
- `blood_donor` += deferral reason/until; `blood_unit` += `screening_status`/`tti_result`/`nat_result`; `blood_issue` += verified flags.

**Behaviour:**
- Donor deferral (reason mandatory) blocks unit creation; reactivation clears; 90-day donation interval enforced; last donation stamped.
- Screening: `CLEARED` only when ttI cleared and NAT not reactive; rejected units discarded; issue requires `CLEARED`.
- Issue: compatible crossmatch required + bedside check (barcode = MRN, confirmed name matches) → 409 `BLOOD_IDENTITY_MISMATCH` otherwise; verified flags stored.
- Traceability endpoint assembles donor → unit → grouping → crossmatches → reservations → issue → transfusions → reactions.
- Events: `CrossmatchRequested`, `CrossmatchIssued`, `TransfusionReactionReported` cataloged (39 → 42).
- OpenAPI snapshot (`b6bef6b1…`), codegen stamped.

## 3. Gap closure
- ✔ Donor deferral + interval rules
- ✔ Screening gate before issue
- ✔ Bedside two-identifier + wristband check (hard block)
- ✔ Reaction reporting + events
- ✔ Donor→recipient traceability
- ✔ PIT 04.07: `bloodBankSafetyCoverage` PASS

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | PIT covers deferral/interval, screening, identity blocks, events, trace |
| 2 Zero-stub | PASS | CI branch run 45155764190 |
| 3 Types | PASS | CI branch run 45155764190 |
| 4 Lint + format | PASS | CI branch run 45155764190 |
| 5 Tests | PASS | CI branch run 45155764190; named PIT below |
| 6 Accessibility | PASS | CI branch run 45155764190 (web untouched) |
| 7 Design system | PASS | CI branch run 45155764190 |
| 8 Contracts | PASS | Snapshot + codegen committed; parity green |
| 9 Data | PASS | V83 additive + rollback note; screening index |
| 10 Audit | PASS | Deferral reason, screener, issue verifier flags, reaction reporter all recorded |
| 11 Docs | PASS | This report; LEDGER row 04.07 COMPLETE; tracker updated |
| 12 Exit item | PASS (partial) | Blood-bank slice of the 04.09 exit demo |

## 5. Tests
Named PIT **`bloodBankSafetyCoverage`** (new): deferred donor blocked/reactivated; second donation in 90 days blocked; unscreened issue blocked; CLEARED screening; wrong barcode/name 409; verified issue; crossmatch-requested + issue + reaction events; traceability chain complete.

Regressions green: `bloodBankWorkflowCoverage` (screening + MRN/name added), `eventFabricCoverage`, `consentNotificationCoverage` (catalog 42).

## 6. Accessibility
Web surface untouched; repo-wide axe gate in CI.

## 7. Dual review — adversarial pass
1. **Deferral** is a status + reason; an expired `deferredUntil` still requires reactivation (no silent auto-clear) — deliberate.
2. **Interval** uses the last donation stamp set at unit creation; back-dated collections can bypass it only through admin data entry.
3. **Screening** rejects on NAT reactive or ttI reactive and discards the unit; no override path.
4. **Identity check** compares the MRN exactly and the full name case-insensitively — a renamed patient needs the record updated first.
5. **Crossmatch** must be `compatible` for the same patient+unit; an equivocal result blocks.
6. **Traceability** is a read-model (no projection) — sufficient for the regulatory report format.
7. **Cross-test interference:** the new PIT is patient/donor-scoped; baseline blood-bank flows updated to the new gates.

## 9. Deviations from contract
- NAT integration is chunk 7; the result hand-off is stored.
- No multi-rule donor questionnaire (deferral is a manual clinical decision).
- Traceability is assembled on read.

## 8. Session trail
- V81 checksum drift after the zero-stub wording fix → local Flyway rows 81-83 reset (idempotent DDL) and re-applied in order.
- Trace assertion failed until the PIT created/confirmed a grouping (trace was empty for grouping).
- Docker Desktop died twice; compose restarted, postgres healthy.
