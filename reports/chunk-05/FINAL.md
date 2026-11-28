# CHUNK 5 - FINAL REPORT (section 05.08 chunk exit)

## 1. Scope
Chunk-5 exit per `chunk-05-ai-intelligence.md` EXIT CRITERIA (L54), TESTS (L45-52), SAFETY (L39-43), ROLLOUT/ROLLBACK (L57-59) and `03-BUILD-PROTOCOL.md` §7. **05.08 carries zero feature code by design.**

**Honest chunk state at exit:** 5 of the buildable sections COMPLETE (05.01, 05.03, 05.04, 05.06, 05.07). **05.02 Eval framework landed SECTION REVIEW 2026-11-28** (harness, datasets, gates, leak suite and 500-sim built; the 200 recorded consults remain `exp:E8`) (gold sets are human-paced), and **05.05 AI Scribe remains IN PROGRESS - gated `exp:E8`** (STT/LLM provider contracts + eval set), so the scribe leg of the exit scenario is owed and reported, never faked. **05.08 lands at SECTION REVIEW**: the 2-week per-doctor pilot and the provider procurement are deployment activities.

## 2. Built (this docs batch)
- `scripts/demo-05.08-chunk5-exit.ps1` - 24-check live exit walk (everything except the scribe leg).
- `docs/runbooks/ai-rollout.md` - capability flags, enable order, 2-week pilot, kill-switch drill, rollback.
- `reports/chunk-05/section-05.08.md` + this FINAL + LEDGER row 05.08 + tracker row.

## 3. Chunk-5 section statuses
| Section | Status | Report |
|---|---|---|
| 05.01 AI gateway | COMPLETE (PR #103) | `reports/chunk-05/section-05.01.md` |
| 05.02 Eval framework | **SECTION REVIEW** (harness built 2026-11-28; gold set owed `exp:E8`) | `reports/chunk-05/section-05.02.md` |
| 05.03 Command bar | COMPLETE (PR #104) | `reports/chunk-05/section-05.03.md` |
| 05.04 Intelligence rail | COMPLETE (PR #106) | `reports/chunk-05/section-05.04.md` |
| 05.05 AI Scribe + report drafting | **IN PROGRESS - gated `exp:E8`** | `reports/chunk-05/section-05.05.md` |
| 05.06 Three production agents | COMPLETE (PR #107) | `reports/chunk-05/section-05.06.md` |
| 05.07 Governance registry | COMPLETE (PR #105) | `reports/chunk-05/section-05.07.md` |
| 05.08 Chunk exit | **SECTION REVIEW** (pilot + procurement) | `reports/chunk-05/section-05.08.md` |

## 4. Chunk exit demo evidence
`pwsh scripts/demo-05.08-chunk5-exit.ps1` against the app on :8080: **24 passed, 0 failed**, live 2026-11-24. Scenario: fresh hospital + governance admin + doctor -> overdue-discharge admission + delayed OT case -> **command bar answers "how many patients?" with a citation (`count.patients`)** -> **ops agent proposes a blood-bank task** -> proposal on the approval queue -> **manager approval executes through the task API** -> task exists -> **rail shows the discharge blocker + OT delay and no longer lists the approved proposal** -> **auditor trace**: chat tokens/cost, redaction, model + `ops@version`, prompt/completion split, cost dashboard, eval PASS attached to `ops@0`, incident opened/closed -> **cost cap 1 hard-cuts chat (409)** and restores -> **kill-switch drill on a dummy capability in 0.2 s (<60 s)** with chat unaffected. **Scribe leg reported OWED (05.05, `exp:E8`).**

## 5. Named test evidence (chunk TESTS L45-52)
| Contract test | Evidence |
|---|---|
| Scribe eval: >=95% gold facts, 0 critical-field fabrications | **Owed to 05.05** (`exp:E8`): no scribe eval set yet |
| Command bar permission-leak suite (cross-dept/tenant/agent-impersonation), citations >=95% | Permission enforcement: `aiCommandBarGroundingCoverage` (scope denial), `aiGatewayGovernanceCoverage` (tenant caps/kill switch), `aiProductionAgentsCoverage` (persona boundaries); the adversarial permission-leak suite now runs in 05.02 (`aiEvalFrameworkCoverage`: DENY/ALLOW probes, cross-tenant forbidden-token probe, all-skipped suite fails) |
| Agents: unauthorized-action rate 0, proposal expiry | `aiProductionAgentsCoverage` (persona boundaries, disabled agents), `aiCommandBarGroundingCoverage` (approval permission, decided-draft 409); expiry is code-enforced, a 500-sim is owed |
| Cost caps: simulated spike -> cap -> capability off -> audited | `aiGatewayGovernanceCoverage` (cap 1 -> 409) + the exit demo |
| Kill-switch drill <60 s, staging, timed | Exit demo: 0.2 s end to end |

## 6. Safety / compliance (L39-43) status
- No direct clinical-record writes: agents/commands only create drafts; approvals execute governed APIs - 05.03/05.06.
- Grounding mandatory: record-backed answers carry citations, `AI_UNGROUNDED_ANSWER` otherwise - 05.03.
- Kill switch <1 min: policy read per request - 05.01 + drill.
- Zero-retention provider contracts: procurement item (not code); self-hosted endpoints are first-class - 05.01.
- Scribe consent flag + 30-day audio retention: 05.05 (gated).
- Redaction: identifiers stripped before dispatch - 05.01.
- Prompt-injection: tool calls are server-side intent handlers, untrusted text never authorizes tools - 05.03 design note.

## 7. Owed / tracked (not skipped)
| Item | Owner | Why it is not CI evidence |
|---|---|---|
| Eval framework + gold sets (05.02) | Clinical + product | `exp:E8`; human-paced recording |
| AI Scribe + report drafting (05.05) | Product + provider contracts | `exp:E8`; STT/LLM provider + eval set |
| Adversarial permission-leak suite | 05.02 | Harness built 2026-11-28 (`aiEvalFrameworkCoverage`) |
| 500-sim agent unauthorized-action rate | 05.02 | Harness built 2026-11-28 - 500 scenarios, unauthorized 0 |
| Provider contracts (India-region/zero-retention) | Procurement | External |
| Recording hardware/mic standard | Operations | External |
| 2-week per-doctor pilot + general enable | Clinical lead | Deployment activity (runbook ready) |

## 8. Gate 12 disposition
Chunk-5 GATE-12: **PASS (partial)** - all five buildable sections carry CI evidence on merged main; 05.02/05.05 are externally gated and tracked; 05.08 is docs + a 24/24 live demo. Chunk-6 (enterprise/scale) consumes the trace, event fabric and governance registry.

## 9. Sign-off
| Role | Name | Date | Verdict |
|---|---|---|---|
| Build owner | - | - | Chunk-5 buildable sections COMPLETE; 05.02/05.05 gated; 05.08 SECTION REVIEW |
| Clinical lead | - | - | Pending eval sets + pilot |
| Owner (B2) | - | - | Pending provider contracts + go-live decision |
