# Section 04.05 — ICU charting (chunk 4)

**Gate-run assessment of the CURRENT tree** @ PR #96 (chunks 0–3 + 04.01-04.03 merged + this PR). Verdict: **COMPLETE → GATE-12 PASS (partial: chunk-4 exit demo slice owed at 04.09)**. CI evidence: **run 45093632237 — all 7 gates green**. Named PIT `icuChartingCoverage` green in the same run.

## 1. Scope
Per `chunk-04-inpatient-surgery-critical.md` §3: ICU flowsheets (hourly), ventilation/infusion charting (manual; device-fed is 04.04, gated `exp:E7`), fluid balance auto-aggregation, SOFA/GCS severity scores (informational), critical-alert routing.

## 2. Key artifacts
**Migration `V80__icu_charting.sql`** (additive; rollback note inside):
- `icu.severity_score` (SOFA component points + total + input snapshot), read-model indexes on `(patient_id, recorded_at)`.

**Behaviour:**
- Flowsheet `GET /icu/patients/{id}/flowsheet?hours=` — hourly buckets with the latest vitals/ventilation per hour, infusions active in the hour, and fluid input/output/net.
- Fluid totals `GET /icu/patients/{id}/fluid-balance/totals?hours=` — input/output/net over the window.
- SOFA `POST /icu/severity-scores` — six component points (respiratory support distinguishes 3/4) + total, stored with the inputs; informational.
- ICU critical alerts emit `icu.critical_alert`, now cataloged as `ThresholdAlertRaised` (EMITTED); acknowledge unchanged.
- Catalog 38 → 39; OpenAPI snapshot (`687eb519…`), codegen stamped.

## 3. Gap closure
- ✔ Hourly ICU flowsheet read model
- ✔ Auto-aggregated fluid balance
- ✔ SOFA severity score (informational)
- ✔ Alert routing event + ack
- ✔ PIT 04.05: `icuChartingCoverage` PASS

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | PIT covers buckets, ventilation/infusion carry, fluid totals, SOFA points, alert event/ack |
| 2 Zero-stub | PASS | CI branch run 45093632237 |
| 3 Types | PASS | CI branch run 45093632237 |
| 4 Lint + format | PASS | CI branch run 45093632237 |
| 5 Tests | PASS | CI branch run 45093632237; named PIT below |
| 6 Accessibility | PASS | CI branch run 45093632237 (web untouched) |
| 7 Design system | PASS | CI branch run 45093632237 |
| 8 Contracts | PASS | Snapshot + codegen committed; parity green |
| 9 Data | PASS | V80 additive + rollback note; indexes for the read models |
| 10 Audit | PASS | Chart rows carry recorders; SOFA stores its inputs; alerts carry actors |
| 11 Docs | PASS | This report; LEDGER row 04.05 COMPLETE; tracker updated |
| 12 Exit item | PASS (partial) | ICU slice of the 04.09 exit demo |

## 5. Tests
Named PIT **`icuChartingCoverage`** (new): backdated vitals/vent/infusion/fluid → ≥3 hourly buckets with latest vitals, SIMV row, carried infusion, per-hour input/output; 24h totals 1500/900/600; SOFA components 2/2/2/1/2/3 = 12; alert open → event → acknowledged; catalog entry EMITTED.

Regressions green: `icuMonitoringCoverage`, `eventFabricCoverage`, `consentNotificationCoverage` (catalog 39).

## 6. Accessibility
Web surface untouched; repo-wide axe gate in CI.

## 7. Dual review — adversarial pass
1. **Hourly bucketing** uses `truncatedTo(HOURS)` on the event times; a row lands in exactly one bucket.
2. **Latest per hour** for vitals/ventilation avoids duplicate rows in a bucket; earlier readings are still in the raw tables.
3. **Infusion carry** includes infusions started before the hour and not yet stopped — clinically correct for a running drip.
4. **Fluid totals** are sums of charted rows (no derived losses); negative net is allowed (reported, not clamped).
5. **SOFA** is informational; the respiratory 3/4 distinction needs `ventilated=true` (a non-ventilated ratio <300 scores 2).
6. **Alert event** is a routing hook only — device alarms stay on-device (contract limitation for v1).
7. **Cross-test interference:** the PIT is patient-scoped with its own backdated rows.

## 9. Deviations from contract
- Device-fed vitals are 04.04 (`exp:E7`); the flowsheet consumes charted rows.
- Vasopressor SOFA input is a coarse code, not dose math.
- No ICU decision support/AI (chunk 5).

## 8. Session trail
- Repository signatures take `(patientId, hid)` — the read models pass `null` (patient-scoped, tenant checked by the controller).
- SOFA respiratory scoring corrected to require support for 3/4 (PIT caught 160 → 3 vs 2).
- Docker Desktop died twice; compose restarted, postgres healthy.
