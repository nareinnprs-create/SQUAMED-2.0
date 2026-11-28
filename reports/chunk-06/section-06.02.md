# Section 06.02 - Telemedicine (chunk 6)

**Verdict: SECTION REVIEW — the Telemedicine Guidelines 2020 rules the platform can enforce are built and green; the video SDK (`exp:E9`) and the payment link (`exp:E4`) remain external.**

## 1. Scope

Per `19-CHUNK6-SECTIONS.md` 06.02: *"WebRTC via provider SDK, Telemedicine Guidelines 2020 checklist encoded (RMP verified, consult-type rules - non-compliant BLOCK), payment link, documentation into standard encounter, tech-support escape path."*

Three of those five are platform rules; two are vendor integrations. The rules are built, the integrations are named and not faked.

## 2. Key artifacts

**Migration `V114`**: `telemedicine.consult` (patient, doctor user, RMP employee, consult type, mode, status, times, encounter link, meeting reference, escape reason), plus `telemedicine:read:consult` / `telemedicine:manage:consult` granted to `SYSTEM_ADMIN`, `HOSPITAL_ADMIN`, `DOCTOR`. Additive only.

**Backend**
- `telemedicine/domain/TelemedicineConsult.kt`, `repository/TelemedicineConsultRepository.kt`.
- `telemedicine/service/TelemedicineService.kt` — schedule, start, complete, escape, cancel and the guideline checks.
- `telemedicine/api/TelemedicineController.kt` — 7 endpoints under `/api/v1/telemedicine/consults`.
- `EncounterService.startEncounter` gained `encounterType` (default `outpatient`), so a tele-consult documents into a **real encounter** typed `telemedicine` rather than a parallel record.

## 3. Gap closure

- **RMP verified** — the RMP is an employee record, not a free-text name. The consult cannot be scheduled unless that employee belongs to the hospital, is active, has a registration number, and its licence is not expired. Each failure has its own code (`TELEMEDICINE_RMP_NOT_FOUND`, `_INACTIVE`, `_UNVERIFIED`, `_LICENCE_EXPIRED`).
- **Consult-type rules, blocking** — `FIRST_CONSULT` must be `VIDEO` (`TELEMEDICINE_FIRST_CONSULT_REQUIRES_VIDEO`); `AUDIO` and `CHAT` are follow-up modes only.
- **Documentation into the standard encounter** — `start` creates an encounter with type `telemedicine`, owned by the consulting doctor, so notes, diagnoses and prescriptions land in the same clinical record as an in-person visit.
- **Tech-support escape path** — `escape` closes a scheduled or in-progress consult as `SUPPORT_ESCAPE` with a required reason, instead of leaving it open or silently cancelling it.
- **State machine** — `SCHEDULED → IN_PROGRESS → COMPLETED`, with `CANCELLED` from scheduled only and `SUPPORT_ESCAPE` from either open state. Illegal transitions are rejected by code, not by convention.

## 4. Gates 1-12

| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS (partial) | `telemedicineGuidelineCoverage` green; video SDK + payment link owed |
| 2 Zero-stub | PASS | no stubs |
| 3 Types | PASS | `compileKotlin` + `compileIntegrationTestKotlin` |
| 4 Lint + format | PASS | `ktlintCheck` clean |
| 5 Tests | PASS (local) | `integrationTest` BUILD SUCCESSFUL 20m57s - 119 PIT, 0 failures; `test jacocoTestReport jacocoTestCoverageVerification` BUILD SUCCESSFUL - 57 unit, 0 failures; JaCoCo instruction 85.03% (>=0.80) |
| 6 Accessibility | PASS | no UI change |
| 7 Design system | PASS | no UI change |
| 8 Contracts | PASS | OpenAPI snapshot regenerated from live `/api-docs`; api-client stamped `33a0157a…`; ContractTests green |
| 9 Data | PASS | `V114` additive; cross-tenant consult read denied |
| 10 Audit | PASS | controller-mediated writes; consult carries the actor |
| 11 Docs | PASS | this report; LEDGER row; chunk-06 FINAL |
| 12 Exit item | PASS (partial) | chunk-6 exit scenario's "teleconsult completed with guideline-compliant prescription" slice |

## 5. Tests

Named PIT **`telemedicineGuidelineCoverage`** (full chain: 119 PIT + 57 unit, 0 failures; JaCoCo 85.03%):

1. Employee with no registration number → `400 TELEMEDICINE_RMP_UNVERIFIED`.
2. `FIRST_CONSULT` + `AUDIO` → `400 TELEMEDICINE_FIRST_CONSULT_REQUIRES_VIDEO`.
3. Compliant first consult → scheduled, started, **encounter created with `encounterType = telemedicine`**, completed.
4. Follow-up + `AUDIO` accepted; tech-support escape records `SUPPORT_ESCAPE` and keeps the reason.
5. Hospital B cannot read hospital A's consult.

## 6. What is still owed

- **Video transport** (`exp:E9`) — WebRTC via a provider SDK with India latency and DPDP terms. A consult carries `meetingRef` for whatever transport is wired in; no fake room is generated.
- **Payment link** (`exp:E4`) — the PSP contract; the consult records no payment state, because inventing one would imply money moved.
- **Identity binding** — there is no user↔employee link in the platform, so the RMP is verified as a **record** (belongs to the hospital, licensed, active), not as *the caller*. A doctor with `telemedicine:manage:consult` can name any licensed colleague. This is a real gap, recorded here rather than papered over; closing it needs a user↔employee association in master data.

## 7. Deviations

- **No Schedule X / restricted-drug check at the telemedicine layer.** Prescribing already goes through the pharmacy and medication-safety paths; duplicating a drug list here would create a second, drifting source of truth. The guideline's prescription restrictions belong with the prescription rules, and are noted as a follow-up rather than half-built.
- **`encounterType` was added to `startEncounter`** rather than writing the encounter directly from the telemedicine service, so the audit, event and ownership behaviour stays in one place.
