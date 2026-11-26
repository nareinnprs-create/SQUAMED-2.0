## 06.07 — Interop outbound FHIR

Outbound half of interop: governed records pushed to a real HAPI FHIR server, profile-checked before send, documents consent-gated, every resource auditable.

### Infrastructure
- `hapiproject/hapi:v7.2.0` as `squamed-hapi` on :8081 (`JAVA_TOOL_OPTIONS` 768m, `restart: unless-stopped`). Distroless image → no container healthcheck; the test waits on `/fhir/metadata`.

### Schema (`V107`, additive)
- `interop.fhir_export` ledger + `integration:export:fhir` (SYSTEM_ADMIN, HOSPITAL_ADMIN).

### Backend (`/api/v1/fhir`)
- **export/{patientId}?target=** — Patient, Encounter, Observation, MedicationRequest, DiagnosticReport pushed by id (`PUT`), null-pruned FHIR JSON, per-resource ledger row with HTTP status and peer OperationOutcome excerpt.
- **exports** — hospital-scoped ledger.
- **Profile validation** — mandatory elements, FHIR status value sets, ISO date predicate; runs before every send, and HAPI re-validates.
- **Document access semantics** — `DiagnosticReport` exported only with a granted `data_sharing` consent; otherwise `WITHHELD` + `CONSENT_REQUIRED`, peer verified to lack it.

### Evidence
- New PIT `fhirOutboundCoverage`: 4 core resources exported and readable back from HAPI by id; empty Patient fails 4 profile rules; report withheld (peer 404) then exported after consent (peer 200); dead peer → 5 FAILED recorded; history keeps EXPORTED + FAILED; cross-tenant ledger empty.
- Full local chain: 111 PIT methods + 56 unit tests, 0 failures; JaCoCo instruction 84.98% (≥ 0.80).

### Two real interoperability bugs found and fixed at the root
- `MedicationRequest.status` emitted the internal `prescribed` state — HAPI rejects it; now mapped to FHIR status codes.
- `?.atOffset(...).toString()` emitted the string `"null"` for missing instants — HAPI rejects it as an invalid date; fixed for Observation, MedicationRequest, ServiceRequest, DiagnosticReport, and guarded in the validator.

### Deviations
- ABDM gateway/consent-artefact exchange needs sandbox credentials → not implemented.
- EHR-M alignment is local mandatory-element rules + HAPI validation; no official StructureDefinition artefacts exist in-repo.
- Hospital-to-hospital is a configured peer base URL; CI runs one HAPI instance.
