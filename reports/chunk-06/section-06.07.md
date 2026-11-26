# Section 06.07 — Interop outbound FHIR (chunk 6)

**Verdict: SECTION REVIEW → local gates green; outbound half built against a real HAPI server. ABDM remains an external dependency.** Patient/Encounter/Observation/DiagnosticReport/MedicationRequest are pushed to a peer FHIR server, profile-checked before send, consent-gated for documents, and recorded per resource.

## 1. Scope
Per `chunk-06-enterprise-scale.md` item 7 / `19-CHUNK6-SECTIONS.md` 06.07: HAPI FHIR server (Patient, Encounter, Observation, DiagnosticReport, MedicationRequest profiles, India EHR-M aligned), hospital-to-hospital + ABDM data flows, document access semantics, profiles validated in CI.

Baseline (per the 2026-11-14 gate run) was read-only: `FhirService` + GET-only `/api/v1/fhir/*`. This section adds the outbound half.

## 2. Key artifacts
**Infrastructure**: `hapiproject/hapi:v7.2.0` as `squamed-hapi` (port 8081, `JAVA_TOOL_OPTIONS=-Xms256m -Xmx768m`, `restart: unless-stopped`). The image is distroless — no shell, no curl — so it has no container healthcheck; the integration test waits on `/fhir/metadata`.

**Migration `V107`**: `interop.fhir_export` (hospital, patient, target, resource type/id, status, http status, detail, exported_at) + permission `integration:export:fhir` granted to SYSTEM_ADMIN and HOSPITAL_ADMIN.

**Backend**
- `interop/fhir/FhirProfileValidator.kt` — mandatory-element rules for the five resources, FHIR status value sets, and a date predicate that rejects the literal string `"null"`.
- `interop/fhir/FhirOutboundService.kt` — per-patient export, null-pruned FHIR JSON, `PUT <peer>/<Type>/<id>`, per-resource ledger row, consent gate for DiagnosticReport, unreachable peer recorded as FAILED.
- `interop/domain/FhirExportEntities.kt`, `interop/repository/FhirExportRepository.kt`.
- `interop/api/FhirOutboundController.kt` — `POST /api/v1/fhir/export/{patientId}?target=`, `GET /api/v1/fhir/exports`.

## 3. Gap closure
- ✔ HAPI server is real and runs in the dev/CI compose stack.
- ✔ Five outbound profiles are validated before every send; a rejected resource is recorded with the peer's OperationOutcome excerpt, never silently dropped.
- ✔ Document access semantics: `DiagnosticReport` is exported only with a granted `data_sharing` consent; otherwise it is recorded `WITHHELD` with `CONSENT_REQUIRED`, and the peer verifiably does not hold it.
- ✔ Hospital-to-hospital: the target base URL is per-call/configured, so a peer hospital's FHIR server is the receiver. Demonstrated end-to-end against a live server.
- ✖ ABDM data flows: no sandbox credentials/consent-artefact exchange in this environment.
- ⚠ EHR-M alignment is enforced as our local mandatory-element rules plus HAPI's own validation; no official StructureDefinition artefacts exist in the repo to validate against.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS (partial) | `fhirOutboundCoverage` green; ABDM absent |
| 2 Zero-stub | PASS | new files grep clean; CI |
| 3 Types | PASS | `compileKotlin` + `compileIntegrationTestKotlin` |
| 4 Lint + format | PASS | `ktlintCheck` clean |
| 5 Tests | PASS (local) | full chain BUILD SUCCESSFUL 13m25s - 111 PIT methods + 56 unit tests, 0 failures; JaCoCo instruction 84.98% (>=0.80) |
| 6 Accessibility | PASS | No UI change |
| 7 Design system | PASS | No UI change |
| 8 Contracts | PASS | OpenAPI snapshot regenerated (sha256 `f746f2314feb6c848b6aea79d6b4695aef7d943836337982a48641d2f579a650`); api-client codegen stamped; ContractTests green |
| 9 Data | PASS | V107 additive; cross-tenant export ledger empty |
| 10 Audit | PASS | every outbound resource has a ledger row with actor, target and outcome |
| 11 Docs | PASS | This report; LEDGER row; chunk-06 tracker; `reports/pr-body-06.07.md` |
| 12 Exit item | PASS (partial) | 06.09 exit slice: outbound interop |

## 5. Tests
Full suite: `integrationTest test jacocoTestReport jacocoTestCoverageVerification` - BUILD SUCCESSFUL, 111 PIT methods + 56 unit tests, 0 failures, JaCoCo instruction 84.98%.

Named PIT **`fhirOutboundCoverage`** (real HAPI on :8081):
- Empty `Patient` violates identifier/name/gender/birthDate before anything is sent.
- Export → 4 core resources `EXPORTED`; the peer returns 200 for the Patient, Encounter, Observation and MedicationRequest by id.
- Lab report: withheld with `CONSENT_REQUIRED` and the peer returns 404; after a granted `data_sharing` consent the report exports and the peer returns 200.
- Dead peer (`127.0.0.1:9`) → 5 resources `FAILED`, nothing `EXPORTED`; history keeps both the successful and the failed attempts.
- Cross-tenant: the second hospital's export ledger is empty.

## 6. Accessibility
No UI change.

## 7. Dual review — adversarial pass
1. **Consent gate** — documents are withheld, not silently omitted; the reason is machine-readable and the peer is verified to lack the resource.
2. **No silent drop** — every resource ends in exactly one state (`EXPORTED`, `FAILED`, `WITHHELD`) with an auditable row.
3. **Profile honesty** — validation runs locally *and* HAPI re-validates; a peer rejection is stored with its OperationOutcome, so a profile gap cannot masquerade as success.
4. **Tenant** — the patient must belong to the caller's hospital; the ledger is hospital-scoped.
5. **Wire correctness** — null pruning plus the status mapping fixed two real interoperability bugs (see §8).
6. **Failure isolation** — one unreachable resource does not abort the run; the rest still export.
7. **Audit** — actor, target base, HTTP status and detail are persisted per row.

## 8. Defects found and fixed at the root
- `MedicationRequest.status` leaked the internal `prescribed` state; HAPI rejects it (`Unknown MedicationRequestStatus code`). Now mapped to FHIR `active/completed/stopped/cancelled/draft/unknown`.
- `?.atOffset(ZoneOffset.UTC).toString()` produced the **string** `"null"` for missing instants, which HAPI rejects as an invalid date (`issued`). Fixed for Observation, MedicationRequest, ServiceRequest and DiagnosticReport; the validator now rejects `"null"` dates too.
- Outbound JSON is null-pruned at the wire boundary (FHIR JSON has no null values).

## 9. Deviations / blockers
- **ABDM** gateway/consent-artefact exchange is external (sandbox credentials); `POST /abdm/*` is not implemented.
- **EHR-M**: no official profile artefacts in-repo, so alignment is enforced by local rules + HAPI validation rather than StructureDefinition conformance.
- **Two-hospital topology** is configuration (peer base URL), not a second deployed HAPI instance in CI.
- 06.06 landed separately; 06.09 chunk exit still owes the 1,500-user load and datacenter-kill drill.

## 10. Session trail
- Named PIT green after fixing the two wire bugs it surfaced; full-suite verification follows.
