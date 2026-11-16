# MASTER TEST STRATEGY
**Gate owner:** QA engineer. Every gate in `03-BUILD-PROTOCOL.md` draws its detail from here.

## 1. Test pyramid & targets
| Level | Tooling | Target |
|---|---|---|
| Unit | JUnit5 (Kotlin), Vitest (TS) | ≥ 80% changed-code coverage; pure-domain logic fully covered |
| Integration | **PIT suite** (`backend/squamed-core`, Gradle task `integrationTest`): by default runs against the **compose dev stack** (`SQUAMED_IT_EXTERNAL=true` — PG 5433, Redis 6380, Kafka 9092, OpenSearch 9200, MinIO 9000); set `SQUAMED_IT_EXTERNAL=false` for Testcontainers mode (Postgres, Redis, Kafka, OpenSearch, MinIO — no Keycloak/Temporal containers). **Never run PIT while a bootRun app is alive** (HL7 MLLP listener collides on 127.0.0.1:2575) | Every repository/service against real DB; RLS denials; outbox→dispatch |
| Contract | OpenAPI snapshot byte-compare + event JSON-schema validation | Breaking API/event change fails CI |
| E2E | Playwright (chromium/firefox/webkit desktop projects; CI installs **chromium only** — no tablet emulation yet) | Chunk exit scenarios scripted; clinical-safety UI flows |
| Performance | Custom Node `.mjs` load scripts (k6 not installed), app on **8081** | NFR §6: 1,000 users, p95 targets, WS fan-out 200/display-zone |
| **Sync chaos simulator** | Nested PIT (`PlatformIntegrationTests` chaos test), **not a separate CI job** | Offline queue replayed under randomized drop/duplicate/reorder/partition — replay must be exactly-once with net effect identical to the online path (extends CS-08) |
| **Architecture continuity drill** | Manual, every chunk exit | Fresh agent builds a throwaway module using **only** `00–13` docs; failure = docs defect, chunk cannot close (risk #9) |
| Security | gitleaks, dep-scan, ZAP baseline | Blocking per §07 — **scanning jobs not wired into CI yet** |
| Accessibility | axe-core CI + manual keyboard/reader pass | Zero critical/serious |
| Visual | Playwright screenshots per component (light/command/brand) | Pixel drift fails |

## 2. Environments
| Env | Purpose | Data |
|---|---|---|
| dev (compose) | Engineering | Synthetic, resettable |
| staging (k8s) | Release candidate, DAST, perf | Synthetic, prod-shaped volume |
| test-hospital (sandbox) | Training + UAT + chunk exit demos | **Synthetic only** (§4) — never real PHI in any non-prod system |
| prod | Live | Real PHI, audit on |
| DR | Standby | Restored copy, drill-verified |

**PIT authoring conventions** (`src/integrationTest/.../it/PlatformIntegrationTests.kt`): run `$env:SQUAMED_IT_EXTERNAL='true'; .\gradlew.bat integrationTest --console=plain` from `backend/squamed-core` (task name is load-bearing, called literally by CI); test data must be unique per run via `suffix()` helpers (fresh hospitals per scenario where isolation matters); assert on API responses, not DB state, except where the property is DB-level (tenant guards, audit chain); test-JVM stdout lands in `build/test-results/integrationTest/TEST-*.xml`.

## 3. Clinical-safety test catalog (every case automated where possible; drills manual)
| ID | Case | Assertion |
|---|---|---|
| CS-01 | Wrong-patient order attempt | Two-identifier block fires; attempt audited |
| CS-02 | Allergy BLOCK match | eRx/dispense hard-blocked; no override path |
| CS-03 | Allergy WARN override | Requires typed reason; override KPI increments |
| CS-04 | High-alert medication | Second authorized user mandatory before administration |
| CS-05 | Critical result SLA | Unacked → auto-escalation chain fires at SLA; ack trail complete |
| CS-06 | eMAR barcode mismatch | Hard block; omission requires reason + prescriber notification |
| CS-07 | Patient merge error | Reversal restores byte-identical prior state |
| CS-08 | Offline replay | Queued offline writes replay exactly once; conflicts resolve per ADR-4 rules |
| CS-09 | Downtime paper mode | Module pack prints; re-entry tool restores without loss |
| CS-10 | Blood bedside check | Wristband mismatch = BLOCK |
| CS-11 | Discharge without med-rec | Blocked until task cleared |
| CS-12 | Unsigned note → claim | Claim assembly rejects |
| CS-13 | Mass casualty mode | Triage surge accepted; drill checklist passes (chunk 7) |
| CS-14 | AI draft → legal record | No AI write path exists to signed records (architecture test) |
| CS-15 | Audit chain tamper | Any mutation detected at next verify; alert pages |

New clinical feature ⇒ new CS cases appended here **before** implementation (protocol §5). Bindings (case → owning section → exit evidence): `22-CS-CASE-MATRIX.md` — binding is the QA dispatch scope.

## 4. Synthetic data generator
- Realistic Indian patient corpus (name/regional distribution, DOB spread, ABHA-shaped identifiers, comorbidity distributions), 10k patients + encounters + labs + bills by chunk 4; deterministically seeded (reproducible evals).
- Generator lives in `tools/synthetic-data/`, runs per environment refresh; **real PHI never enters non-prod** (verified by scan + attestation in reports).

## 5. Flaky-test policy
Quarantine label + 48h fix-or-delete; a quarantined test cannot mask a red suite (quarantined tests don't count toward green).

## 6. Release gates recap
Chunk exit = all pyramid levels green + CS catalog green for shipped features + perf within NFR + a11y clean + exit demo run recorded in chunk FINAL report.
