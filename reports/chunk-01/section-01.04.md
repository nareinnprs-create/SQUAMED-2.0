# Section 01.04 — OPD EMR core (chunk 1)

**Gate-run assessment of the CURRENT tree** @ 886c51c (merged baseline + wiring PRs #2–#11). Static read-only; no builds/tests run. Verdict after verification: **PARTIAL** → **IN PROGRESS** (named contents missing; clinical-safety cases absent). CI evidence: run 43548016617 — all 6 gates green.

## 1. Scope
Per `14-CHUNK1-SECTIONS.md`: encounter lifecycle, SOAP + specialty template notes, vitals capture, coded diagnosis (ICD-10/SNOMED), procedures, orders → tracked tasks (fulfilled chunk 2), **ePrescription** with allergy BLOCK/WARN, referral, follow-up booking, note signature (eSign + PIN fallback, immutable). Dual review (clinical write) required. CS bindings: CS-01, CS-02, CS-03, CS-12 (verified at 01.12).

## 2. Built (current tree)
- `backend/squamed-core/src/main/kotlin/com/squamed/core/encounter/domain/*.kt` — Encounter, VitalSign (incl. computed BMI, pain score), ClinicalNote (SOAP fields + isSigned/signedAt/signedBy), Diagnosis (code + system, default "ICD-10"), OrderEntry (type/code/name/status/priority/instructions), Prescription (drug, strength, form, dose, frequency, route, duration, quantity, PRN, prescribedBy/At).
- `backend/.../encounter/service/EncounterService.kt` — start/complete encounter (+ `encounter.started` event + search index), recordVitals (+ BMI calc), recentVitals, addNote, signNote (flag flip — no eSign/PIN machinery, no immutability enforcement), diagnoses add/list/remove, orders place/cancel/list, prescribe (no allergy check), encounter detail aggregate.
- `backend/.../encounter/api/EncounterController.kt` + `EncounterDtos.kt` — REST surface for all of the above.
- `apps/web/src/app/(app)/consult/page.tsx` (33.3 KB) — consult workspace UI (notes, vitals, orders incl. referral as an order-type option, prescriptions).
- `apps/web/src/app/(app)/orders/` — page.tsx + workspace.tsx (17.1 KB) + orders.test.tsx: **rebuilt by PR #8** from the former `PlaceholderPage` into a real apiFetch-wired orders tracker (catalog load, place-order form, status filters, honest empty states).
- `packages/ui/src/domain/OrderEntryForm.tsx`, `PrescriptionBuilder.tsx`, `ClinicalNoteEditor.tsx` — domain components.

## 3. Data/API/event contracts delivered
- Schema: `V12__clinical_encounter.sql` → `clinical.encounter`, `clinical.vital_sign`, `clinical.clinical_note`, `clinical.diagnosis`, `clinical.order_entry`, `clinical.prescription`.
- API: `/api/v1/encounters` (POST), `/{id}` (GET), `/{id}/notes`, `/notes/{id}/sign`, `/{id}/diagnoses` (+ delete), `/{id}/orders`, `/{id}/prescriptions`, `/{id}/vitals`, `/patients/{id}/vitals` (recent).
- Events: `encounter.started` only (complete/notes/vitals/orders/prescriptions emit none). Direct Kafka publisher (no outbox).
- Orders → tracked tasks: **NOT wired** — OrderEntry has no task linkage (chunk-2 fulfilment consumer absent).

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | **FAIL** | Encounter lifecycle, vitals (+BMI), SOAP notes + sign, diagnoses, orders, prescriptions work end-to-end (PIT journeys). Missing named contents: **specialty template notes** (free-form only), **coded diagnosis validation** (code is free text; no ICD-10/SNOMED catalog), **allergy BLOCK/WARN in prescribe** (grep: no allergy check in backend src; CS-02/CS-03 tests absent), **procedures**, **referral** (order-type string only, no referral flow), **follow-up booking** (none), **eSign/PIN signature machinery + immutability** (signNote is a boolean flip), **orders→tasks wiring**. Gap list: allergy BLOCK/WARN · coded dx catalog · eSign immutability · follow-up booking · specialty templates |
| 2 Zero-stub | PASS | CI run 43548016617 (gate 2 green). Verified: the former orders `PlaceholderPage` (audit §3.1 stub #2) is gone — rebuilt with real apiFetch wiring by PR #8 |
| 3 Types | PASS | CI run 43548016617 |
| 4 Lint + format | PASS | CI run 43548016617 |
| 5 Tests | **FAIL (partial)** | Repo-wide suite green (PIT 37/37, coverage 80.13%). But gate 5 requires **clinical-safety cases for clinical sections green** — CS-01/CS-02/CS-03/CS-12 have **no tests** (no allergy-BLOCK code exists); dual clinical review not performed |
| 6 Accessibility | PASS | CI run 43548016617 (axe 0 serious/critical). Caveat: `/consult` and `/orders` NOT in SCAN_ROUTES — not axe-scanned |
| 7 Design system | PASS | CI run 43548016617 (visual regression green) |
| 8 Contracts | **FAIL** | schema-registry harness not yet wired (00.02 scope); OpenAPI surfaces `encounters` (PIT `openApiExposesAllModuleSurfaces`); `encounter.started` event has no committed schema |
| 9 Data | **FAIL** | Migration `V12__clinical_encounter.sql` applied; **no rollback note**; **no RLS/cross-tenant denial suite** (00.04/ADR-13 pending); encounter entities carry no hospital scoping consistency check |
| 10 Audit | PASS | `AuditAspect` intercepts EncounterController writes → audit rows for all clinical writes; audit read asserted in PIT `collaborationAndWorkflowJourney`. Caveat: plain audit table (ADR-9 pending) |
| 11 Docs | PASS | This report; chunk file lists contents accurately; ledger row stays PLANNED |
| 12 Exit item | **FAIL** | no scripted slice yet (phase1-e2e.ps1 walks encounters REST; not the chunk-exit script; dual review pending) |

## 5. Tests
Named PIT tests (green in run 43548016617): `fullPatientRevenueJourney` (encounter start → vitals → note sign → prescribe → lab/pharmacy downstream), `clinicalEncounterAndPharmacyCoverage` (encounter detail, diagnoses CRUD, orders, notes, vitals, prescriptions), `openApiExposesAllModuleSurfaces` (encounters surface). Web: `orders.test.tsx` — flattenOrders, orderMatchesFilters, badge variants, buildOrderPayload, statusOptions, patientName, PlaceOrderForm (catalog load + submit, honest empty state, onError surfacing) — 47 vitest tests repo-wide green (PR #8/#10). Pass counts: PIT 37/37; vitest 47/47 (run 43548016617). Coverage: merged instruction 80.13%. Clinical-safety: none for this section.

## 6. Accessibility
Repo-wide axe gate green (run 43548016617); `/consult` and `/orders` outside the 5 scanned routes — no per-page axe evidence for this section's screens.

## 7. Issues found during build + fixes
Baseline section — see BASELINE-AUDIT.md §2.1 (encounter row: PARTIAL, no ICD-10/SNOMED validation, no allergy BLOCK/WARN, no eSign/PIN, no referral/follow-up). Verified in current tree: orders **page stub was rebuilt** by PR #8 (`reports/LEDGER.md` merge-log row #8; `apps/web/src/app/(app)/orders/` = page.tsx wrapper + workspace.tsx + tests) — this closes the audit's §3.1 stub #2; encounter module itself unchanged from baseline.

## 8. External blockers
None in code; B2 (clinical lead) is the hard human gate for the dual clinical review (per ledger aging note) before this section can leave IN PROGRESS.

## 9. Deviations from chunk file
Zero (SC-6 baseline adoption; orders-page rebuild was wiring-window scope, not a chunk-file scope change).

## 10. Exit-demo slice
`pwsh scripts/phase1-e2e.ps1` — walks `/api/v1/encounters` (start → vitals → notes → sign → prescribe). Nearest scripted evidence = PIT `clinicalEncounterAndPharmacyCoverage` + `fullPatientRevenueJourney`.
