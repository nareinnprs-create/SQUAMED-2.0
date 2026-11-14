# Section 02.01 — Analyzer intake (Mirth) (chunk 2)

**Gate-run assessment of the CURRENT tree** @ 886c51c (merged baseline + wiring PRs #2–#11). Static read-only; no builds/tests run. Verdict after verification: **PARTIAL (thin)** → **IN PROGRESS**. CI evidence: run 43548016617 — all 6 gates green.

## 1. Scope
Per `15-CHUNK2-SECTIONS.md`: HL7 v2 channels, ASTM bridge adapter, `analyzer_message_log`, quarantine + alert (zero silent drops), ACK policy, version-controlled channel configs, 1k-message sim harness. ⛔ external: analyzer interface specs week 1.

## 2. Built (current tree)
- `backend/squamed-core/src/main/kotlin/com/squamed/core/interop/hl7/Hl7Service.kt` — hand-rolled MLLP server bound to **loopback only** (`InetAddress.getLoopbackAddress()`, port 2575, 16 backlog, 30 s timeouts), MSH-required parse, ACK build (AA/AE), `receive()` + `processInbound()` persist every inbound message to `Hl7MessageLog` (messageType, controlId, direction, source, ackCode, raw up to 20k chars, parsed JSON), ADT-only PID import (ORU and other types accepted + ACKed AA but **results not imported**), MLLP log-save failure caught→warn (log row dropped silently in that path).
- `backend/.../interop/hl7/Hl7Model.kt` — segment/component parser + `buildAck` (MSH/MSA/ERR construction).
- `backend/.../interop/config/Hl7ServerLifecycle.kt` — starts server on ApplicationReady.
- `backend/.../interop/domain/InteropEntities.kt` — `Hl7MessageLog` (+ ack_code column) and `IntegrationConnection`.
- `backend/.../interop/service/IntegrationConnectionService.kt` — connection registry with MLLP/TCP health probe + error counting.
- `backend/.../interop/api/InteropControllers.kt` — `/api/v1/hl7` parse/receive/history, `/api/v1/interop` connections CRUD + health (+ FHIR read views, 06.07 scope).

## 3. Data/API/event contracts delivered
- Schema: `V24__interop.sql` → `interop.hl7_message_log` (the de-facto message log; **not** the contract's `analyzer_message_log`), `interop.integration_connection`.
- API: POST `/api/v1/hl7/messages` (receive, returns ackCode/importedPatientId/logId), GET `/api/v1/hl7/messages` (history), POST `/api/v1/hl7/parse`, `/api/v1/interop/connections` CRUD + health.
- MLLP inbound framing with ACK reply over socket.
- No events emitted on message intake.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | **FAIL** | HL7 v2 ADT intake + ACK + message log + connection health work (loopback). Missing named contents: **HL7 channels/channel configs (version-controlled)** (none — hardcoded lifecycle), **ASTM bridge adapter** (none), **`analyzer_message_log`** (generic hl7_message_log only), **quarantine + alert with zero silent drops** (no quarantine lane; import errors → AE + log but no alert; MLLP log-save failure is a silent drop), **ORU result flow into lab** (OBX parsed/ACKed but never imported), **1k-message sim harness** (none). Gap list: quarantine+alert · ASTM bridge · ORU→lab import · channel configs · sim harness |
| 2 Zero-stub | PASS | CI run 43548016617 (gate 2 green); no stub markers in interop module |
| 3 Types | PASS | CI run 43548016617 |
| 4 Lint + format | PASS | CI run 43548016617 |
| 5 Tests | PASS | CI run 43548016617; named tests below; merged instruction coverage 80.13% ≥ 0.80 |
| 6 Accessibility | PASS | CI run 43548016617; `/integrations` page exists (35.7 KB, connection registry UI) but is NOT in SCAN_ROUTES — not axe-scanned |
| 7 Design system | PASS | CI run 43548016617 (visual regression green) |
| 8 Contracts | **FAIL** | schema-registry harness not yet wired (00.02 scope); OpenAPI surfaces `hl7`/`interop` (PIT `openApiExposesAllModuleSurfaces`); no ACK-policy or channel-config contract artifacts |
| 9 Data | **FAIL** | Migration `V24__interop.sql` applied; **no rollback note**; **no RLS/cross-tenant denial suite** (00.04/ADR-13 pending); message log carries no hospital scoping |
| 10 Audit | PASS | `AuditAspect` intercepts InteropControllers writes (hl7 receive, connection CRUD) → audit rows; audit read asserted in PIT `collaborationAndWorkflowJourney`. Caveat: plain audit table (ADR-9 pending) |
| 11 Docs | PASS | This report; chunk file lists contents accurately; ledger row stays PLANNED |
| 12 Exit item | **FAIL** | no scripted slice yet (analyzer sim is 02.09 exit scope; nothing scripted) |

## 5. Tests
Named tests (green in run 43548016617): PIT `hl7ReceiveVariantsAndLockoutCoverage` (ORU^R01 → AA, ADT-without-name → AE, auth lockout adjacency), PIT `hl7ReceiveAndAuthFailureCoverage` (ADT receive + history), PIT `interopAndFhirCoverage`, unit `Hl7MessagesTest` (7 tests: parse/ACK construction). Pass counts: PIT 37/37 + unit suite green. Coverage: merged instruction 80.13% (branch 52%).

## 6. Accessibility
Backend-only section; repo-wide axe gate green (run 43548016617). `/integrations` page not axe-scanned (outside SCAN_ROUTES).

## 7. Issues found during build + fixes
Baseline section — see BASELINE-AUDIT.md §5 D19 (hand-rolled MLLP vs Mirth; loopback-only, ADT-only). Verified in current tree: unchanged except the PIT suite now proves ORU is **accepted and ACKed AA** (logged, not imported) and ADT-variant AE paths — the "ADT-only" audit description remains accurate for *import*.

## 8. External blockers
Yes — analyzer interface specs (E6-class external dependency, ledger `exp:E6` on the 02.01 row; week-1 chunk-2 item per chunk file). Zero-silent-drop + ASTM work is blocked on those specs for real analyzers; the code gaps above are buildable without them.

## 9. Deviations from chunk file
Zero (SC-6 baseline adoption). Note: D19 (Mirth vs harden) is decided as ADR-14 per `24-DECISIONS-REGISTER.md` — implementation choice pending, chunk file unchanged.

## 10. Exit-demo slice
Nearest scripted evidence: PIT `hl7ReceiveVariantsAndLockoutCoverage` + `hl7ReceiveAndAuthFailureCoverage` (run 43548016617). Reproduce locally: `./gradlew integrationTest --tests "*hl7*"` with compose stack up; or `pwsh scripts/phase1-e2e.ps1` (walks `/api/v1/hl7/messages` + parse). No 1k-message sim exists.
