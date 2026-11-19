# Section 02.03 — Critical results + QC (chunk 2)

**Gate-run assessment of the CURRENT tree** @ PR #70 (merged baseline + wiring PRs #2–#11 + Wave-3 sections 02.01/02.02 via #66 + 02.06 via #68 + this PR). Verdict after verification: **COMPLETE → GATE-12 PASS**. CI evidence: **run 44554420192 — all 7 gates green** (zero-stub, parity, source-tree, backend ktlint+test+ArchUnit, integration compose+PIT+coverage, frontend, a11y/perf). Named PIT `criticalResultsQcCoverage` green in the same run.

## 1. Scope
Per `15-CHUNK2-SECTIONS.md`: SLA escalation machinery, ack trail, dept head + CMO dashboard line; Westgard QC runs hard-block release. Depends on 02.02. CS-05 (critical result SLA escalation chain: 02.03 lab leg → 02.05 rad findings → 04.05 ICU routing → 05.04 consumer rail) binds to this section's lab leg; full role verification at 02.09. Also closes the 02.02 recorded deviation: **delta threshold blocking**.

## 2. Key artifacts
**Migration `V65__critical_results_qc.sql`** (additive; rollback note inside):
- `master_data.lab_test` += `critical_low`, `critical_high` (nullable — no thresholds = no critical detection), `critical_sla_minutes INTEGER NOT NULL DEFAULT 60`.
- `laboratory.lab_test_request` += `is_delta_blocked BOOLEAN NOT NULL DEFAULT FALSE`.
- `laboratory.critical_result` — one row per threshold-crossing result: hospital/order/request/patient scoped, `criticality CRITICAL_LOW|CRITICAL_HIGH`, `status OPEN|ACKNOWLEDGED`, `sla_minutes` (snapshot from master), `acked_by/acked_at`.
- `laboratory.critical_result_ack` — append-only ack trail (critical_result_id, actor_id, note, occurred_at).
- `laboratory.qc_run` — instrument (nullable), test_code, `level LOW|NORMAL|HIGH`, observed_value, target_mean, target_sd, `westgard_rules` CSV, `status ACCEPTED|WARNING|REJECTED`, note, run_by, run_at.

**Service (`LabService.kt`):**
- **Critical detection** in `enterResults`: numeric result vs the active master-test row by test code (`findByCode` + isActive); value < critical_low → CRITICAL_LOW, > critical_high → CRITICAL_HIGH. Missing master row or null thresholds → no row (existing behavior unchanged).
- **Delta block (02.02 deviation closed)**: threshold **100.0%**; `applyDeltaCheck` flags `is_delta_blocked` when |Δ|/prior ≥ threshold; `verifyOrder` rejects with **`DELTA_THRESHOLD_BLOCK`** when any test is blocked and no `overrideNote` is supplied; override is recorded in the custody trail ("delta override: …").
- **Westgard multi-rule** evaluated at QC-run creation against the last 10 runs for the same hospital+test code (chronological z history): **1_3S** (|z|≥3), **2_2S** (current+previous same side, both |z|≥2), **R_4S** (current+previous opposite sides, both |z|≥2), **4_1S** (4 consecutive same side |z|≥1), **10X** (10 consecutive same side of mean) → REJECTED; **1_2S** (2≤|z|<3) → WARNING; none → ACCEPTED. Fired rules stored as CSV. `QC_TARGET_SD_INVALID` on sd ≤ 0.
- **Release hard-blocks** (in order): **`QC_BLOCK`** — the latest qc_run for any test on the order is REJECTED (absence of QC runs does not block — standard LIS behavior); then **`CRITICAL_RESULT_UNACKED`** — any OPEN critical result on the order.
- **Ack trail**: `POST /critical-results/{id}/ack` requires OPEN, appends the trail row, flips status + stamps acked_by/acked_at; second ack → **`CRITICAL_RESULT_ALREADY_ACKNOWLEDGED`**.
- **SLA escalation** read-evaluated on the dashboard line: minutesOpen ≥ 2× sla → level 2 (`cmo`), ≥ sla → level 1 (`dept_head`), else 0 (`none`) — plus minutesOpen/slaMinutes fields (deviation, §9).

**API (`LabController.kt`)** — 5 new endpoints under `/api/v1/lab`: `GET /critical-results?status=`, `GET /critical-results/{id}/acks`, `POST /critical-results/{id}/ack`, `POST /qc-runs`, `GET /qc-runs`; all reuse **V17** permission codes (`laboratory:read:lab` / `laboratory:write:lab` / `laboratory:verify:lab`) — no new codes seeded. `POST /orders/{id}/verify` now accepts optional `VerifyLabOrderDto(overrideNote)`. Master-data lab-test CRUD carries the three critical fields through `LabTestDto`.

**Contracts** — `docs/contracts/openapi-v1.json` regenerated from live `/api-docs`; `packages/api-client/src/generated/schema.d.ts` re-stamped (sha256 659605bc…33e6369a). Events unchanged.

## 3. Gap closure
- ✔ Critical result detection at result entry (master-threshold driven)
- ✔ Ack trail (append-only rows; acked-by/at stamps; double-ack rejected)
- ✔ SLA escalation machinery + dept head + CMO dashboard line (escalationLevel/escalationRole on every critical-result read)
- ✔ Delta threshold blocking with audited override (02.02 deviation closed)
- ✔ Westgard QC runs (1_3S/1_2S/2_2S/R_4S/4_1S/10X) with rules CSV on the run
- ✔ QC hard-block on release (latest REJECTED run blocks; no-QC-history passes)
- ✔ Release blocked while any critical result on the order is unacked
- ✔ PIT 02.03: `criticalResultsQcCoverage` PASS

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | All named contents implemented; PIT walk: CRITICAL_LOW/HIGH detection, UNACKED block, ack trail + double-ack, DELTA_THRESHOLD_BLOCK + override, 1_3S REJECT → QC_BLOCK, ACCEPTED → release, 1_2S WARNING, dashboard metadata |
| 2 Zero-stub | PASS | CI branch run (PR #70) |
| 3 Types | PASS | CI branch run (PR #70) |
| 4 Lint + format | PASS | CI branch run (PR #70) (ktlintFormat produced no changes) |
| 5 Tests | PASS | CI branch run (PR #70); named PIT below. Role-mapping legs deferred to 02.09 (§9) |
| 6 Accessibility | PASS | CI branch run (PR #70) (axe 0 serious/critical). Web surface untouched by this PR |
| 7 Design system | PASS | CI branch run (PR #70) |
| 8 Contracts | PASS | OpenAPI snapshot regenerated + committed (same PR); codegen stamped; PIT `openApiSnapshotMatchesLiveContract` green in the same run |
| 9 Data | PASS | V65 additive with rollback note; tenant pinning: QC writes pinned via `tenantResolver.pinOrDefault`, cross-hospital reads null-safe (`:hid is null or …`), `requireSameHospital` on ack mutations. RLS stage-2 remains repo-wide ADR-13 debt (not this section) |
| 10 Audit | PASS | AuditAspect covers the new write endpoints (controller-intercepted); ack trail itself is an auditable history; verify-override recorded in custody trail |
| 11 Docs | PASS | This report; LEDGER row 02.03 COMPLETE; tracker updated |
| 12 Exit item | PASS (partial) | SLA escalation drill at 02.09 chunk exit (per chunk file); this section supplies the PIT evidence |

## 5. Tests
Named PIT (green in the PR #70 branch run): **`criticalResultsQcCoverage`** (new, 02.03): master test with criticalLow 2.0 / criticalHigh 10.0 / sla 60 → order A result 1.0 → one OPEN critical (CRITICAL_LOW, level 0/none) → verify ok → release 4xx `CRITICAL_RESULT_UNACKED` → ack (trail row "called attending", ack flips status, second ack 4xx) → release 200 → order B result 12.0 → verify 4xx `DELTA_THRESHOLD_BLOCK` (prior 1.0, Δ 1100%) → verify with overrideNote 200 → `isDeltaBlocked` flagged + open CRITICAL_HIGH → order C result 12.0 (flat delta vs B) → verify ok → QC z=5.0 → `1_3S` REJECTED → release 4xx `QC_BLOCK` → QC z=0 ACCEPTED → release 4xx `CRITICAL_RESULT_UNACKED` → ack → release 200 → QC z=2.5 `1_2S` WARNING → dashboard: qc history (3 runs, latest-first), critical-result lines carry escalationLevel/escalationRole/slaMinutes (60 from master), 2 ACKNOWLEDGED of 3. Regression legs green locally in the same session: `labWorkflowCoreCoverage`, `clinicalEncounterAndPharmacyCoverage`, `fullPatientRevenueJourney`.

## 6. Accessibility
Repo-wide axe gate green (PR #70 branch run); web surface untouched by this PR — no per-page axe delta.

## 7. Issues found during build + fixes
- **Jackson two-letter-camelCase mangling**: `zScore` field serialized under a mangled name (`getZScore` → non-obvious key), PIT got null. Fixed by renaming to `zValue` + explicit `@get:JsonProperty("zValue")`.
- **Admin principal has null hospitalId**: `POST /qc-runs` failed `HOSPITAL_REQUIRED` for the platform-admin PIT token. Fixed with `tenantResolver.pinOrDefault` (same pattern as `createOrder`).
- **View gap**: `isDeltaBlocked` initially missing from `LabTestRequestView` — block worked server-side but was invisible on reads; added to the view.

## 8. External blockers
None for this section's code. Westgard history scoping (hospital+test code, ignoring instrument/level) is an internal simplification — QC manager multi-instrument history stays with the LIS vendor lane if ever needed.

## 9. Deviations from chunk file
- **SLA escalation computed on read** — no persisted escalation rows / notification fan-out; `escalationLevel`/`escalationRole` derived from minutesOpen vs slaMinutes on every critical-result read (same read-evaluated pattern as 02.06 min-level alerts). Notification integration stays with the 00.12 engine.
- **Escalation drill + full role verification (dept head/CMO roles on ack) deferred to 02.09** (CS-05 chain exit); here the machinery + dashboard line are real and PIT-verified at level 0 (fresh criticals).
- **QC history scoped to hospital+test code** (instrument/level not part of the westgard history key); instrument is stored per run.

## 10. Exit-demo slice
PIT `criticalResultsQcCoverage` (PR #70 branch run); 02.09 chunk exit assembles the CS-05 escalation drill slice.
