# Section 05.07 — Governance registry (chunk 5)

**Verdict: COMPLETE → GATE-12 PASS (partial: eval execution is 05.02, human-paced)**. Gate-run assessment of the CURRENT tree @ PR #105 (chunks 0–4 + 05.01 + 05.03 merged + this PR). CI evidence: **run 45207434040 — all 7 gates green**. Named PIT `aiGovernanceRegistryCoverage` green in the same run.

## 1. Scope
Per `chunk-05-ai-intelligence.md` §7: model/prompt/agent registries with scopes, eval results attached to releases, the incident form, and the cost dashboard per tenant per capability.

## 2. Key artifacts
**Migration `V87__ai_governance_registry.sql`** (additive; rollback note inside): `ai.agent_config.scopes`, `ai.eval_run`, `ai.incident`, cost index on `ai.interaction_log`.

**Backend:** `AiGovernance.kt` (eval/incident/cost services + views), `AiService.kt` (agent-scope enforcement), `AiController.kt` (governance endpoints, agent scopes in views), `AiEntities.kt` / `AiRepositories.kt`, `EventCatalog.kt` (`ai.incident_reported`, catalog 47).

**Web:** AI page Governance tab (costs, incidents + report form, evals).

## 3. Gap closure (from the gate-run report §7)
- ✔ No scopes on registries → `agent_config.scopes`, enforced against the caller at call time.
- ✔ No eval-on-release link → `eval_run` keyed by release subject, verdict/score computed, history per subject.
- ✔ No incident form → register with severity, lifecycle and mandatory resolution; event emitted.
- ✔ No cost dashboard → per-capability and per-model aggregation with a tenant total.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | PIT covers scopes, evals, incidents, costs |
| 2 Zero-stub | PASS | CI branch run 45207434040 |
| 3 Types | PASS | CI branch run 45207434040 (`tsc --noEmit` clean locally) |
| 4 Lint + format | PASS | CI branch run 45207434040 |
| 5 Tests | PASS | CI branch run 45207434040; named PIT below |
| 6 Accessibility | PASS (caveat) | Governance tab uses real controls with aria-labels; /ai page still outside axe `SCAN_ROUTES` |
| 7 Design system | PASS | @squamed/ui Tabs/Select/Input/Badge/Button |
| 8 Contracts | PASS | Snapshot `7fe99894…` + codegen committed; parity green |
| 9 Data | PASS | V87 additive; hospital-scoped eval/incident rows; cost index |
| 10 Audit | PASS | Registry writes controller-mediated; incidents carry reporter/decider; event with actor |
| 11 Docs | PASS | This report; LEDGER row 05.07 COMPLETE; chunk-05 tracker |
| 12 Exit item | PASS (partial) | Governance slice of the chunk-5 exit demo; eval harness (05.02) owed |

## 5. Tests
Named PIT **`aiGovernanceRegistryCoverage`** (fresh hospital): scoped agent allows a SYSTEM_ADMIN caller and denies a doctor `403 AI_SCOPE_DENIED`; eval PASS 100.00 / FAIL 80.00, history newest-first, impossible counts 400 `AI_EVAL_COUNTS_INVALID`; incident OPEN → INVESTIGATING → close without resolution 400 → CLOSED with timestamp → reopen 409 `AI_INCIDENT_CLOSED`; closed list; cost rows per capability and model; `ai.incident_reported` in the outbox.

Regressions: `aiGatewayGovernanceCoverage`, `aiCommandBarGroundingCoverage`, `eventFabricCoverage` (catalog 47).

## 6. Accessibility
Governance tab: labelled selects/inputs, buttons, tables with headers; status is text + badge. The page-level axe scan still excludes /ai.

## 7. Dual review — adversarial pass
1. **Scope semantics** — agent scopes are intersected with the caller's permissions, never granted; a scope the caller lacks is a 403 before any provider call.
2. **Eval honesty** — verdict is derived from counts (PASS only on a full pass); a caller cannot post a PASS with misses; history is append-only per subject.
3. **Incident lifecycle** — one-way to CLOSED, resolution mandatory, closed rows immutable; reopening is refused.
4. **Tenant isolation** — eval/incident lookups are hospital-scoped; a foreign incident id is 404.
5. **Cost correctness** — sums come from the trace rows (tokens/cost) in the window; model rows use the recorded model name.
6. **Empty states** — the dashboard renders zero rows without error for a fresh tenant.
7. **Scope string hygiene** — CSV is trimmed; blank entries ignored; an unknown permission simply fails the caller check (fail-closed).
8. **Event** — incident event carries capability + severity and the reporting actor.

## 8. Deviations from contract
- Eval execution (gold sets, harness) is 05.02 and remains human-paced (`exp:E8`); this section stores/attaches results.
- Cost is model-rate based (05.01 trace), not provider-invoice reconciliation.
- Prompt registry versioning is expressed as `agent@version` on the trace/eval subject rather than a separate prompt table.

## 9. Session trail
- Extension functions could not read the service's private repositories; the methods were folded back into `AiGovernanceService`.
- The eval-history query initially used an `@` in the query string (`%40`), which the test HTTP client mangled; subjects now use `-v0`.
- Docker Desktop dropped once; compose restarted before the PIT run.
