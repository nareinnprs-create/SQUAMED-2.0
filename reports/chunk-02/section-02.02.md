# Section 02.02 — Lab workflow core (chunk 2)

**Gate-run assessment of the CURRENT tree** @ PR #66 (V63 + lab workflow core extension). Verdict after verification: **COMPLETE** → **GATE-12 PASS**. CI evidence: run 44488415740 — all gates green incl. new PIT `labWorkflowCoreCoverage`.

## 1. Scope
Per `15-CHUNK2-SECTIONS.md`: order acceptance → phlebotomy worklist → barcode labeling → accessioning (chain-of-custody) → result entry (manual+instrument) → delta checks → pathologist validation; reject-and-recollect (never edit-in-place). Depends on chunk 1 exit.

## 2. Built (current tree)
- `backend/.../laboratory/domain/LabOrder.kt` — order entity: patient/encounter, order_number (`LO-YYYY-####`), priority, status (ordered/collected/received/processing/resulted/rejected), result_status (pending/verified/released), clinical note, lifecycle timestamps + by-ids, rejected_reason. **New (V63):** `barcode_id`, `accession_id`, `rejected_by`, `rejected_at`, `recollect_of`.
- `backend/.../laboratory/domain/LabTestRequest.kt` — per-test line: test code/name, status, result value/unit/reference range, isAbnormal, result-entered by/at. **New (V63):** `prior_value`, `delta_percent`, `entered_via` (MANUAL|INSTRUMENT, NOT NULL default MANUAL).
- `backend/.../laboratory/domain/SpecimenCustody.kt` + repository — **new** chain-of-custody entity: one row per specimen status change (event, actor, note, occurred_at).
- `backend/.../laboratory/service/LabService.kt` — createOrder (multi-test), listOrders, **phlebotomyWorklist (new: uncollected orders, priority-ranked emergency > urgent > routine by orderedAt)**, collectSpecimen (stamps **barcode = order number**), receiveSpecimen (stamps **accession = `ACC-<orderNumber>`**), startProcessing, enterResults (**delta check: priorValue + deltaPercent vs most recent prior result for same patient+testCode on a different order; enteredVia plumbed**), verifyOrder, releaseOrder, **rejectOrder(recollect=true → fresh successor order re-running the same tests, `recollectOf` link, original stays rejected)**. Status guards throughout (`LAB_ORDER_WRONG_STATUS`); every transition writes a custody event.
- `backend/.../laboratory/api/LabController.kt` + `LabDtos.kt` — REST surface for the full lifecycle **+ new `GET /api/v1/lab/phlebotomy/worklist` (laboratory:collect:lab)**; collect actor defaults to principal; reject records rejectedBy + `recollect` flag.
- `apps/web/src/app/(app)/laboratory/page.tsx` (21.9 KB) — order lifecycle + result entry UI (real page).

## 3. Data/API/event contracts delivered
- Schema: `V14__laboratory.sql` (base) + **`V63__lab_workflow_core.sql`** — additive columns, `laboratory.specimen_custody` table (+BaseEntity audit columns), worklist index `(status, priority, ordered_at)`, **rollback note in file header**.
- API: POST/GET `/api/v1/lab/orders`, GET `/{id}`, POST `/{id}/collect|receive|start|results|verify|release|reject`, **GET `/api/v1/lab/phlebotomy/worklist`**. Order detail now carries `custody[]` (SpecimenCustodyView).
- Event: `lab.result.released` on release (direct Kafka).
- Permissions: `V17` (base) + **V63 role split per chunk-02 contract — PATHOLOGIST granted `laboratory:verify:lab`, revoked from LAB_TECHNICIAN** (tech keeps collect/accession/enter; validation is pathologist-only). PIT-asserted: tech verify → 403, pathologist verify/release → 200.
- OpenAPI snapshot regenerated from live `/api-docs` (worklist endpoint + new fields included); TS client regenerated (`packages/api-client`).

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | **PASS** | CI run 44488415740. Prior gap list closed: **phlebotomy worklist** (GET endpoint, priority-ranked), **barcode labeling** (barcode_id at collect), **accessioning chain-of-custody** (ACC- id at receive + `specimen_custody` events on every transition), **delta checks** (priorValue + deltaPercent stored), **instrument result entry** (`enteredVia=INSTRUMENT`), **pathologist validation role separation** (V63 grant/revoke + PIT 403/200 asserts), **reject-and-recollect** (successor order with recollectOf link, same tests) |
| 2 Zero-stub | PASS | CI run 44488415740 (gate 2 green); LabService `toDoubleOrNull` hits are SC-9-allowlisted false positives |
| 3 Types | PASS | CI run 44488415740 |
| 4 Lint + format | PASS | CI run 44488415740 |
| 5 Tests | PASS | CI run 44488415740; named tests below; coverage verification ≥ 0.80 green |
| 6 Accessibility | PASS | CI run 44488415740 (axe 0 serious/critical). Caveat: `/laboratory` NOT in SCAN_ROUTES — not axe-scanned |
| 7 Design system | PASS | CI run 44488415740 (visual regression green) |
| 8 Contracts | **PASS** | OpenAPI snapshot regenerated from live (includes worklist + new fields); `lab` surface in PIT `openApiExposesAllModuleSurfaces`; `lab.result.released` event on release. Schema-registry harness still 00.02 scope (unchanged caveat) |
| 9 Data | **PASS** | `V63__lab_workflow_core.sql` **carries a rollback note** (additive only: drop table/columns, re-grant tech verify); tenant scoping asserted by PIT `tenantSweepDenialMatrix` (green in run 44488415740). RLS/ADR-13 remains a chunk-wide open item, unchanged by this section |
| 10 Audit | PASS | `AuditAspect` intercepts LabController writes (order create, collect, receive, start, results, verify, release, reject) → audit rows; audit read asserted in PIT `collaborationAndWorkflowJourney`. Caveat: plain audit table (ADR-9 pending) |
| 11 Docs | PASS | This report; chunk file lists contents accurately; ledger row COMPLETE |
| 12 Exit item | **PASS** | Scripted slice = PIT `labWorkflowCoreCoverage` (full workflow walk incl. worklist rank, custody chain, delta, recollect). Lab parallel-run prep itself stays 02.09 exit scope |

## 5. Tests
Named PIT tests (green in run 44488415740): **`labWorkflowCoreCoverage` (new)** — worklist priority rank (relative order vs shared seed data), barcode=orderNumber at collect, `ACC-` accession at receive, INSTRUMENT enteredVia, tech verify 403 / pathologist verify+release 200, custody event chain `COLLECTED→RECEIVED→PROCESS_STARTED→RESULTS_ENTERED→VERIFIED→RELEASED`, delta check (priorValue 5.2 → deltaPercent > 0), reject+recollect successor (fresh order, recollectOf link, same tests, original stays rejected); `fullPatientRevenueJourney` (lab order full walk), `clinicalEncounterAndPharmacyCoverage` (lab order from encounter), `openApiExposesAllModuleSurfaces` (lab surface), `tenantSweepDenialMatrix` (tenant denial). Coverage: merged instruction ≥ 0.80 verified in CI.

## 6. Accessibility
Repo-wide axe gate green (run 44488415740); `/laboratory` outside scanned routes — no per-page axe evidence.

## 7. Issues found during build + fixes
- Baseline gaps (BASELINE-AUDIT.md §2.1) closed in this section: barcode/accessioning, custody, delta checks, worklist, recollect, role separation.
- Analyzer-intake sibling hardening landed in the same PR #66 (02.01): quarantine-lane list endpoint filtered status after `take(limit)` (rows fell outside the window); quarantined payloads were not alerted on the 00.09 DLQ. Both fixed; asserted by `analyzer_intake_zero_silent_drop_coverage`.

## 8. External blockers
None for this section's code. Analyzer-driven result entry rides on 02.01 intake (landed, same PR).

## 9. Deviations from chunk file
One: delta check records prior value + % change but **does not block on a threshold** — no QC threshold table exists yet (02.03 Critical results + QC scope). Custody `receivedBy` recorded as custody event only (no dedicated received_by column was needed; accession id is stamped at receive). Barcode convention: `barcodeId` = order number stamped at collect; `accessionId` = `ACC-<orderNumber>` stamped at receive (chunk file names the accession register, not the id format).

## 10. Exit-demo slice
PIT `labWorkflowCoreCoverage` (run 44488415740) — full slice: create → worklist rank → collect (barcode) → receive (accession) → start → enter (instrument + delta) → verify/release (role-gated) → custody chain assert → reject-and-recollect. `pwsh scripts/phase1-e2e.ps1` still walks the basic lifecycle.
