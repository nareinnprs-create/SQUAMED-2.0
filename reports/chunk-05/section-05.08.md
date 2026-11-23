# Section 05.08 — Chunk-5 exit (AI intelligence)

**Verdict: SECTION REVIEW** (never COMPLETE). CI evidence: **run 45229065570 — all 7 gates green** on the docs branch. Live demo evidence: `scripts/demo-05.08-chunk5-exit.ps1` → **24 passed, 0 failed** against the app on :8080 (2026-11-24). Zero feature code: the exit item is the live scenario, the AI rollout runbook, this report and `reports/chunk-05/FINAL.md`.

## 1. Scope
Per `chunk-05-ai-intelligence.md` §EXIT CRITERIA (L54), TESTS (L45-52), SAFETY (L39-43), ROLLOUT/ROLLBACK (L57-59).

## 2. Deliverables
- `scripts/demo-05.08-chunk5-exit.ps1` — 24-check live walk.
- `docs/runbooks/ai-rollout.md` — flags, enable order, 2-week per-doctor pilot, kill-switch drill, rollback.
- `reports/chunk-05/FINAL.md` — chunk roll-up, named-test evidence, owed items, Gate-12 disposition.
- This report + LEDGER row 05.08 (SECTION REVIEW) + tracker row.

## 3. Exit demo — what it proves (live, 24/24)
| Contract step | Demo evidence |
|---|---|
| Grounded command-bar answer | "how many patients?" → count + citation `count.patients` |
| Agent proposal | ops agent proposes "Blood bank: urgent crossmatch" task draft (`agentCode=ops`) |
| Approval UI path | draft on the PROPOSED queue → approve → governed task created |
| Rail reflects the day | overdue discharge blocker + delayed OT case shown; approved proposal no longer pending |
| Auditor AI trace | chat tokens/cost + redaction, model + `ops@version`, prompt/completion split, cost dashboard, eval PASS on `ops@0`, incident opened/closed |
| Cost cap | cap 1 → `409 AI_DAILY_TOKEN_CAP_REACHED` → restored |
| Kill switch | dummy capability off → chat unaffected → on; **0.2 s** (<60 s) |
| Scribe leg | **OWED** to 05.05 (`exp:E8`) — printed, not faked |

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | 24/24 live scenario |
| 2 Zero-stub | PASS | CI docs run 45229065570 |
| 3 Types | PASS | CI docs run 45229065570 |
| 4 Lint + format | PASS | CI docs run 45229065570 |
| 5 Tests | PASS | CI docs run 45229065570; chunk named PITs in FINAL §5 |
| 6 Accessibility | PASS | CI docs run 45229065570 (no UI change) |
| 7 Design system | PASS | CI docs run 45229065570 |
| 8 Contracts | PASS | No API change in this section; snapshot `45334cfb…` |
| 9 Data | PASS | No migration in this section |
| 10 Audit | PASS | Demo asserts trace/approvals/evals/incidents |
| 11 Docs | PASS | This report, FINAL, runbook, LEDGER + tracker |
| 12 Exit item | PASS (partial) | Full non-scribe scenario live; scribe (05.05) and eval framework (05.02) externally gated |

## 5. Contract TESTS status
- Scribe eval — **owed to 05.05** (`exp:E8`).
- Command-bar permission-leak suite — scope/tenant/persona enforcement proven by PITs; a formal adversarial suite is owed to 05.02.
- Agent unauthorized-action rate / 500-sim — persona boundaries + disabled agents proven; the sim harness is owed to 05.02.
- Cost caps — PIT + demo.
- Kill-switch drill — demo, timed 0.2 s.

## 6. Safety (L39-43) status
- No direct record writes; approvals human + governed — 05.03/05.06.
- Grounding mandatory — 05.03.
- Kill switch per capability, immediate — 05.01 + drill.
- Redaction before dispatch — 05.01.
- Scribe consent/retention — 05.05 (gated).

## 7. Deviations / owed
- 05.02 eval framework PLANNED (`exp:E8`): adversarial suites + 500-sim owed.
- 05.05 scribe IN PROGRESS (`exp:E8`): STT/LLM contracts + gold set; the scribe leg of the exit scenario is absent.
- Provider procurement + recording hardware are external.
- The 2-week per-doctor pilot is a deployment activity (runbook ready).

## 8. Session trail
- The exit demo originally planned an OT-delay Q&A; the command bar has no free-form clinical intent, so the demo uses a cited count + the ops proposal + the rail to prove the same chain honestly.
- Docker Desktop dropped twice; compose restarted before the live run.
