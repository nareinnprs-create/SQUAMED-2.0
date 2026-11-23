# Section 05.06 — Three production agents (chunk 5)

**Verdict: COMPLETE → GATE-12 PASS (partial: agent runtime/tool loop out of scope)**. Gate-run assessment of the CURRENT tree @ PR #107 (chunks 0–4 + 05.01/05.03/05.04/05.07 merged + this PR). CI evidence: **run 45223585102 — all 7 gates green**. Named PIT `aiProductionAgentsCoverage` green in the same run.

## 1. Scope
Per `chunk-05-ai-intelligence.md` §6: Operations, Revenue and Patient-comms agents — proposal-only, scoped, execution always through governed platform APIs on human approval.

## 2. Key artifacts
**Migration `V89__ai_agent_personas.sql`**: seeds `revenue` and `patient_comms` personas (rollback = delete rows).

**Backend:** `AiActions.kt` (`proposeAsAgent` with persona boundaries + scope check), `AiController.kt` (`POST /ai/agents/{code}/propose`).

## 3. Gap closure
- ✔ Three personas registered (ops, revenue, patient_comms).
- ✔ Agents only propose; approval (05.03) executes through `TaskService` / `AdmissionService` with the approver's scopes.
- ✔ Persona boundaries: revenue only REVENUE tasks, patient_comms only PATIENT_COMMS tasks, ops task/discharge.
- ✔ Disabled agents blocked; unknown agents rejected.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | PIT covers all three personas + boundaries + disabled/unknown |
| 2 Zero-stub | PASS | CI branch run 45223585102 |
| 3 Types | PASS | CI branch run 45223585102 |
| 4 Lint + format | PASS | CI branch run 45223585102 |
| 5 Tests | PASS | CI branch run 45223585102; named PIT below |
| 6 Accessibility | PASS | No UI change |
| 7 Design system | PASS | No UI change |
| 8 Contracts | PASS | Snapshot `45334cfb…` + codegen committed |
| 9 Data | PASS | V89 additive seeds only |
| 10 Audit | PASS | Draft rows carry `agentCode` provenance; approval/events as 05.03 |
| 11 Docs | PASS | This report; LEDGER row 05.06 COMPLETE; tracker |
| 12 Exit item | PASS (partial) | Agent-proposal slice of the chunk-5 exit; rail 05.04, scribe 05.05 |

## 5. Tests
Named PIT **`aiProductionAgentsCoverage`**: three personas seeded; ops proposes a task → approve → task created; revenue REVENUE ok / OTHER 400 `AI_AGENT_ACTION_NOT_ALLOWED`; patient_comms PATIENT_COMMS ok; disabled revenue 409 `AI_AGENT_DISABLED`; unknown agent 400 `AI_AGENT_NOT_FOUND`.

Regressions: `aiRailAttentionCoverage`.

## 6. Accessibility
No UI change.

## 7. Dual review — adversarial pass
1. **No execution path in the agent** — the only write is the draft row; execution is the 05.03 approval.
2. **Boundary by related type** — revenue/comms cannot propose unrelated work even if the caller is an admin.
3. **Disabled kill** — a disabled persona is refused before any draft is created.
4. **Scope inheritance** — agent scopes (05.07) are checked against the caller first.
5. **Provenance** — `agentCode` rides in the payload, so the approval/trace shows which persona proposed.
6. **Unknown code** — 400 with `AI_AGENT_NOT_FOUND` (consistent with the platform's argument-error mapping).
7. **Shared TTL** — proposals expire in 24 h with the 05.03 lifecycle.

## 8. Deviations from contract
- Personas share the draft/approval machinery; no separate agent runtime or autonomous tool loop in chunk 5.
- Denial clustering/documentation nudges are prompt-level personas (data work belongs to chunk 3/6).
- Agent service accounts with narrow scopes are a deployment concern (05.07 note).

## 9. Session trail
- The unknown-agent guard returns 400 (IllegalArgumentException mapping), not 404; the PIT was corrected.
