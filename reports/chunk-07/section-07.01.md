# Section 07.01 — Six new agents + orchestrator (chunk 7)

**Verdict: COMPLETE → GATE-12 PASS (partial: 07.11 exit slice).** Gate-run assessment of the CURRENT tree @ PR #127. CI evidence: **run 45627032693 — all 7 gates green**.

## 1. Scope
Per `chunk-07-full-depth.md` item 1 / `20-CHUNK7-SECTIONS.md` 07.01: six additional agents on top of chunk-5's three, an orchestrator that decomposes cross-domain questions into agent queries and synthesizes blocker → owner → impact → recommended action, per-agent eval suites with unauthorized-action = 0.

## 2. Key artifacts
**Migration `V99`**: six `ai.agent_config` personas (`clinical`, `pharmacy`, `facilities`, `procurement`, `security`, `executive`) with scopes.

**Backend**
- `ai/service/AgentPersonas.kt` — one persona boundary table shared by proposals and the eval harness.
- `ai/service/AiOrchestrator.kt` — deterministic domain classifier, per-agent diagnostics over governed repositories, severity-ranked synthesis, per-agent unauthorized-action eval recorder.
- `ai/api/AiOrchestratorController.kt` — `POST /api/v1/ai/orchestrate`, `POST /api/v1/ai/agents/{code}/eval`.
- `AiActions.proposeAsAgent` now uses the shared persona table (unknown agent codes are denied).

## 3. Gap closure
- ✔ Six personas seeded, enabled, scoped, and proposing only their own task domain.
- ✔ Orchestrator routes surgery/clinical/pharmacy/procurement/facilities/security/executive questions to the right agents.
- ✔ Diagnostics read governed state: critical results + overdue discharges, pharmacy expiry/reorder, CSSD tray availability + work orders + assets, pending requisitions, high-severity incidents.
- ✔ Synthesis returns blocker, owner role, impact and recommended action; executive questions consult all six agents and pick the highest-severity blocker.
- ✔ Per-agent eval runs 10 persona probes and records an `ai.eval_run`; PASS only when unauthorized actions = 0.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | CI run 45627032693; `aiOrchestrationCoverage` green |
| 2 Zero-stub | PASS | CI run 45627032693 |
| 3 Types | PASS | CI run 45627032693 |
| 4 Lint + format | PASS | CI run 45627032693 |
| 5 Tests | PASS | CI run 45627032693; 103 PIT methods + 56 unit tests, 0 failures; JaCoCo instruction 84.48% (≥0.80) |
| 6 Accessibility | PASS | No UI change |
| 7 Design system | PASS | No UI change |
| 8 Contracts | PASS | snapshot regenerated (`cc8f5a82…`); CI run 45627032693 |
| 9 Data | PASS | V99 additive; CI run 45627032693 |
| 10 Audit | PASS | Controller-mediated writes; eval runs carry actor |
| 11 Docs | PASS | This report; LEDGER; tracker |
| 12 Exit item | PASS (partial) | 07.11 exit slice (surgical-delay question) |

## 5. Tests
Full suite: `integrationTest test jacocoTestReport jacocoTestCoverageVerification` — BUILD SUCCESSFUL, 103 PIT methods + 56 unit tests, 0 failures, JaCoCo instruction 84.48%.

Named PIT **`aiOrchestrationCoverage`**:
- Seeds one blocker per domain (overdue discharge, expired/low stock, CSSD tray in cycle, submitted requisition, high-severity incident) in a fresh hospital.
- 50 cross-domain questions vs ground truth; asserts primary-blocker accuracy ≥ 45/50 (observed 50/50).
- Executive question consults all six agents, picks the critical blocker, names owner + action.
- Nine personas (six new + three existing) each score 10/10 with zero unauthorized actions.
- Clinical agent cannot propose a PHARMACY task (400 `AI_AGENT_ACTION_NOT_ALLOWED`); an own-domain proposal succeeds.

## 6. Accessibility
No UI change.

## 7. Dual review — adversarial pass
1. **No execution** — agents only create drafts; approval still runs the platform service with the approver's scopes.
2. **Persona boundary** — shared table; unknown codes denied; cross-domain proposals 400.
3. **Orchestrator read-only** — diagnostics only read repositories; no source mutation.
4. **Severity ranking** — critical before high before medium; consulted order breaks ties except executive synthesis, which ranks globally.
5. **Tenant** — every diagnostic query is hospital-scoped through the caller's pinned tenant.
6. **Eval integrity** — ground truth is declared independently of the policy table; PASS requires 10/10 and zero unauthorized actions.
7. **Determinism** — no LLM in the routing path; the same state + question yields the same answer.

## 8. Deviations from contract
- Orchestrator routing is deterministic keyword + governed-state diagnostics rather than LLM decomposition; the §8 contract rules (draft-only, approval, scopes) are unchanged.
- Executive agent has no own source records; it synthesizes the other agents' findings.
- Security agent reads the AI incident register (no dedicated security-operations incident module in scope).
- No new event-catalog entries; eval runs and incidents already have cataloged events.

## 9. Session trail
- Local Docker compose restarted after an engine stop; named PIT green first pass.
- Full suite run 1: 102/103 green; `fullPatientRevenueJourney` hit a transient queue-start 500 and passed standalone on re-run. Suite re-run for clean local evidence.
