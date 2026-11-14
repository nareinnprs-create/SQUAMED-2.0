# Section 04.05 — ICU charting — gate-run report

Static read-only assessment. Baseline commit `886c51c`, CI run `43548016617` (all 6 CI gates green; merged instruction coverage 80.13%). No code, ledger, chunk file, or `.gate/` content changed by this report.

## 1. Scope
Chunk file §04.05: 1–2 h flowsheets, ventilation/infusion charting, auto-aggregated fluid balance, SOFA/GCS informational calculators, critical-alert routing. Depends on 04.04 (device feed). Present at baseline: manual flowsheet entries, ventilation/infusion charting, alert lifecycle. **Device feed, calculators, auto-aggregation, cadence absent.**

## 2. Built
- `backend/squamed-core/src/main/kotlin/com/squamed/core/icu/service/IcuService.kt` — 14 functions (vitals/MAP, fluid entries, ventilation, infusions, alerts).
- ICU REST endpoints (PIT exercises over HTTP incl. 403 role checks).
- `V31__icu.sql` — 5 ICU tables.

## 3. Data/API/event contracts delivered
- Tables: 5 ICU tables (flowsheet entries, ventilation, infusion, alerts).
- API: vitals+MAP charting, fluid-balance entry list, ventilation charting, infusion start/stop, alert open→ack→resolve; 403 role-less.
- Events: no device stream (04.04 absent); no alert escalation event routing beyond open/ack/resolve.

## 4. Gates 1–12
| # | Gate | Result | Evidence |
|---|---|---|---|
| 1 | Functional completeness | **FAIL (PARTIAL)** | Present: vitals+MAP, fluid entries, ventilation, infusions start/stop, alerts open→ack→resolve. Missing: device-fed flowsheets (blocked by absent 04.04), SOFA/GCS calculators, auto-aggregated fluid balance (`listFluidBalance` returns entries only, no summation), 1–2 h flowsheet cadence. |
| 2 | Zero-stub scan | PASS | CI run 43548016617 (gate 2 green); SC-9 allowlist. |
| 3 | Types | PASS | CI run 43548016617 (gate 3 green). |
| 4 | Lint + format | PASS | CI run 43548016617 (gate 4 green). |
| 5 | Tests | PASS | CI run 43548016617 (gate 5 green); merged instruction coverage 80.13% ≥ 0.80. |
| 6 | Accessibility | PASS | CI run 43548016617 (gate 6 green). Backend section; no ICU page at baseline. |
| 7 | Design system | PASS | CI run 43548016617 (gate 7 green). |
| 8 | Contracts | **FAIL** | Schema-registry harness not yet wired (00.02 scope). |
| 9 | Data | **FAIL** | RLS cross-tenant suite ABSENT until 00.04 (ADR-13) — tenant-scoped ICU tables. |
| 10 | Audit | PASS | ICU writes controller-mediated; `AuditAspect.kt:22-23` covers them. |
| 11 | Docs | PASS | This report re-checks chunk file §04.05; ledger untouched; gate-parity green. |
| 12 | Exit item | PASS | Named PIT slice `icuMonitoringCoverage` green in run 43548016617. |

## 5. Tests
- PIT: `icuMonitoringCoverage` — ICU vitals+MAP, fluid balance, ventilation, infusions start/stop, alerts open→ack→resolve, 403s. Green in run 43548016617.
- Unit: none dedicated.
- Coverage: merged instruction 80.13%.

## 6. Accessibility
axe green in CI; no ICU-specific page exists to scan.

## 7. Issues
- Fluid balance requires manual summation by readers (no aggregate) — **open**.
- SOFA/GCS calculators absent — open (chunk file marks them informational).
- Flowsheet cadence (1–2 h) unenforced — open; device feed is the root supply (04.04).

## 8. External blockers
⛔ 04.04 device integration (monitor gateway specs = biggest lead-time risk per chunk file) blocks device-fed flowsheets and the device-fed part of 04.06's timeline.

## 9. Deviations from chunk file
None logged; chunk file not edited.

## 10. Exit-demo slice
`gradlew.bat :squamed-core:integrationTest --tests "*PlatformIntegrationTests*icuMonitoringCoverage*"` — last run green in CI run 43548016617.
