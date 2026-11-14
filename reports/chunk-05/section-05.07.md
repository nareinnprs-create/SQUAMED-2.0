# Section 05.07 — Governance registry — gate-run report

Static read-only assessment. Baseline commit `886c51c`, CI run `43548016617` (all 6 CI gates green; merged instruction coverage 80.13%). No code, ledger, chunk file, or `.gate/` content changed by this report.

## 1. Scope
Chunk file §05.07: model/prompt/agent registries **with scopes**, eval results attached to releases, incident form, cost dashboard per tenant per capability. Depends on 05.01. Present at baseline: model/agent/tool registries + interaction log + model CRUD. **Scopes, eval-on-release, incident form, cost dashboard absent.**

## 2. Built
- `backend/squamed-core/src/main/kotlin/com/squamed/core/ai/domain/AiEntities.kt` — model_config, agent_config, tool_config, interaction_log.
- `backend/squamed-core/src/main/kotlin/com/squamed/core/ai/api/AiController.kt` — registry CRUD (model create/delete in PIT).
- `V27__ai.sql` — ai schema (4 tables); seeded `ops` agent.

## 3. Data/API/event contracts delivered
- Tables: model_config / agent_config / tool_config (no `scopes` column), interaction_log (trace).
- API: model create/delete, agent/tool config endpoints.
- Events: none (no incident event, no cost aggregation).

## 4. Gates 1–12
| # | Gate | Result | Evidence |
|---|---|---|---|
| 1 | Functional completeness | **FAIL (PARTIAL)** | Present: model/agent/tool registries with CRUD, interaction_log trace, seeded ops agent. Missing: scopes on registries, eval results attached to releases (05.02 dependency), incident form, cost dashboard per tenant per capability. |
| 2 | Zero-stub scan | PASS | CI run 43548016617 (gate 2 green); SC-9 allowlist. |
| 3 | Types | PASS | CI run 43548016617 (gate 3 green). |
| 4 | Lint + format | PASS | CI run 43548016617 (gate 4 green). |
| 5 | Tests | PASS | CI run 43548016617 (gate 5 green); merged instruction coverage 80.13% ≥ 0.80. |
| 6 | Accessibility | PASS | CI run 43548016617 (gate 6 green). Caveat: ai page not in axe `SCAN_ROUTES`. |
| 7 | Design system | PASS | CI run 43548016617 (gate 7 green). |
| 8 | Contracts | **FAIL** | Schema-registry harness not yet wired (00.02 scope). |
| 9 | Data | **FAIL** | RLS cross-tenant suite ABSENT until 00.04 (ADR-13) — tenant-scoped ai tables. |
| 10 | Audit | PASS | Registry writes controller-mediated; `AuditAspect.kt:22-23` covers them. |
| 11 | Docs | PASS | This report re-checks chunk file §05.07; ledger untouched; gate-parity green. |
| 12 | Exit item | PASS | Named PIT slice `aiGatewayAndInteropJourney` registry leg (model create/delete, ops agent, tools ≥5) green in run 43548016617. |

## 5. Tests
- PIT: `aiGatewayAndInteropJourney` — ops agent + tools ≥5, model create/delete. Green in run 43548016617.
- Unit: `AiIntentServiceTest` (agent/capability surface). Green in run 43548016617.
- Coverage: merged instruction 80.13%.

## 6. Accessibility
axe green in CI; ai page outside `SCAN_ROUTES`.

## 7. Issues
- No `scopes` field on agent/tool registries → effective-scope enforcement (05.01 gap) cannot be expressed — **open** (root shared with 05.01).
- No eval-results-on-release link (blocked by absent 05.02 harness) — open.
- No incident form or cost dashboard — open.

## 8. External blockers
None vendor-gated; eval linkage blocked internally by 05.02 (gold-set recording is human-paced).

## 9. Deviations from chunk file
None logged; chunk file not edited.

## 10. Exit-demo slice
`gradlew.bat :squamed-core:integrationTest --tests "*PlatformIntegrationTests*aiGatewayAndInteropJourney*"` — last run green in CI run 43548016617.
