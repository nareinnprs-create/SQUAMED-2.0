## 07.05 — ED mass-casualty drill harness

Closes one of 07.11's three owed items. The drill runs the real ED paths and keeps the timings as evidence.

### Schema (`V109`, additive)
- `ops.ed_drill`: hospital, name, planned/created casualties, triage distribution, status, declared/stood-down timestamps, elapsed ms.

### Backend (`/api/v1/ops/ed/drills`)
- **POST /** — `{name, casualties:[{patientId, triageLevel, chiefComplaint}]}`. Validates every casualty (level 1–5, complaint present) **before** anything is declared, declares the mass-casualty event, registers each casualty through `EdService.create`, stands down, and records the run with timings and triage distribution.
- **GET /** — hospital-scoped drill history.

### Two properties that matter more than the happy path
- A rejected drill (bad triage level) declares nothing and records nothing — asserted.
- The stand-down runs in a `finally`, so a casualty failing mid-drill can never leave the ED stuck in "mass casualty active"; the run is then recorded as `FAILED` with the reason.

### Evidence
- New PIT `edMassCasualtyDrillCoverage`: 3 casualties (levels 2/3/4) → `COMPLETED`, 3 registered, distribution 1/1/1, stood down, timing recorded; no active mass casualty afterwards; a level-6 drill is rejected 400 with nothing declared or recorded; a second hospital sees an empty drill history.

### Still owed in 07.05
- Resus protocol/checklist engine and the casualty tracking board.
- The drill exercises registration and triage; bay assignment and treatment timings are not scripted yet.
