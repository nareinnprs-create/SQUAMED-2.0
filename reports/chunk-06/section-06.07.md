# Section 06.07 — Interop outbound FHIR — gate-run report

Static read-only assessment. Baseline commit `886c51c`, CI run `43548016617` (all 6 CI gates green; merged instruction coverage 80.13%). No code, ledger, chunk file, or `.gate/` content changed by this report.

## 1. Scope
Chunk file §06.07: HAPI FHIR server (Patient/Encounter/Observation/DiagnosticReport/MedicationRequest profiles, India EHR-M aligned), hospital-to-hospital + ABDM data flows, document access semantics. Depends on 06.01. Present at baseline: read-only FHIR resources + HL7 v2 inbound. **HAPI server, profiles, FHIR writes/search, ABDM, hospital-to-hospital flows absent.**

## 2. Built
- `backend/squamed-core/src/main/kotlin/com/squamed/core/interop/fhir/FhirService.kt` — 10 read-only FHIR resources.
- `backend/squamed-core/src/main/kotlin/com/squamed/core/interop/api/InteropControllers.kt` — GET-only `/api/v1/fhir/*` with per-resource permissions.
- `V24__interop.sql` — `interop.integration_connection`, `hl7_message`.
- `apps/web` integrations page (945 LOC).

## 3. Data/API/event contracts delivered
- Tables: integration_connection, hl7_message.
- API: 10 FHIR read resources (GET-only, per-resource perms); HL7 ADT parse/import (accept/ack AA); MLLP health check.
- Events: no outbound FHIR write events; no ABDM flow.

## 4. Gates 1–12
| # | Gate | Result | Evidence |
|---|---|---|---|
| 1 | Functional completeness | **FAIL (PARTIAL)** | Present: 10 read-only FHIR resources with per-resource permissions, FHIR Bundle (Patient/Encounter/DiagnosticReport) in journey, HL7 ADT parse/import AA, MLLP health. Missing: HAPI FHIR server, India EHR-M profiles, FHIR writes/search (section is outbound by name — only read built), ABDM data flows, hospital-to-hospital document access semantics (06.01 dependency unbuilt). |
| 2 | Zero-stub scan | PASS | CI run 43548016617 (gate 2 green); SC-9 allowlist. |
| 3 | Types | PASS | CI run 43548016617 (gate 3 green). |
| 4 | Lint + format | PASS | CI run 43548016617 (gate 4 green). |
| 5 | Tests | PASS | CI run 43548016617 (gate 5 green); merged instruction coverage 80.13% ≥ 0.80. |
| 6 | Accessibility | PASS | CI run 43548016617 (gate 6 green). Caveat: integrations page (945 LOC) **not** in axe `SCAN_ROUTES`. |
| 7 | Design system | PASS | CI run 43548016617 (gate 7 green). |
| 8 | Contracts | **FAIL** | Schema-registry harness not yet wired (00.02 scope); FHIR profiles not validated in CI (chunk file: "Profiles validated in CI"). |
| 9 | Data | **FAIL** | RLS cross-tenant suite ABSENT until 00.04 (ADR-13) — tenant-scoped interop tables. |
| 10 | Audit | PASS | Read-only GET endpoints (no write paths); `AuditAspect.kt:22-23` still wired over interop controllers. |
| 11 | Docs | PASS | This report re-checks chunk file §06.07; ledger untouched; gate-parity green. |
| 12 | Exit item | PASS | Named PIT slices `aiGatewayAndInteropJourney` (FHIR Bundle + HL7 + MLLP, :889-1038) + `ContractTests` (:1276-1332), green in run 43548016617. |

## 5. Tests
- PIT: `aiGatewayAndInteropJourney` — FHIR Bundle Patient/Encounter/DiagnosticReport, HL7 ADT parse/import AA, MLLP health; `ContractTests` :1276-1332. Green in run 43548016617.
- Unit: `Hl7MessagesTest` (7 tests). Green in run 43548016617.
- Coverage: merged instruction 80.13%.

## 6. Accessibility
axe green in CI; integrations page outside `SCAN_ROUTES` — not page-axe-verified.

## 7. Issues
- No outbound write path at all (section name = outbound FHIR; baseline is read-only) — **open**.
- No HAPI server / profile validation in CI — open (chunk-file CI requirement unmet).
- Hospital-to-hospital + ABDM flows blocked by unbuilt 06.01 — open.

## 8. External blockers
⛔ ABDM/India EHR-M spec alignment and HAPI server infrastructure are external dependencies for the outbound half.

## 9. Deviations from chunk file
None logged; chunk file not edited.

## 10. Exit-demo slice
`gradlew.bat :squamed-core:integrationTest --tests "*PlatformIntegrationTests*aiGatewayAndInteropJourney*" --tests "*PlatformIntegrationTests*ContractTests*"` + `gradlew.bat :squamed-core:test --tests "*Hl7MessagesTest*"` — last run green in CI run 43548016617.
