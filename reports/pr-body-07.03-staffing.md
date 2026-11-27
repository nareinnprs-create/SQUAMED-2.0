## 07.03 — staffing + demand forecast in the capacity answer (closes 07.03)

07.03's last open gap: the capacity model now carries staffing coverage and a demand forecast, and the answer explains both.

### Schema (`V110`, additive)
- `twin.capacity_snapshot.staff_on_duty` and `forecast_admissions_next_24h`.

### What changed
- **Staff on duty** — today's roster shifts whose code covers the current window (the HR roster stores a code, not times: EARLY 07–15, LATE 15–23, NIGHT 23–07).
- **Demand forecast** — 7-day admission run-rate, deliberately a flat mean (same honesty as the finance forecast, which is a flat 3-month run-rate).
- **Capacity answer now has five evidence factors**: beds, diagnostics backlog, equipment down, staffing (patients per on-duty clinician ≤ 6) and demand forecast.

### Two design decisions worth stating
The forecast is **advisory, not blocking**. It is reported with `blocking=false` and `canAccept` only requires the blocking factors to pass — a projection should not by itself refuse a patient. The PIT asserts exactly that: with the only bed occupied, `canAccept` is false and the *beds* factor is named as the blocker, while the forecast factor is present and non-blocking.

Staffing is blocking **only when a roster exists for today**. A hospital with no roster configured would otherwise refuse every admission, which is missing data being treated as an empty ward. This surfaced as a real regression in `digitalTwinCapacityCoverage` (no roster → `canAccept` flipped to false); the rule now reports the factor as advisory when nothing is rostered, and blocking when it is.

### Evidence
- New PIT `digitalTwinStaffingForecastCoverage`: admit one patient, roster a shift covering the current window → snapshot `staffOnDuty=1` and a non-zero run-rate; capacity returns five factors including staffing and demandForecast; staffing passes at 1 patient per clinician; `+1` admission refused on beds; `+0` accepted.
- Full chain: 115 PIT + 57 unit, 0 failures.

### Deviations (recorded, not hidden)
- Forecast is a flat 7-day mean; no seasonality, no day-of-week profile, no OT/elective-case demand.
- Staffing counts rostered shifts, not attendance; leave is not netted off.
- No per-ward or per-specialty capacity model.
