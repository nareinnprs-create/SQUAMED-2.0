# Section 00.07 — Patient master

## 1. Scope
Registration, identifiers (MRN/ABHA/mobile/national), demographics, guardian/dependent links, patient photos (MinIO dev / S3-API prod), Postgres FTS patient search, unknown-patient flow.

## 2. Built (current tree)
- `patient/domain/PatientRecord.kt` (+ Address, Allergy, ContactPoint, PatientIdentifier, PatientNote, MedicalHistory, EpisodeOfCare) — demographics + identifiers + photoUrl column.
- `patient/service/MrnGenerator.kt` — MRN generation.
- `patient/service/PatientService.kt` — registration, find-or-create, identifier matching.
- `patient/api/PatientController.kt` + `PatientDtos.kt` — registration/list/detail APIs.
- Migration `V7__patient_identity.sql` (+ permission seed V8, audit columns V9).
- Search: patient index in `search/` module (OpenSearch, see 00.15) — not Postgres FTS.

## 3. Contracts delivered
- Registration, MRN generation, identifier CRUD, find-or-create APIs (PIT-asserted).
- NOT delivered: ABHA identifier (zero grep hits), photo upload (photoUrl column exists, no MinIO/S3 upload path in backend), guardian/dependent links, unknown-patient flow, Postgres FTS (OpenSearch only).

## 4. Gates
1. FAIL — named contents missing: ABHA identifier, photo upload flow, guardian/dependent links, unknown-patient flow, Postgres FTS.
2. PASS — CI run 43548016617 (gate 2 green).
3. PASS — CI run 43548016617 (gate 3 green).
4. PASS — CI run 43548016617 (gate 4 green).
5. PASS — CI run 43548016617 (gate 5 green); named tests below.
6. PASS — CI run 43548016617 (gate 6 green) (patients pages in axe scope).
7. PASS — CI run 43548016617 (gate 7 green).
8. FAIL — schema-registry harness not yet wired (00.02 scope).
9. FAIL — `V7__patient_identity.sql` exists; patient tables hospital-scoped (hospital_id still nullable client-supplied) and RLS cross-tenant denial suite ABSENT until 00.04 lands (ADR-13).
10. PASS — AuditAspect intercepts PatientController writes; `/audit/logs?entityType=Patient` rows asserted in PIT.
11. PASS — this report.
12. PASS — PIT `fullPatientRevenueJourney` covers register→find→treat→bill journey.

## 5. Tests
PIT: `fullPatientRevenueJourney` (registration + MRN + by-name search), `empiInputIdentifiersAndLifecycleCoverage` (identifier variants) — green, run 43548016617. Coverage ≥0.80 merged enforced.

## 6. Accessibility
patients pages in axe gate scope; run 43548016617 gate 6 green.

## 7. Issues found during build
Baseline section — see BASELINE-AUDIT.md §2.1 patient row. Verified current tree: still no ABHA/photos-upload/guardian/unknown-patient wiring from PRs; photoUrl is a stored string only.

## 8. External blockers
E1 — ABDM sandbox credentials (per ledger 00.07 Blocked exp:E1); blocks ABHA identifier work.

## 9. Deviations from chunk file
Zero; missing contents are unbuilt (E1-tracked), not re-scoped.

## 10. Exit-demo slice
PIT `fullPatientRevenueJourney` (register patient → MRN minted → searched by name → encounter → bill) — green in run 43548016617.
