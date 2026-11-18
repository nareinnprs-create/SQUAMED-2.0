# Pilot shadow-run checklist (01.11)

A shadow run is a parallel-run week at the pilot hospital: staff keep the existing
paper/legacy OPD flow AND double-enter into Squamed. Nothing patient-facing switches over.
Purpose: prove the numbers (OPD counts, wait times, no-show rate) and the flow before cutover.

## Before the shadow week

- [ ] Pilot hospital exists with its own tenant (`POST /api/v1/hospital-config/hospitals`),
      at least one doctor schedule template, and named staff accounts (reception, doctor, admin).
- [ ] Kiosk/tablet reachable on the hospital LAN (01.08 kiosk-lite check-in works end to end).
- [ ] TV display board (01.06) shows the live queue on the ward screen.
- [ ] Emergency fast-path + downtime pack rehearsed (01.09): staff know the paper fallback and
      the recovery path (`POST /api/v1/downtime/*` batch flow).
- [ ] Baseline metrics captured the Friday before: read
      `GET /api/v1/analytics/{overview,appointments,queue}` and save the JSON next to the
      pilot log (this is the comparison point for day 1).
- [ ] Legacy-side owner named: someone on the paper flow records, per clinic day, the same
      three numbers the dashboard shows (patients booked, patients seen, no-shows).

## Daily during the shadow week (each clinic day, ~15 min)

- [ ] Both systems ran the full day; no Squamed-only or paper-only patients. If a patient
      was processed on paper only, back-enter them the same day so counts stay comparable.
- [ ] `GET /api/v1/analytics/appointments` — OPD counts by status match the paper tally
      (booked / confirmed / arrived / completed / no-show buckets in `byStatus`).
- [ ] `GET /api/v1/analytics/queue` — avg wait minutes is populated (not null) and within
      +/- 10 min of the stopwatch sample the receptionist took at the desk.
- [ ] `noShowRate` on the appointments read is plausible vs the paper rate
      (no-show / (completed + no-show) over terminal outcomes; null means nothing resolved yet).
- [ ] Zero unresolved exceptions in reception: no stuck `booked` appointments that actually
      arrived, no queue entries waiting with nobody behind the counter.
- [ ] Log the day's three numbers (Squamed vs paper) in the pilot log with a yes/no match.

## Exit criteria (end of the shadow week)

- [ ] 5 consecutive clinic days with OPD count parity (Squamed total = paper total per day).
- [ ] Wait-time sample within tolerance on at least 4 of 5 days.
- [ ] No-show rate within +/- 2 percentage points of the paper rate.
- [ ] Zero critical defects open; P2/P3 defects ticketed with owners.
- [ ] Downtime drill evidence filed (01.09 drill script output) and cutover decision signed
      by the pilot hospital's OPD lead.

## Rollback / abort

Shadow mode has no patient-facing blast radius: stop double-entering and the paper flow is
unchanged. If the dashboard misleads (numbers clearly wrong), freeze entries, capture
`GET /api/v1/analytics/*` output for the defect report, and raise a critical ticket —
do not hand-edit historical rows to force parity.

## Automated coverage (what CI already proves)

- `analyticsShadowRunCoverage` PIT journey: fresh hospital, books two appointments, completes
  one + no-shows one, asserts `noShowRate == 50.0` exactly, queue/overview avg wait non-null
  after check-ins, and a foreign hospital reading zeros/null (tenant scoping).
- Dashboard Appointments panel surfaces the no-show badge (`no-show 50%` / `no-show —`).
- `scripts/demo-01.11-analytics-shadow-run.ps1` — scripted slice of the above against :8080.
