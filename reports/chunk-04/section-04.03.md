# Section 04.03 — Assessments + care plans + risk scores (chunk 4)

**Gate-run assessment of the CURRENT tree** @ PR #94 (chunks 0–3 + 04.01/04.02 merged + this PR). Verdict: **COMPLETE → GATE-12 PASS (partial: chunk-4 exit demo slice owed at 04.09)**. CI evidence: **run 45090008029 — all 7 gates green**. Named PIT `nursingAssessmentsCareplansRisksCoverage` green in the same run.

## 1. Scope
Per `chunk-04-inpatient-surgery-critical.md` §2: assessment forms (configurable), care plans (problem → intervention → goal, templates), risk assessments (falls/pressure-ulcer scores, configurable scales). Vitals flowsheets, nursing notes and SBAR handover remain baseline code (regression-covered).

## 2. Key artifacts
**Migration `V79__assessments_careplans_risks.sql`** (additive; rollback note inside):
- `nursing.assessment_template` (hospital + code unique, fields JSON), `nursing.careplan_template` (problem/goal/interventions), `nursing.risk_scale` (min/low_max/moderate_max).
- `nursing_assessment` += `template_code`, `fields_json`; `nursing_careplan` += `problem`, `template_code`; `risk_assessment` += `scale_code`.

**Behaviour:**
- Assessment templates define required fields; creation with `templateCode` validates presence (`ASSESSMENT_FIELD_REQUIRED`), stores values + provenance, rejects unknown/inactive templates.
- Care-plan templates carry problem/interventions/goal; `/careplans/apply` instantiates a plan with provenance; creation and status changes emit `CarePlanUpdated`.
- Risk scales validate thresholds; an assessment with `scaleCode` derives `low|moderate|high` from the score and rejects scores below the minimum (`RISK_SCORE_OUT_OF_RANGE`); without a scale the caller's level is kept.
- Catalog 37 → 38 (`CarePlanUpdated` EMITTED).
- OpenAPI snapshot regenerated (`8b65df42…`), codegen stamped.

## 3. Gap closure
- ✔ Configurable assessment forms with required fields
- ✔ Care-plan templates (problem → intervention → goal) + provenance + event
- ✔ Configurable risk scales deriving the level
- ✔ PIT 04.03: `nursingAssessmentsCareplansRisksCoverage` PASS

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | PIT covers templates, required fields, plan application, scale derivation, guards |
| 2 Zero-stub | PASS | CI branch run 45090008029 |
| 3 Types | PASS | CI branch run 45090008029 |
| 4 Lint + format | PASS | CI branch run 45090008029 |
| 5 Tests | PASS | CI branch run 45090008029; named PIT below |
| 6 Accessibility | PASS | CI branch run 45090008029 (web untouched) |
| 7 Design system | PASS | CI branch run 45090008029 |
| 8 Contracts | PASS | Snapshot + codegen committed; parity green |
| 9 Data | PASS | V79 additive + rollback note; per-hospital unique template/scale codes |
| 10 Audit | PASS | Assessments/plans/scales carry actors; plan events carry patient + change |
| 11 Docs | PASS | This report; LEDGER row 04.03 COMPLETE (totals count corrected to 41/12/3/34 at start, now 42/11/3/34); tracker updated |
| 12 Exit item | PASS (partial) | Nursing-forms slice of the 04.09 exit demo |

## 5. Tests
Named PIT **`nursingAssessmentsCareplansRisksCoverage`** (new, isolated hospital): template fields stored/listed; assessment with both fields echoes values + provenance; missing field 400 `ASSESSMENT_FIELD_REQUIRED`; unknown template 400 `ASSESSMENT_TEMPLATE_NOT_FOUND`; care-plan template applied (problem/goal/interventions + provenance); completion emits `CarePlanUpdated`; falls scale 1→low / 4→moderate / 8→high; score −1 → 400 `RISK_SCORE_OUT_OF_RANGE`; manual level without a scale kept.

Regressions green: `nursingDocumentationCoverage`, `emarNursingCoreCoverage`, `eventFabricCoverage`, `consentNotificationCoverage` (catalog 38).

## 6. Accessibility
Web surface untouched; repo-wide axe gate in CI.

## 7. Dual review — adversarial pass
1. **Required fields** are enforced server-side from the template; free-text values keep the model simple (typed widgets are UI).
2. **Template scope** is per hospital + code unique; a second POST updates the template in place (no versioning) — noted.
3. **Care-plan provenance** records the template code so an applied plan is traceable after template edits.
4. **Risk level derivation** is deterministic (low ≤ lowMax, moderate ≤ moderateMax, else high); thresholds validated on upsert.
5. **Scale range** only rejects below the minimum; a clinically impossible high score is not capped (scales with a max can add it later).
6. **Legacy path** without a scale keeps the caller's level — the baseline PITs remain valid.
7. **Cross-test interference:** the PIT uses an isolated hospital; template/scale codes are suffixed.

## 9. Deviations from contract
- No global template catalogue (per-hospital data).
- Care-plan goal evaluation (met/unmet scoring) is not modelled.
- Handover/SBAR and vitals flowsheets stay baseline (regression-covered).

## 8. Session trail
- LEDGER totals line corrected before this section (it had drifted: 00.14 double-counted as COMPLETE while IN PROGRESS rows were undercounted) — real counts 41/12/3/34 at the start of 04.03.
- Docker Desktop died once; compose restarted, postgres healthy.
