## 07.01 — Six new agents + orchestrator

Adds Clinical, Pharmacy, Facilities, Procurement, Security and Executive personas plus a cross-domain orchestrator over governed platform state.

### Schema (`V99`, additive)
- Six `ai.agent_config` personas with scopes: `clinical`, `pharmacy`, `facilities`, `procurement`, `security`, `executive`.

### Backend
- **`POST /api/v1/ai/orchestrate`:** classifies a question (surgery/clinical/pharmacy/procurement/facilities/security/executive), consults the matching agents, ranks blockers by severity and returns blocker → owner → impact → recommended action. Executive questions consult all six and pick the highest-severity blocker.
- **Diagnostics (read-only governed state):** critical results + overdue discharges; pharmacy expiry/reorder; CSSD tray availability + work orders + assets; pending requisitions; high-severity incidents.
- **`POST /api/v1/ai/agents/{code}/eval`:** runs 10 persona probes and records an `ai.eval_run`; PASS only when unauthorized actions = 0.
- **Persona boundary:** `AgentPersonas` is shared by proposals and the eval harness; unknown agent codes are denied.

### Evidence
- New PIT `aiOrchestrationCoverage`: one blocker seeded per domain; 50 cross-domain questions with ≥90% primary-blocker accuracy (observed 50/50); executive question consults six agents and names the critical blocker + owner + action; nine personas each pass 10/10 with zero unauthorized actions; clinical agent cannot propose a PHARMACY task (400 `AI_AGENT_ACTION_NOT_ALLOWED`) while its own-domain proposal succeeds.
- OpenAPI snapshot regenerated (`cc8f5a82…`), codegen stamped; api-client 5/5.

### Deviations
- Routing is deterministic keyword + governed-state diagnostics, not LLM decomposition; §8 draft-only/approval/scope rules unchanged.
- Executive agent has no own source records; it synthesizes the other agents.
- Security agent reads the AI incident register (no dedicated security-operations incident module).
- No new event-catalog entries; eval runs/incidents already have cataloged events.
