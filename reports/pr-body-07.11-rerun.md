## 07.11 — chunk-7 exit re-run against the new installer and drill harness

Docs + demo only; no runtime code changed. The exit scenario now exercises the two capabilities built since the first exit pass, so the report stops calling them owed.

### Demo: 22 → 32 checks, all passing live
`scripts/demo-07.11-chunk7-exit.ps1` now also proves:

- **Config-only specialty pack install** (07.10): register a two-component pack (task-sla config + CARDIOLOGY role), install it for the hospital, read the published config back (`defaultSlaHours=48`), and confirm a second install returns `ALREADY_INSTALLED` — plus the install duration, which is milliseconds, not the "< 1 day" the exit criterion allows.
- **Mass-casualty drill harness** (07.05): three casualties registered through the real ED paths, drill `COMPLETED`, stood down, and no mass-casualty event left active.
- **Capacity answer** now asserts the five evidence factors (beds, diagnostics, equipment, staffing, forecast) rather than three.

### Owed list: three → two
1. The 22 clinically reviewed specialty pack **contents** — the installer is built and exercised.
2. The prescriptive-CDS **counsel opinion** — external gate, documented, not crossed.

The mass-casualty drill harness is no longer owed.

### Section status entering the exit
07.01, 07.02, 07.03, 07.04, 07.05, 07.07, 07.08, 07.09 COMPLETE; 07.06 (E12 vendor import) and 07.10 (pack contents) SECTION REVIEW; 07.11 SECTION REVIEW on the two items above.

### Evidence
- Live run: **32 passed, 0 failed** against the app on :8080 with the dev compose stack.
- No code change, so the previous full chain (115 PIT + 57 unit, JaCoCo 85.05%) still stands.
