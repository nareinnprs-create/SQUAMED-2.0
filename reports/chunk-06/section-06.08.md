# Section 06.08 — Resilience + retention + sandbox (chunk 6)

**Verdict: COMPLETE → GATE-12 PASS (partial: DR drill execution and the 10k replay drill owed)**. Gate-run assessment of the CURRENT tree @ PR #117 (chunks 0–5 + 06.01/06.03/06.04/06.05 merged + this PR). CI evidence: **run 45395624519 — all 7 gates green**. Named PIT `resilienceRetentionCoverage` green in the same run.

## 1. Scope
Per `chunk-06-enterprise-scale.md` §8/§9/§10/§11 and `19-CHUNK6-SECTIONS.md` 06.08: degraded mode whitelist, store-and-forward + replay, conflict rules, DR drill, Temporal adopt/defer, DPDP erasure/legal holds, sandbox hospital.

## 2. Key artifacts
**Migration `V95`**: `hospital.sandbox`, `resilience.degraded_state/queued_write/replayed_write`, `governance.retention_policy/legal_hold/erasure_request`, resilience/governance permissions.

**Backend:** `ResilienceEntities.kt`, `ResilienceRepositories.kt`, `ResilienceServices.kt` (degraded/replay/retention/sandbox), `ResilienceController.kt` (12 endpoints).

**Docs:** `docs/architecture/adr/ADR-15-temporal-defer.md`, `docs/runbooks/dr-drill.md`.

## 3. Gap closure
- ✔ Degraded mode with the offline write whitelist (identity/finance blocked, clinical appends queued, registration allowed).
- ✔ Store-and-forward queue with idempotent replay and zero-duplication ledger.
- ✔ Conflict rule: same key + different payload is a 409; append-only writes merge by occurrence order.
- ✔ Retention policies, legal holds, erasure (clinical blocked) + event.
- ✔ Sandbox hospital flag + deterministic synthetic seeding.
- ✔ Temporal deferral ADR with evidence.
- ⚠ DR drill (RTO/RPO measurement) owed to deployment; 10k replay drill owed to load testing.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS (partial) | PIT covers every shipped leg; DR execution owed |
| 2 Zero-stub | PASS | CI branch run 45395624519 |
| 3 Types | PASS | CI branch run 45395624519 |
| 4 Lint + format | PASS | CI branch run 45395624519 |
| 5 Tests | PASS | CI branch run 45395624519; named PIT below |
| 6 Accessibility | PASS | No UI change |
| 7 Design system | PASS | No UI change |
| 8 Contracts | PASS | Snapshot `1f0ca599…` + codegen committed |
| 9 Data | PASS | V95 additive; unique queue/ledger keys |
| 10 Audit | PASS | Erasure event with actor; replay resolutions recorded |
| 11 Docs | PASS | This report; ADR-15; DR runbook; LEDGER/tracker |
| 12 Exit item | PASS (partial) | Resilience slice of the 06.09 exit; DR drill owed |

## 5. Tests
Named PIT **`resilienceRetentionCoverage`**: degraded classification (identity/billing blocked, eMAR append queued, registration allowed, non-append blocked); 50 queued writes replayed → ledger 50, second replay 0; key conflict 409 `IDEMPOTENCY_CONFLICT`; release restores writes; retention policy + hold blocks erasure (`LEGAL_HOLD_ACTIVE`) → release → erasure COMPLETED; clinical erasure 409 `ERASURE_CLINICAL_BLOCKED`; missing policy 400; sandbox seed refused (`HOSPITAL_NOT_SANDBOX`) then enabled → 5 `SBX-` patients; `governance.erasure_completed` in the outbox.

## 6. Accessibility
No UI change.

## 7. Dual review — adversarial pass
1. **Offline safety** — identity, billing, finance and consent writes are blocked in degraded mode; only registration/queue/display pass through; clinical appends are queued, not silently dropped.
2. **Replay idempotence** — a unique ledger key means a replayed write can never apply twice; the second replay applies zero.
3. **Conflict rule** — the same key with a different payload is rejected at enqueue (409); append-only clinical writes carry `MERGED_BY_OCCURRENCE`.
4. **Ordering** — replay walks pending writes by occurrence time.
5. **Legal holds** — an active hold blocks erasure; holds are released explicitly with an audit timestamp.
6. **Clinical erasure** — clinical entity types are never erasable by this path.
7. **Sandbox isolation** — seeding requires the sandbox flag; non-sandbox hospitals refuse.
8. **Honest DR** — the runbook records the procedure and evidence fields; no fake RTO/RPO numbers.

## 8. Deviations from contract
- DR drill execution + measured RTO/RPO owed (needs a second region).
- Replay applies at the ledger level; module dispatchers are future work; 10k-event load drill owed.
- Retention is policy + erasure tooling (no scheduled expiry sweeper).
- Sandbox seeds patients only (synthetic encounters/orders owed).

## 9. Session trail
- Local Redis 6380 was in a Windows excluded port range; the PIT ran on a 7379 container and the patch was reverted before commit.
- Docker Desktop dropped twice; compose restarted.
