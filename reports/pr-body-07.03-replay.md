## 07.03 — event replay for the digital twin

Closes the second of 07.03's two real gaps: the "30-day replay that reconciles with actuals" is now an actual event replay, not just snapshot-vs-live.

### What it does
`GET /api/v1/twin/replay?days=30` reads the hospital's stored event stream for the window (`events.event_outbox`, ordered by occurrence), rebuilds admitted-patient and bed-occupancy state by applying:

- `admission.admitted` / `admission.bed_assigned` → patient occupies that bed
- `admission.transferred` → patient moves to `toBedId`
- `admission.discharged` → patient leaves

then compares the replayed end state with live counts and returns per-day event buckets, `replayed` vs `live`, `mismatches`, and `reconciled`.

### One real fix it forced
`admission.transferred` and the follow-on `admission.bed_assigned` carried no `patientId`, so a replayed transfer could not be attributed to a patient. Both payloads now include `patientId` — additive, no envelope change.

### Evidence
- New PIT `digitalTwinReplayCoverage`: admit → full discharge path (summary, med-rec, sign, checklist legs) → admit a second patient; replay ends at `replayed.admissionsActive=1`, `bedsOccupied=1`, matches live, `reconciled=true`, per-day buckets present; `days=0` rejected 400; a second hospital's replay applies none of the first hospital's admission events.
- Window is bounded 1–90 days; out-of-range is rejected rather than silently clamped.

### Still owed in 07.03
- Staffing (roster) and demand forecasting are not part of the capacity model.
