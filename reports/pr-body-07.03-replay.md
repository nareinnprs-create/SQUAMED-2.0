## 07.03 — event replay for the digital twin (+ the redaction bug its CI failure exposed)

Closes the second of 07.03's two real gaps: the "30-day replay that reconciles with actuals" is now an actual event replay, not just snapshot-vs-live.

### What the replay does
`GET /api/v1/twin/replay?days=30` reads the hospital's stored event stream for the window (`events.event_outbox`, ordered by occurrence), rebuilds admitted-patient and bed-occupancy state by applying:

- `admission.admitted` / `admission.bed_assigned` → patient occupies that bed
- `admission.transferred` → patient moves to `toBedId`
- `admission.discharged` → patient leaves

then compares the replayed end state with live counts and returns per-day event buckets, `replayed` vs `live`, `mismatches`, and `reconciled`. Window is bounded 1–90 days; out-of-range is rejected rather than clamped.

### Payload gap it forced
`admission.transferred` and the follow-on `admission.bed_assigned` carried no `patientId`, so a replayed transfer could not be attributed to a patient. Both now include it — additive, no envelope change.

### The defect this PR's CI failure actually found
`aiCommandBarGroundingCoverage` failed twice on `POST /api/v1/ai/command` with `AI_ACTION_ADMISSION_REQUIRED`, while the admission id asserted as a valid UUID immediately before. Cause: `PromptRedactor`'s phone rule (`\d{8}` not adjacent to another digit) matches a UUID's first group whenever that 8-hex-character group happens to be all digits — about 2% of ids. The redactor replaced it with `[PHONE]`, so the command bar received `prepare discharge [PHONE]` and correctly reported a missing admission.

Fix: UUIDs are masked with a digit-free sentinel before the PHI rules run and restored afterwards, so identifiers survive redaction while email/MRN/QID/phone are still masked. Unit test covers exactly that case (an all-digit first group plus real MRN and phone in the same prompt).

This was not a flaky test; it was a ~2% silent corruption of any identifier passed through the AI prompt path.

### Evidence
- New PIT `digitalTwinReplayCoverage`: admit → full discharge path (summary, med-rec, sign, checklist legs) → admit a second patient; replay ends at `replayed.admissionsActive=1`, `bedsOccupied=1`, matches live, `reconciled=true`, per-day buckets present; `days=0` rejected 400; a second hospital's replay applies none of the first hospital's admission events.
- New unit test: uuid survives redaction with an all-digit first group; MRN and phone in the same prompt are still redacted.
- Full chain: 114 PIT + 57 unit, 0 failures, JaCoCo 85.01%.

### Also in this PR
`backend/squamed-core/gradle.properties` caps Gradle and Kotlin daemons at 1.5 GB (were 2 GB), paired with a 7 GB Docker VM — the combination that stopped the Docker engine from being killed mid-suite on a 15.3 GB host.

### Still owed in 07.03
- Staffing (roster) and demand forecasting are not part of the capacity model.
