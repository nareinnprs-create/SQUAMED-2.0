## What this is

06.02 was gated on `exp:E9` (video SDK). Three of its five deliverables are platform rules, not vendor work — the Telemedicine Guidelines 2020 checklist, documentation into the standard encounter, and the tech-support escape path. Those are built; the section lands at **SECTION REVIEW** with the video SDK and payment link named.

## What is built

| Deliverable | How |
|---|---|
| RMP verified | The RMP is an **employee record**, not a free-text name: the consult cannot be scheduled unless that employee belongs to the hospital, is active, has a registration number and an unexpired licence (`TELEMEDICINE_RMP_NOT_FOUND` / `_INACTIVE` / `_UNVERIFIED` / `_LICENCE_EXPIRED`) |
| Consult-type rules, blocking | `FIRST_CONSULT` must be `VIDEO` (`TELEMEDICINE_FIRST_CONSULT_REQUIRES_VIDEO`); `AUDIO`/`CHAT` are follow-up modes only |
| Documentation into the standard encounter | `start` creates an encounter with `encounterType = telemedicine` owned by the consulting doctor, so notes/diagnoses/prescriptions land in the same clinical record |
| Tech-support escape path | `escape` closes a scheduled or in-progress consult as `SUPPORT_ESCAPE` with a required reason |
| State machine | `SCHEDULED → IN_PROGRESS → COMPLETED`, `CANCELLED` from scheduled only, `SUPPORT_ESCAPE` from either open state |

`EncounterService.startEncounter` gained an `encounterType` parameter (default `outpatient`) rather than letting the telemedicine service write encounters directly — ownership, audit and events stay in one place.

## Evidence

- `integrationTest`: **BUILD SUCCESSFUL 20m57s**, **119 PIT, 0 failures**.
- `test jacocoTestReport jacocoTestCoverageVerification`: BUILD SUCCESSFUL, **57 unit, 0 failures**, JaCoCo instruction **85.03%** (≥ 0.80).
- PIT `telemedicineGuidelineCoverage`: unlicensed RMP blocked; first consult as audio blocked; compliant first consult scheduled → started → encounter typed `telemedicine` → completed; follow-up as audio accepted and escaped with a reason; hospital B cannot read hospital A's consult.
- OpenAPI regenerated from live `/api-docs`; api-client stamped `33a0157a…`.

## Two limits recorded, not hidden

1. **The RMP is verified as a record, not as the caller.** There is no user↔employee link in the platform, so a user with `telemedicine:manage:consult` can name any licensed colleague. Closing that needs a user↔employee association in master data; inventing one here would have meant a second identity model.
2. **No Schedule X / restricted-drug check at this layer.** Prescription restrictions already live in the pharmacy and medication-safety paths; a second drug list here would drift from the first. Noted as a follow-up.

## What this does not claim

- **No video transport.** A consult carries `meetingRef` for whatever SDK is wired in; no fake room is generated.
- **No payment state.** `exp:E4` is the PSP contract; recording a payment status without one would imply money moved.

Ledger: 06.02 PLANNED → SECTION REVIEW. Totals 66 COMPLETE / 1 IN PROGRESS / 13 SECTION REVIEW / 10 PLANNED.
