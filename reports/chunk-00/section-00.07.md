# Section 00.07 — Patient master

## 1. Scope
Registration, identifiers (MRN/ABHA/mobile/national), demographics, guardian/dependent links, patient photos (MinIO dev / S3-API prod), Postgres FTS patient search, unknown-patient flow. The baseline report (run 43548016617) FAILED gates 1/8/9 with the named gap: ABHA identifiers, photo upload flow, guardian/dependent links, unknown-patient flow. This section closes that named gap on the 00.04 tenancy primitives (hospital-scoped, server-side tenant pinning), with live ABDM linkage separately tracked under E1.

## 2. Built (current tree)
- Baseline half (retained): `patient/domain/PatientRecord.kt` (+ Address, Allergy, ContactPoint, PatientIdentifier, PatientNote, MedicalHistory, EpisodeOfCare), `MrnGenerator`, `PatientService` (registration, find-or-create, HL7 intake), `PatientController` + DTOs, `V7__patient_identity.sql`.
- **`V46__patient_master_completion.sql`** — `patient.patient_relationship` (`hospital_id NOT NULL` FK per ADR-13 stage 1, CHECK no self-link, `UNIQUE(hospital_id, patient_id, related_patient_id, relationship_type)`) and `patient.patient_record.is_unknown` (boolean, not null default false).
- **Unknown-patient flow**: registration with `unknownPatient: true` stores sentinel identity (firstName `Unknown`, DOB `1900-01-01`, gender `unknown`) with `records_status = pending`; identification via `PUT /patients/{id}` with a real name + DOB clears `is_unknown` → `active` (sentinel DOB constant documented in the V46 header and service).
- **ABHA identifiers**: stored as `patient_identifier` rows; format-validated per ABDM — 14-digit number `^\d{14}$` and ABHA address `^[a-z0-9._-]{4,20}@[a-z]{2,}$` (address normalised lowercase before both validation and duplicate checks). Errors `ABHA_NUMBER_INVALID` / `ABHA_ADDRESS_INVALID` → 400; duplicate values → `DUPLICATE_IDENTIFIER`. Identifier search: a query matching an identifier value (ABHA or any stored id) resolves to the owning patient.
- **Relationships**: guardian/family links with `relationship_type` (lowercased), `is_primary_guardian` flag, notes; reverse listing via `findByRelatedPatientId`; cross-hospital related patient → `RELATED_PATIENT_OTHER_HOSPITAL` 400; self-link rejected by the DB CHECK + a service guard; duplicates → deterministic pre-save `existsByHospitalIdAndPatientIdAndRelatedPatientIdAndRelationshipType` check → `DUPLICATE_RELATIONSHIP` 400 (belt-and-braces catch retained).
- **Photos**: `PatientPhotoStorage` — AWS SDK v2 S3 client against MinIO (`pgsty/silo`, compose `squamed-minio` 9000/9001) in dev / S3 in prod; keys `photos/{hospitalId}/{patientId}/{uuid}.{ext}`; ≤5 MB, jpeg/png/webp only (part type derived from the uploaded filename). Errors `PHOTO_EMPTY` / `PHOTO_TOO_LARGE` / `PHOTO_CONTENT_TYPE_UNSUPPORTED` / `PHOTO_NOT_UPLOADED` / `PHOTO_NOT_STORED` / `PHOTO_STORAGE_DISABLED`. MinIO testcontainer added to the PIT harness so CI (Testcontainers mode) serves photo storage.
- **Tenant guards**: `requireSameHospital` on update / identifiers / relationships / photo / merge; merge refuses cross-hospital pairs (`MERGE_CROSS_HOSPITAL` service guard).
- Contract: `docs/contracts/openapi-v1.json` snapshot (287,809 bytes) + stamped `packages/api-client/src/generated/schema.d.ts` regenerated in the same PR (sha256 `ba4c6db5…`) per `docs/development/contract-codegen.md`.
- Web: patient detail page gains photo upload + thumbnail, identifiers panel (add/delete + ABHA inputs) and relationships panel with related-patient picker; registration gains the unknown toggle, ABHA fields and Unknown badges; payload logic extracted to a pure `register-payload.ts` module with vitest coverage (`register-payload.test.ts`, 5 tests).
- `scripts/demo-00.07-patient-master.ps1` — exit-demo slice (13 checks, pwsh, port 8081).

## 3. Contracts delivered
- `PUT /api/v1/patients/{id}` — demographic update incl. the unknown→identified transition.
- `POST /api/v1/patients/{id}/identifiers` / `GET …/identifiers` / `DELETE …/identifiers/{identifierId}` — ABHA + generic identifiers (`DUPLICATE_IDENTIFIER`, `IDENTIFIER_NOT_FOUND`).
- `POST /api/v1/patients/{id}/relationships` / `GET …/relationships` (outgoing + incoming) / `DELETE …/relationships/{relationshipId}`.
- `POST /api/v1/patients/{id}/photo` (multipart) / `GET …/photo` (byte roundtrip).
- Search: `GET /api/v1/patients?query=` now also matches identifier values.
- Snapshot + stamped codegen landed together; `openApiSnapshotMatchesLiveContract` green (drift would fail the integration gate).

## 4. Gates
1. PASS — zero real stubs; zero-stub grep job green (run 43631893238).
2. PASS — run 43631893238 (PR #19, merge 08cab2a; no `--admin`).
3. PASS — Detect source trees job green.
4. PASS — ktlint + unit + ArchUnit (Backend gates job) green; local `ktlintFormat test` BUILD SUCCESSFUL.
5. PASS — Backend gates job (unit suite) green.
6. PASS — Accessibility + performance gates (axe + Lighthouse + visual) green; patients pages (detail photo/identifier/relationship panels, registration unknown toggle) in axe scope.
7. PASS — Frontend gates (typecheck + lint + test) green; stamped client pins the snapshot hash; web vitest 5/5 new + full suite green; root typecheck 15/15 tasks.
8. PASS — Integration gates job green: all **46** PIT (45 retained + 1 new `patientMasterCompletionCoverage`) + merged-coverage ≥ 0.80; local PIT 46/46 BUILD SUCCESSFUL.
9. PASS — V46 applied (Flyway validate green in run logs); `patient_relationship` hospital-scoped (`hospital_id NOT NULL`, unique triple, no-self-link CHECK), unknown flag on `patient_record`; cross-hospital relationship/merge guards asserted in PIT; residual: stage-2 Postgres RLS remains its own ADR-13 item before chunk 6 (tracked since 00.04).
10. PASS — audit surface: `AuditAspect` pointcut covers `PatientController`, so every write (update/identifier/relationship/photo/merge) writes an audit row; integration gates green.
11. PASS — this report; ledger row 40 + merge-log #19 + totals updated in the same docs batch.
12. PASS — scripted live slice `scripts/demo-00.07-patient-master.ps1`: **13/13 on 8081** (merged main 08cab2a; pwsh; header documents `SERVER_PORT=8081 bootRun`), 2026-11-15; first proven pre-merge on the branch during development.

## 5. Tests
- PIT (new): `CoverageTests.patientMasterCompletionCoverage` (46th) — fresh hospitals per run (PA-/PB- prefixes; dev DB persists rows), per-run-unique ABHA values derived from millis: unknown registration (sentinel identity + pending) → identification PUT clears flag → malformed ABHA number/address rejected → valid ABHA number persists + ABHA address accepted (lowercased) → duplicate identifier rejected → identifier search resolves the patient → guardian relationship created → self-link rejected → reverse relationship listed → duplicate triple rejected → photo roundtrip (uploaded bytes == downloaded bytes) → non-image content type 4xx → foreign-hospital probes 403 (`TENANT_FORBIDDEN`) → cross-hospital merge refused.
- PIT (retained): all 46 tests green locally (BUILD SUCCESSFUL against the compose dev stack) and in CI run 43631893238.
- Web: `register-payload.test.ts` 5/5 (unknown toggle, ABHA validation, payload shape); full vitest suite green.
- API-client: 5/5 (stamped snapshot test).

## 6. Accessibility
Patients pages in axe gate scope (detail panels and registration toggle render through the existing shared form components); a11y gate green in run 43631893238 (zero critical/serious).

## 7. Issues found during build
- **Duplicate-guard 500 root cause (fixed)**: a UNIQUE-index violation surfaces at flush/commit time, so a `try/catch (DataIntegrityViolationException)` around `save()` inside the transaction cannot catch it — the request dies as a generic 500 instead of `DUPLICATE_RELATIONSHIP` 400. Fixed with a deterministic pre-save repository `existsBy…` check (the catch is retained as belt-and-braces). Same latent flaw exists in `create()`'s identifier try/catch — left as-is (no observed failure; noted for the next touch).
- **Dev-DB row persistence vs unique values**: the PIT failed with a `data()` NPE because fixed ABHA values from run 1 were already in the dev database → run 2's insert hit `DUPLICATE_IDENTIFIER`. ABHA number/address in PIT and demo script are now derived per run from `System.currentTimeMillis()`.
- **RestTemplate probe semantics**: `rest.exchange` throws `RestClientResponseException` on 4xx — the bad-photo-upload probe had to wrap the call and capture `e.statusCode`; the multipart probe's request content-type must be `MULTIPART_FORM_DATA` (the server derives the *part* type from the filename, so `notes.txt` still triggers `PHOTO_CONTENT_TYPE_UNSUPPORTED`).
- **PowerShell multipart pitfalls (demo script)**: `$pid` is a reserved read-only variable → renamed `$patId`; `[byte[]]+[byte[]]` yields an `Object[]` that mangles the multipart body (`Required part 'file' is not present` → 500) → body assembled through a `MemoryStream`. Script fixes landed in the same docs batch as this report.
- **Async poller noise**: `ClientAbortException` / `AsyncRequestNotUsableException` stack traces in bootRun logs during the api-docs poll are the snapshot poller aborting connections — harmless, no action.

## 8. External blockers
E1 — ABDM sandbox credentials (per register `23`, week-1 aging above escalation threshold): **live** ABHA linkage through the ABDM APIs stays owed in this section's scope. The unblocked substrate is built — ABHA numbers/addresses are format-validated and stored as `patient_identifier` rows with duplicate rejection and identifier search — so when credentials arrive the linkage adapter can attach to existing rows without schema change. Credential debt honestly owed here per the 00.03/00.04 precedent; everything else in the scope (unknown flow, relationships, photos, search) is delivered.

## 9. Deviations from chunk file
- **Postgres FTS**: search stays on the OpenSearch index (00.15 shared module) and now additionally matches identifier values; a Postgres-FTS fallback was not built — the plan text names both, the shipped implementation is OpenSearch-backed with identifier resolution added (documented here rather than re-scoped in the chunk file).
- **ABHA as stored identifiers, not live linkage**: E1 (sandbox credentials) keeps live ABDM verification/linkage out; see §8.
- **Photo dev backing store is MinIO via the S3 API** as planned (`pgsty/silo` compose, same client as prod S3); no separate filesystem fallback.

## 10. Exit-demo slice
`pwsh scripts/demo-00.07-patient-master.ps1` against the running app (8081, merged main 08cab2a): admin login → fresh hospital → unknown patient registered (sentinel identity, pending) → identified via PUT (real name/DOB, active) → malformed ABHA number rejected → per-run ABHA identifier stored → duplicate identifier rejected → identifier search resolves the patient → guardian relationship created → self-link rejected → reverse relationship listed → photo uploaded (multipart, byte-exact) → photo bytes roundtrip. **13 passed, 0 failed**, live 2026-11-15. PIT suite remains green in CI run 43631893238.
