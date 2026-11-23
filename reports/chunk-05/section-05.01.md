# Section 05.01 — AI gateway (chunk 5)

**Verdict: COMPLETE → GATE-12 PASS (partial: eval framework is 05.02, cost dashboard is 05.07)**. Gate-run assessment of the CURRENT tree @ PR #103 (chunks 0–4 merged + this PR). CI evidence: **run 45181226944 (attempt 2) — all 7 gates green**. Named PIT `aiGatewayGovernanceCoverage` green in the same run.

## 1. Scope
Per `chunk-05-ai-intelligence.md` §1 (single entry point: OIDC client identity, effective-scope enforcement, prompt redaction, provider router incl. custom/self-hosted, token budgets + daily tenant caps, full trace, kill switch) and `03-BUILD-PROTOCOL.md` §7.

## 2. Key artifacts
**Migration `V85__ai_gateway_governance.sql`** (additive; rollback note inside):
- `ai.gateway_policy` (hospital, capability, enabled, daily_token_cap; unique per hospital+capability).
- `ai.interaction_log` += `capability`, `prompt_version`, `prompt_tokens`, `completion_tokens`, `cost_usd`, `redacted`; index (hospital, capability, created_at).
- `ai.model_config` += `cost_per_1k_tokens`.
- `ai:chat:agent` granted to DOCTOR and NURSE.

**Code:** `AiGovernance.kt` (PromptRedactor + AiGovernanceService), `AiProviders.kt` (AiCompletion with usage), `AiService.kt` (governance gate, redaction, trace, scope map), `AiController.kt` (governance policies/usage endpoints, trace fields in logs).

## 3. Gap closure (from the gate-run report §7)
- ✔ `tokensUsed` always null → provider usage parsed (prompt/completion split), character-estimate fallback; cost from the model price.
- ✔ No tenant-cap hard-cut → daily cap per hospital+capability, checked before dispatch (`AI_DAILY_TOKEN_CAP_REACHED`).
- ✔ No kill switch → per-capability policy read fresh per request (`AI_CAPABILITY_DISABLED`); flip is effective on the next request.
- ✔ No prompt redaction → email/MRN/QID/phone stripped before dispatch; the log keeps the redacted text only.
- ✔ Effective-scope enforcement → intent→permission map through the platform evaluator (`AI_SCOPE_DENIED`).
- ✔ Custom/self-hosted OpenAI-compatible endpoints stay first-class routing targets.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | PIT covers redaction, trace, scopes, kill switch, cap, usage, cost, self-hosted model |
| 2 Zero-stub | PASS | CI branch run 45181226944 |
| 3 Types | PASS | CI branch run 45181226944 |
| 4 Lint + format | PASS | CI branch run 45181226944 |
| 5 Tests | PASS | CI branch run 45181226944; named PIT below |
| 6 Accessibility | PASS | CI branch run 45181226944 (web untouched) |
| 7 Design system | PASS | CI branch run 45181226944 |
| 8 Contracts | PASS | Snapshot `ebfa2520…` + codegen committed; parity green |
| 9 Data | PASS | V85 additive + rollback note; policy unique per hospital+capability |
| 10 Audit | PASS | Governance writes are controller-mediated (AuditAspect); interactions trace user/capability/model/cost |
| 11 Docs | PASS | This report; LEDGER row 05.01 COMPLETE; chunk-05 tracker |
| 12 Exit item | PASS (partial) | Gateway slice of the chunk-5 exit demo; eval framework (05.02) and cost dashboard (05.07) owed |

## 5. Tests
Named PIT **`aiGatewayGovernanceCoverage`** (fresh hospital; SYSTEM_ADMIN/DOCTOR/NURSE users): redacted chat logs `[QID]`/`[MRN]` with no raw digits, token split + `ops@N` prompt version + `chat` capability; doctor count allowed, nurse count 403 `AI_SCOPE_DENIED`; kill switch off → 409 → on → 200; cap 1 → 409 → restored; usage dashboard 4 capabilities with today's tokens and cap; priced model → `costUsd > 0` (agent model restored in `finally`); self-hosted model registered/removed.

Regression: `aiGatewayAndInteropJourney` green (FHIR/HL7/MLLP, chat/SSE, command intents, model create/delete).

## 6. Accessibility
Web surface untouched; repo-wide axe gate in CI.

## 7. Dual review — adversarial pass
1. **Redaction ordering** — email → MRN → QID → phone; the QID is masked before the phone pattern can eat 8-digit fragments.
2. **False positives** — the phone pattern needs 8 digits (with optional +974) and a digit boundary, so ordinary numbers in prose are left alone; names are deliberately kept (ops answers about them) and documented.
3. **Log privacy** — only the redacted text is persisted; the raw prompt never reaches the log or the provider.
4. **Cap accounting** — counts `tokensUsed` since UTC midnight; a single request can overshoot the cap (checked before dispatch), which is the standard hard-cut trade-off.
5. **Kill switch latency** — no cache: each request reads the policy row, so the flip applies immediately.
6. **Scope map completeness** — unknown count labels map to no permission (fail-open for informational counts); patient/encounter/appointment/queue/lab/invoice/workflow/task/analytics map explicitly. A new tool must be added to the map.
7. **Test isolation** — the PIT uses a hospital-scoped SYSTEM_ADMIN so caps/kill switch never leak into the primary hospital; the priced-model agent pin is restored in `finally`.
8. **Cost** — configured per-model rate, not provider billing; the 05.07 dashboard reconciles against invoices.

## 8. Deviations from contract
- OIDC client identity is the platform JWT identity (single service, no separate AI service account yet).
- Cost is model-rate based, not provider-invoice based (05.07).
- Command calls trace 0 tokens (no model call).
- Eval-gating (05.02) and the agent registry (05.07) are separate sections.

## 9. Session trail
- The exact `--tests` class filter failed to match; the wildcard form (`*aiGatewayGovernanceCoverage*`) found the test inside `OpdEmrCoreTests`.
- Docker Desktop dropped once; compose restarted, postgres healthy before the PIT run.
