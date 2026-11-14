# Section 05.01 — AI gateway — gate-run report

Static read-only assessment. Baseline commit `886c51c`, CI run `43548016617` (all 6 CI gates green; merged instruction coverage 80.13%). No code, ledger, chunk file, or `.gate/` content changed by this report.

## 1. Scope
Chunk file (`18-CHUNK5-SECTIONS.md` §05.01): OIDC client identity, effective-scope enforcement, prompt redaction layer, provider router (cloud/India-region **+ custom/self-hosted OpenAI-compatible endpoints as first-class targets**), token budgets, daily tenant caps **hard-cut at 100%**, full trace (model/prompt version/tokens/cost/latency), kill switch <1 min. Present at baseline: provider router + registries + trace table. **Identity, scopes, redaction, budgets/caps, kill switch absent.**

## 2. Built
- `backend/squamed-core/src/main/kotlin/com/squamed/core/ai/api/AiController.kt` — chat/SSE/model/agent/tool/command endpoints.
- `backend/squamed-core/src/main/kotlin/com/squamed/core/ai/service/AiService.kt` — gateway orchestration.
- `backend/squamed-core/src/main/kotlin/com/squamed/core/ai/service/AiProviders.kt` — Echo + OpenAI-compatible providers; **custom/self-hosted LOCAL endpoints first-class** (config `squamed.ai.*`).
- `backend/squamed-core/src/main/kotlin/com/squamed/core/ai/domain/AiEntities.kt` — model_config, agent_config, tool_config, interaction_log.
- `V27__ai.sql` — ai schema (4 tables).

## 3. Data/API/event contracts delivered
- Tables: model_config / agent_config / tool_config / interaction_log.
- API: chat + SSE streaming (token+done events), model create/delete, agent/tool config endpoints.
- Provider contract: Echo default; any OpenAI-compatible base URL incl. self-hosted.
- Trace: interaction_log rows; **tokensUsed always null** (no usage parsing).

## 4. Gates 1–12
| # | Gate | Result | Evidence |
|---|---|---|---|
| 1 | Functional completeness | **FAIL (PARTIAL)** | Present: provider router (Echo + OpenAI-compatible, self-hosted first-class), SSE streaming, model/agent/tool registries, interaction_log trace rows. Missing: OIDC client identity, effective-scope enforcement, prompt redaction layer, token budgets + daily tenant cap hard-cut, kill switch <1 min, full trace (tokens/cost null). |
| 2 | Zero-stub scan | PASS | CI run 43548016617 (gate 2 green); SC-9 allowlist. |
| 3 | Types | PASS | CI run 43548016617 (gate 3 green). |
| 4 | Lint + format | PASS | CI run 43548016617 (gate 4 green). |
| 5 | Tests | PASS | CI run 43548016617 (gate 5 green); merged instruction coverage 80.13% ≥ 0.80. |
| 6 | Accessibility | PASS | CI run 43548016617 (gate 6 green). Caveat: ai page (1012 LOC) **not** in axe `SCAN_ROUTES`. |
| 7 | Design system | PASS | CI run 43548016617 (gate 7 green). |
| 8 | Contracts | **FAIL** | Schema-registry harness not yet wired (00.02 scope). |
| 9 | Data | **FAIL** | RLS cross-tenant suite ABSENT until 00.04 (ADR-13) — tenant-scoped ai tables. |
| 10 | Audit | PASS | AI writes (model/agent/tool config, interactions) controller-mediated; `AuditAspect.kt:22-23` covers them. |
| 11 | Docs | PASS | This report re-checks chunk file §05.01; ledger untouched; gate-parity green. |
| 12 | Exit item | PASS | Named PIT slice `aiGatewayAndInteropJourney` gateway leg (:889-1038) green in run 43548016617. |

## 5. Tests
- PIT: `aiGatewayAndInteropJourney` :889-1038 — ECHO default provider, ops agent + tools ≥5, model create/delete, chat, SSE token+done. Green in run 43548016617.
- Unit: `AiIntentServiceTest` (10 tests — gateway-adjacent intent layer). Green in run 43548016617.
- Coverage: merged instruction 80.13%.

## 6. Accessibility
axe green in CI; ai page outside `SCAN_ROUTES` — not page-axe-verified.

## 7. Issues
- `tokensUsed` always null → cost/latency trace incomplete (root: provider usage field not parsed) — **open**.
- No tenant-cap hard-cut or kill switch — security dual-review item open (chunk file demands both).
- No prompt redaction before provider dispatch — open (PHI-to-provider risk).

## 8. External blockers
None hard: Echo default + self-hosted OpenAI-compatible config keep build unblocked; cloud provider contracts (procurement) remain open for production keys.

## 9. Deviations from chunk file
None logged; chunk file not edited.

## 10. Exit-demo slice
`gradlew.bat :squamed-core:integrationTest --tests "*PlatformIntegrationTests*aiGatewayAndInteropJourney*"` — last run green in CI run 43548016617.
