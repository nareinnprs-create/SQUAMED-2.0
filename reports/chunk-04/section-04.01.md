# Section 04.01 — Admission + bed management (chunk 4)

**Gate-run assessment of the CURRENT tree** @ PR #91 (chunks 0–3 merged + this PR). Verdict: **COMPLETE → GATE-12 PASS (partial: chunk-4 exit demo slice owed at 04.09)**. CI evidence: **run 44989139607 — all 7 gates green**. Named PIT `admissionBedManagementCoverage` green in the same run.

## 1. Scope
Per `chunk-04-inpatient-surgery-critical.md` §1: admission request (ER/OPD/elective), bed allocation with class/isolation/gender-mix rules, transfers with full trace, expected-discharge planning, a live bed board (occupied/reserved/cleaning/available), and the discharge checklist workflow (pharmacy returns, final bill, summary signature). Discharge stays a Temporal-free state machine per the contract.

## 2. Key artifacts
**Migration `V77__admission_bed_management.sql`** (additive; rollback note inside):
- `hospital_config.bed` += `isolation_type` + `gender_policy`; `(hospital_id, status)` index.
- `ipd.admission` += `expected_discharge_at`, `isolation_type`.
- `ipd.discharge_summary` += `status` (`DRAFT|SIGNED`), `icd_codes`, `signed_by/at` — signed summaries are immutable.
- `ipd.discharge_checklist` — pharmacy returns + final-bill verification + completion stamp (one per admission).

**Behaviour:**
- Allocation (requested or auto) enforces isolation equality and the bed's gender policy against the patient's gender → `ADMISSION_BED_RULE_VIOLATION`; transfers enforce the same.
- Bed board endpoint with the live occupant; `POST /beds/{id}/cleaning|available` for housekeeping transitions.
- `POST /admissions/{id}/plan-discharge` → expected date + `DischargePlanned`.
- Discharge gate: pharmacy returns cleared + final bill verified (no unpaid/partial invoices) + SIGNED summary with ICD codes, else 409 `DISCHARGE_CHECKLIST_INCOMPLETE`; the bill leg rejects an unsettled bill (`DISCHARGE_BILL_UNSETTLED`).
- Bed release → `cleaning` + housekeeping task + `HousekeepingTaskCreated`; housekeeping clears to `available`.
- Events catalog 26 → 34 (all eight 04.01 events EMITTED).
- OpenAPI snapshot regenerated (`45dd4cc8…`), codegen stamped.

## 3. Gap closure
- ✔ Admission + bed allocation rules (isolation, gender)
- ✔ Transfers with trace + rule enforcement
- ✔ Expected-discharge planning
- ✔ Bed board (occupied/cleaning/available + occupant)
- ✔ Checklist-gated discharge + signed coded summary + housekeeping task
- ✔ PIT 04.01: `admissionBedManagementCoverage` PASS

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | PIT covers rules, board, planning, transfer trace, checklist gates, housekeeping |
| 2 Zero-stub | PASS | CI branch run 44989139607 |
| 3 Types | PASS | CI branch run 44989139607 |
| 4 Lint + format | PASS | CI branch run 44989139607 |
| 5 Tests | PASS | CI branch run 44989139607; named PIT below |
| 6 Accessibility | PASS | CI branch run 44989139607 (web untouched) |
| 7 Design system | PASS | CI branch run 44989139607 |
| 8 Contracts | PASS | Snapshot + codegen committed; parity green |
| 9 Data | PASS | V77 additive + rollback note; one checklist per admission; signed summary immutable |
| 10 Audit | PASS | Admission/transfer/summary/checklist carry actors; housekeeping task linked to the bed |
| 11 Docs | PASS | This report; LEDGER row 04.01 COMPLETE; chunk-04 tracker created |
| 12 Exit item | PASS (partial) | Admission/bed slice of the 04.09 exit demo |

## 5. Tests
Named PIT **`admissionBedManagementCoverage`** (new, isolated hospital): gender + isolation rule rejections; admission occupies the bed; created/admitted/bed-assigned events; plan-discharge; transfer rule rejection then a valid transfer with the from-bed trace; early discharge 409; summary signature needs ICD and cannot repeat; pharmacy + bill legs; discharge → bed cleaning + housekeeping task; housekeeping clears; unsettled invoice blocks the bill leg; eight catalog entries EMITTED.

Regressions green: `inpatientAdmissionLifecycleCoverage` (checklist-gated discharge + housekeeping-cleared bed reuse), `nursingDocumentationCoverage`, `icuMonitoringCoverage`, `surgicalWorkflowCoverage`, `bloodBankWorkflowCoverage`, `eventFabricCoverage`, `consentNotificationCoverage`.

## 6. Accessibility
Web surface untouched; repo-wide axe gate in CI.

## 7. Dual review — adversarial pass
1. **Allocation rules** compare the admission's isolation flag exactly with the bed's — a non-isolation patient cannot occupy an isolation bed (capacity kept for IPC needs) and vice versa.
2. **Gender policy** is per bed (`ANY|MALE|FEMALE`); a patient without a gender on record bypasses the check (data-quality path).
3. **Discharge gate** is server-side; the final-bill leg re-checks invoices at completion time so a bill raised after the checklist still blocks.
4. **Signed summary immutability:** upsert after signing → 400 `SUMMARY_ALREADY_SIGNED`; corrections would need an amendment workflow (noted).
5. **Bed release** always goes through cleaning; housekeeping tasks carry the bed id so the board and task list reconcile.
6. **Transfers** keep the isolation/gender rules and log the from-bed; the old bed returns to available directly (a transfer is not a terminal cleaning event) — noted.
7. **Cross-test interference:** the PIT uses an isolated hospital; the baseline inpatient PITs were updated to the new gate (checklist + signed summary) and their cleanup discharges run through the same gate.

## 9. Deviations from contract
- Bed `reserved` is not modelled yet (OT reservations arrive with 04.06).
- Isolation flags are captured on the admission (IPC stub), not fetched from an IPC service.
- Discharge summary amendments are not built (signed = final).

## 8. Session trail
- Baseline `inpatientAdmissionLifecycleCoverage` discharged without the checklist → updated to the gated flow (summary draft + sign + checklist) and to clear the bed via housekeeping before reuse.
- Nursing and ICU cleanup discharges moved to a shared `dischargeWithChecklist` helper.
- Docker Desktop died twice; compose restarted, postgres healthy.
