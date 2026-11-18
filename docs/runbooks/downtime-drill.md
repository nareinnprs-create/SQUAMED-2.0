# Downtime drill protocol (CS-09, chunk-1 exit)

A downtime drill is a 30-minute rehearsal of the paper fallback: the OPD front desk
works a simulated outage on paper, then re-enters everything through the batch
re-entry flow. The chunk-1 contract puts one drill in exit week; the pilot hospital
repeats it quarterly thereafter. This is a HUMAN drill - staff walk the steps with
real paper; the scripted demo (`scripts/demo-01.09-emergency-fastpath-downtime.ps1`)
is the tooling rehearsal that proves the API path the drill exercises.

## Roles

- **Drill lead** (reception supervisor): opens/closes the downtime window, runs re-entry.
- **Desk staff** (2): run the paper flow - token pad, visit register, receipts book.
- **Observer**: keeps the stopwatch, records the timeline and the mismatch count.

## Prerequisites

- [ ] Pilot (or drill) hospital tenant exists with named staff accounts (reception role).
- [ ] Printed paper pack ready: blank token pad, visit register sheet, receipt book,
      and the printed one-page fallback instructions (this runbook, printed).
- [ ] `scripts/demo-01.09-emergency-fastpath-downtime.ps1` ran green on :8080 within
      the last 30 days (31/31) - proves the API side the drill relies on.

## Drill steps (30 min)

1. **Open the window (min 0).** Drill lead calls
   `POST /api/v1/downtime/windows {"reason": "drill: <date>"}` and writes the returned
   window id on the register sheet. Wall clock starts here.
2. **Paper OPD flow (min 0-20).** Desk staff register and process 5 walk-in patients
   purely on paper: hand out consecutive paper tokens (C-01..C-05 style), record name,
   identifier (MRN if known, otherwise name + phone), reason, arrival time, fee taken
   (cash book). No Squamed screens for these patients.
3. **Recovery + batch re-entry (min 20-25).** Drill lead closes the window
   (`PATCH /api/v1/downtime/windows/{id}/close`), then re-enters the paper cohort in
   ONE batch: `POST /api/v1/downtime/reentries` with the token list (queueType,
   tokenNumber, patientId, patientName, priority) and any visit ids to re-stamp.
   Paper tokens must return with their original numbers.
4. **Verification (min 25-30).** Confirm: every paper token appears on
   `GET /api/v1/queue/board?queueType=consult` with its original number; re-stamped
   visits show as checked in; the downtime window shows closed with its reason and
   the re-entry recorded against it (`GET /api/v1/downtime/windows`).

## Pass criteria

- [ ] Re-entry completed inside the 30-minute window.
- [ ] All paper tokens re-entered with original numbers preserved.
- [ ] Zero unmatched patients (every paper row maps to a real re-entry).
- [ ] Downtime log closed and auditable (window + reason + re-entry in one timeline).

## Records

File the drill sheet next to the pilot log: date, names, token range, re-entry batch
id, mismatch count, and who signed the pass criteria. A failed drill is re-run after
the specific step that failed is fixed - record both runs.

## Automated coverage (what CI already proves)

- 01.09 PIT journey: emergency bump under 2 s to the display path, priority classes
  interleaving per config, downtime window open/close + batch re-entry preserving
  paper token numbers.
- `scripts/demo-01.09-emergency-fastpath-downtime.ps1` - the same path live on :8080.
