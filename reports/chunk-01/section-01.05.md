# Section 01.05 — Patient 360 v1 (chunk 1)

**Gate-run assessment of the CURRENT tree** @ 886c51c (merged baseline + wiring PRs #2–#11). Static read-only; no builds/tests run. Verdict after verification: **PARTIAL** → **IN PROGRESS** (named contents missing). CI evidence: run 43548016617 — all 6 gates green.

## 1. Scope
Per `14-CHUNK1-SECTIONS.md`: demographics, allergies, conditions, encounters, meds, vitals trend, documents, consents, tasks; persistent context bar.

## 2. Built (current tree)
- `backend/squamed-core/src/main/kotlin/com/squamed/core/patient360/service/Patient360Service.kt` — single aggregate: demographics, latest + recent-10 vitals (trend source), last-20 encounters (each with diagnoses/prescriptions/orders/note-count), allergies, notes, medical history, last-20 appointments, last-20 visits, lab orders (+ total), last-20 medications (dispensations), charges, invoices (+ total), tasks (+ open/completed task summary).
- `backend/.../patient360/api/Patient360Controller.kt` — GET `/api/v1/patient360/{patientId}`.
- `backend/.../patient360/api/Patient360Dtos.kt` — Patient360View + Encounter360View + TaskSummaryView.
- `apps/web/src/app/(app)/patients/[id]/overview/page.tsx` (34.5 KB) — 360 UI: stat cards, allergies (severity badges), tasks (open/completed), encounters, meds, vitals (real page).

## 3. Data/API/event contracts delivered
- API: `/api/v1/patient360/{patientId}` (read-only aggregate; joins encounter/lab/pharmacy/billing/task/patient repos — read model, no events).
- No new schema (composes existing tables from V7/V10/V11/V12/V14/V15/V16/V19).

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | **FAIL** | Aggregate endpoint + overview UI deliver demographics, allergies, conditions (medical history), encounters, meds, vitals trend (latest + recent-10), tasks. Missing named contents: **documents** (no documents section — grep 0 hits in Patient360View/overview page), **consents** (absent; consent core itself is a chunk-0 gap, audit §6), **persistent context bar** (no sticky/context-bar component in overview page grep). Gap list: documents · consents · persistent context bar |
| 2 Zero-stub | PASS | CI run 43548016617 (gate 2 green); no stub markers in patient360 module or overview page |
| 3 Types | PASS | CI run 43548016617 |
| 4 Lint + format | PASS | CI run 43548016617 |
| 5 Tests | PASS | CI run 43548016617; named tests below; merged instruction coverage 80.13% ≥ 0.80 |
| 6 Accessibility | PASS | CI run 43548016617 (axe 0 serious/critical). Caveat: `/patients/[id]/overview` NOT in SCAN_ROUTES — not axe-scanned |
| 7 Design system | PASS | CI run 43548016617 (visual regression green) |
| 8 Contracts | **FAIL** | schema-registry harness not yet wired (00.02 scope); OpenAPI surfaces `patient360` (PIT `openApiExposesAllModuleSurfaces`) |
| 9 Data | **FAIL** | Read-only section; no own migration. Rollback/RLS N/A directly, but tenant scoping of the aggregate rides nullable `hospital_id` on source tables — cross-tenant denial suite absent (00.04/ADR-13 pending) → FAIL by convention |
| 10 Audit | PASS | Read-only endpoint; `AuditAspect` covers the controller (read logged); no write paths of its own |
| 11 Docs | PASS | This report; chunk file lists contents accurately; ledger row stays PLANNED |
| 12 Exit item | **FAIL** | no scripted slice yet (phase1-e2e.ps1 walks `/api/v1/patient360/`; not the chunk-exit script) |

## 5. Tests
Named PIT tests (green in run 43548016617): `searchAndPatient360Journey` (asserts demographics.id/mrn, encounters, labOrders, tasks present in aggregate), `fullPatientRevenueJourney` (patients/{id}/allergies feed). Pass counts: PIT 37/37. Coverage: merged instruction 80.13% (branch 52%).

## 6. Accessibility
Repo-wide axe gate green (run 43548016617); `/patients/[id]/overview` outside scanned routes — no per-page axe evidence.

## 7. Issues found during build + fixes
Baseline section — see BASELINE-AUDIT.md §2.1 (patient360 row: PARTIAL — "consents/documents/med-trend completeness unverified"). Verified in current tree: consents + documents genuinely absent from both the backend view and the overview page; med trend = dispensation list (not a trend chart). Audit's uncertainty resolved: gaps confirmed.

## 8. External blockers
None (consents depend on chunk-0 00.12 consent core, a build dependency not an external blocker).

## 9. Deviations from chunk file
Zero (SC-6 baseline adoption).

## 10. Exit-demo slice
`pwsh scripts/phase1-e2e.ps1` — walks `/api/v1/patient360/`. Nearest scripted evidence = PIT `searchAndPatient360Journey`.
