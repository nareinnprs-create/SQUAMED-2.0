# INTEROP SPEC — ABDM · HL7 v2 · FHIR · DICOM · Terminology
**Principle (ADR-7):** HAPI FHIR, Mirth Connect, Orthanc are bought components; Squamed writes profiles, channels, and mappings — never the standards engines.

## 1. ABDM (India)
| Flow | Design |
|---|---|
| ABHA creation/verification | Registration screen → ABDM sandbox APIs (OTP/email demo modes); ABHA number + token stored encrypted (field-level, §07.3); demographic reconciliation on fetch |
| Facility linkage | Hospital registered in HFR; HIP id per facility stored in config (per-tenant, never platform-global) |
| Consent artefacts | Patient-scoped data-share requests → ABDM consent artefact stored in `abdm_consent_artefact`, enforced by cross-hospital share gate (chunk 6 test) |
| Data push | FHIR bundles (patient-authored share) via HIP data-push API; every push audited with artefact ref |
| Linking care context | Encounter/report events → link care contexts (OP consult, diagnostic report) per ABDM token validity |

**Config surface:** sandbox→production switch is tenant config; sandbox pinned versions (risk register #7).

## 2. HL7 v2 (via Mirth Connect)
| Channel | Direction | Messages | Handling |
|---|---|---|---|
| Lab analyzers | inbound | ORU^R01 (ASTM bridged) | → `analyzer_message_log` → result queue; malformed → quarantine + alert (zero silent drops) |
| Lab orders out | outbound | OML^O21 | To middleware/LIS partners when hospital has one |
| ADT to device gateways | outbound | ADT^A08 (admit/transfer/discharge) | Keeps monitor mappings current |
| Pharmacy/ERP partners | outbound | RDS/RDE variants | Per-partner config document |
| ACK policy | both | MSH-15/16 | Accept = parsed+validated; errors → integration error queue with alert; every message audited with correlation id to clinical event |

Channel configs are version-controlled artifacts deployed by pipeline — no dashboard-only changes in prod.

## 3. FHIR (R4 via HAPI, from chunk 6)
Profiles (aligned to India EHR-M): `Patient` (ABHA identifier), `Encounter`, `Observation` (vitals, LOINC), `DiagnosticReport` (+`DiagnosticReport/Lab`), `MedicationRequest`, `AllergyIntolerance`, `Consent` (ABDM artefact mapping), `DocumentReference` (reports).
Rules: profiles live in `packages/schemas/fhir`, validated in CI with example fixtures; outbound only v1 (inbound FHIR write path opens with ecosystem, chunk 8, behind certification).

## 4. DICOM
Orthanc = storage + MWL provider; modality pulls worklist (DICOM MWL) fed from `rad_order`; completed studies auto-route to `study` records + Patient 360; OHIF viewer proxied with scoped tokens (study-level ABAC); external study import via CD/staged upload with patient-match confirmation screen (two-identifier).

## 5. Terminology service
Internal service (chunk 1): ICD-10, SNOMED CT (NRCeS license), LOINC loaded + search + mapping tables (local code ↔ standard); every clinical entry validated to carry ≥1 standard code (platform rule, master plan §3); quarterly terminology release update job.
