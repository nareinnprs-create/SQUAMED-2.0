# Section 04.08 — IPD billing + dietary + discharge med-rec (chunk 4)

**Gate-run assessment of the CURRENT tree** @ PR #100 (chunks 0–3 + 04.01-04.03/04.05-04.07 merged + this PR). Verdict: **COMPLETE → GATE-12 PASS (partial: chunk-4 exit demo slice owed at 04.09)**. CI evidence: **run 45169393989 — all 7 gates green**. Named PIT `ipdBillingDietaryCoverage` green in the same run.

## 1. Scope
Per `chunk-04-inpatient-surgery-critical.md` §8 (room charges by bed-hour, interim bills), §9 (diet orders + kitchen manifest, allergy-aware), §10 (medication-reconciliation leg before the summary finalizes).

## 2. Key artifacts
**Migration `V84__ipd_billing_dietary.sql`** (additive; rollback note inside):
- `ipd.diet_order`; `ipd.discharge_checklist` += `med_rec_done`, `med_rec_at`.

**Behaviour:**
- Room charge by bed-hour from the admission window, priced from the active `ROOM-<bed type>` tariff (or explicit unit price), posted to the ledger as an audited `room` charge; missing tariff blocks (`ROOM_RATE_NOT_CONFIGURED`).
- Interim bill issues an invoice from the posted ledger.
- Diet orders replace the active order, reject active-allergen conflicts (`DIET_ALLERGEN_CONFLICT`), and feed `GET /beds/dietary/manifest` with patient names.
- `med_rec` checklist leg opens a `DISCHARGE` task for the attending doctor; `signSummary` requires it (`DISCHARGE_MEDREC_REQUIRED`).
- OpenAPI snapshot (`33e24f36…`), codegen stamped.

## 3. Gap closure
- ✔ Room charges by bed-hour (tariff-priced, audited)
- ✔ Interim bills from the ledger
- ✔ Allergy-aware diet orders + per-meal manifest
- ✔ Med-rec gate before the signed summary (task trail)
- ✔ PIT 04.08: `ipdBillingDietaryCoverage` PASS

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | PIT covers room charge, interim bill, rate guard, diet conflict/replacement/manifest, med-rec gate + task |
| 2 Zero-stub | PASS | CI branch run 45169393989 |
| 3 Types | PASS | CI branch run 45169393989 |
| 4 Lint + format | PASS | CI branch run 45169393989 |
| 5 Tests | PASS | CI branch run 45169393989; named PIT below |
| 6 Accessibility | PASS | CI branch run 45169393989 (web untouched) |
| 7 Design system | PASS | CI branch run 45169393989 |
| 8 Contracts | PASS | Snapshot + codegen committed; parity green |
| 9 Data | PASS | V84 additive + rollback note; diet index |
| 10 Audit | PASS | Room charge carries the reason; diet order the ordering user; med-rec task links the admission |
| 11 Docs | PASS | This report; LEDGER row 04.08 COMPLETE; tracker updated |
| 12 Exit item | PASS (partial) | IPD-billing/dietary slice of the 04.09 exit demo |

## 5. Tests
Named PIT **`ipdBillingDietaryCoverage`** (new, isolated hospital): 3h × 100 = 300 charge + interim bill 300; ICU bed without tariff → `ROOM_RATE_NOT_CONFIGURED`; peanut-free diet → `DIET_ALLERGEN_CONFLICT`, low-salt accepted, replacement keeps one active, manifest carries it; sign before med-rec 409, med-rec task opened, sign succeeds.

Regressions green: `admissionBedManagementCoverage`, `inpatientAdmissionLifecycleCoverage`, `nursingDocumentationCoverage`, `icuMonitoringCoverage`, `surgicalWorkflowCoverage`, `bloodBankWorkflowCoverage`.

## 6. Accessibility
Web surface untouched; repo-wide axe gate in CI.

## 7. Dual review — adversarial pass
1. **Bed-hour math** ceilings minutes/60 and floors at 1 hour; a same-hour admission still bills one hour (policy choice).
2. **Rate source** is the tariff table; an explicit price override is allowed for admin corrections (audited via the charge reason).
3. **Interim bills** reuse the chunk-3 engine (rate cards, tax, discounts), so IPD bills obey the same rules.
4. **Diet allergen check** is a ≥4-char token containment against active allergies; free-text diets can miss unusual synonyms (documented).
5. **One active diet order** per admission; the manifest shows only active rows.
6. **Med-rec** is enforced at signature time, so a discharge without the review cannot produce a signed summary.
7. **Cross-test interference:** the PIT uses an isolated hospital; existing discharge helpers updated to the new leg.

## 9. Deviations from contract
- Room charges are posted on demand (no nightly batch job).
- Med-rec is a checklist leg + review task (no structured med-list diff UI).
- Dietary ops beyond the manifest (tray tracking, kitchen workflow) are chunk 7.

## 8. Session trail
- The 04.01 PIT hit the med-rec gate before the ICD guard — reordered so both guards are exercised (med-rec 409 → complete → ICD 400 → sign).
- Docker Desktop died twice; compose restarted, postgres healthy.
