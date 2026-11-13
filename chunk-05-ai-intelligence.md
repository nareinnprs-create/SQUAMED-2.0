# CHUNK 05 — AI INTELLIGENCE (governed)
**Status:** PLANNED · **Window:** Mar → Jun 2028 (14 wk) · **Depends on:** chunk 04 exit
**Stack entering:** `services/ai-gateway`, model provider contracts (India-region/zero-retention), STT provider, eval harness
**Baseline (SC-6):** `master` prior build has a scribe module (pipeline, STT providers, entities, tests) and an `ai/` module (intent service, providers) — enter gates on existing code after the chunk-0 baseline audit; §8 rule 8 (custom providers) and eval-gating still apply in full.

## GOAL
The AI layer of blueprint §56–63 **with contracts**: scribe, command bar, intelligence rail, 3 production agents — every capability eval-gated, permission-bound, kill-switched.

## IN SCOPE
1. **AI gateway:** single entry point per §8 contract — OIDC client identity, effective-scope enforcement, prompt redaction layer, provider router (cloud/India-region, cost/latency/privacy routing per task), **mandatory custom-provider support**: any OpenAI-compatible endpoint (base URL + key + model), self-hosted models, per-tenant/per-reseller provider contracts — first-class routing targets with identical tracing, caps and kill switch (§8 rule 8), token budgets & daily tenant caps, full trace logging (model, prompt version, tokens, cost, latency), kill switch per capability.
2. **Universal command bar (⌘K "Ask Squamed"):** intent routing: navigation (go to screen), retrieval (permission-aware search + Patient-360 Q&A with citations), action-drafting (prepare discharge, create task — always as **draft proposal → approval UI**). Ungrounded answers impossible: clinical/operational answers must cite record references (§8 rule 3).
3. **Intelligence rail:** contextual side panel (blueprint §90 pattern) — attention items assembled from governed events (critical results unacked, discharge blockers, OT delays, claim denials) with act/inspect buttons wired to real workflows.
4. **AI Scribe:** ambient consult recording → streaming STT (medical vocabulary, en/hi/Hinglish) → diarization → structured SOAP draft into the encounter note as **AI-draft** (blueprint provenance tags: `ai_generated` fields rendered distinctly) → clinician edit → **signature converts draft to legal record**; corrections captured as eval data (blueprint §14 honored verbatim). No live recording without per-encounter patient consent flag (chunk 0 consent engine).
5. **Permission-aware RAG:** retrieval over hospital knowledge (policies, protocols, formulary) + patient record — retrieval executes with the user's scopes via the same ABAC layer (§8 rule 1), never raw DB.
6. **Three production agents (not nine — fix for blueprint gap):**
   - **Operations Agent:** discharge-blocker detection, bed-cleaning/turnover task synthesis, OT-delay blocker reports (blueprint §59 pattern, scoped).
   - **Revenue Agent:** integrity-finding triage drafts, denial-reason clustering, documentation-gap nudges to doctors (drafts only).
   - **Patient-comms Agent:** appointment reminders, report-ready notifications, follow-up messages — template-governed, consent-checked, no free-form clinical advice.
7. **AI governance registry:** model registry, prompt/version registry, agent registry with scopes, eval results attached to releases, incident reporting form, cost dashboard per tenant per capability (blueprint §60 complete).
8. **Eval framework (§8 rule 5):** scribe eval set (≥200 recorded consults across 5 specialties, transcribed + clinician-gold), command-bar eval (retrieval precision/permission-leak suite incl. adversarial "ignore instructions" probes), agent eval (task success, unauthorized-action rate = 0 tolerance).
9. **AI report drafting:** structured radiology/lab report draft from findings dictation + result context, same machinery as scribe (STT → structured draft → `ai_generated` provenance → clinician edit → signature converts to legal record), eval-gated with specialty gold sets; fills the chunk-2 deferral.

## OUT OF SCOPE
Imaging AI integration framework → chunk 8 (marketplace-based). Prescriptive clinical decision-support → chunk 7 with regulatory gate. Voice task execution + remaining 6 agents → chunk 7. Agent SDK/marketplace → chunk 8.

## DATA MODEL (key additions)
`ai_request(trace, cost, model, prompt_version), ai_draft(artifact ref, provenance, corrections), eval_dataset, eval_run(scores), agent_registration(scopes, owner), ai_incident, provider_contract(endpoints, retention terms), attention_item(rail)`

## APIS
- `/ai/command:ask|draft-action` (streaming), `/ai/rail:items`, `/ai/reports/{studyId}:draft`
- `/ai/scribe/sessions:start|stop|draft|finalize`
- `/ai/agents/{id}:propose` (agents only propose; execution always through governed platform APIs)
- `/governance/models|prompts|agents|incidents|costs` (admin/auditor)

## EVENTS
`AiScribeSessionStarted/Completed, AiDraftCreated, AiDraftSigned(with corrections), AiActionProposed/Approved/Rejected/Expired, AiIncidentReported, CostCapReached, AttentionItemRaised/Cleared`

## PERMISSIONS (adds)
All AI permissions **inherit the invoking user** (ABAC); agent service accounts get narrow named scopes; auditor sees full AI trace incl. cost; admin: registry, kill switch, caps.

## SAFETY / COMPLIANCE
- §8 contract fully enforced: no direct clinical-record writes, grounding mandatory, approvals human, kill switch < 1 min, zero-retention/DPA provider contracts verified in writing.
- Scribe consent flag per encounter; recording indicator on consult UI; retention 30 days for audio, then purge (configurable ≤90 d).
- Redaction layer strips direct identifiers from prompts where task allows (e.g., ops agent works on encounter IDs, not names).
- Prompt-injection defense: tool calls originate from server-side allowlist; untrusted text (transcripts, notes) never concatenated into tool-authorizing instructions.
- Alert fatigue: rail items capped per role per shift; dismissal reasons tracked.

## TESTS
- Scribe eval: completeness ≥95% of gold facts, **0 critical-field fabrications** (allergy, dose, laterality) on eval set; correction-rate tracked per doctor (improvement metric).
- Command bar: permission-leak suite (cross-dept, cross-tenant, agent-impersonation prompts) — 0 leaks; citation correctness ≥95%.
- Agents: unauthorized-action rate 0 across 500-sim; proposal expiry works (no stale auto-approvals).
- Cost caps: simulated spend spike → cap triggers → capability flag off → audited.
- Kill-switch drill: 60 s from decision to disabled feature flag, in staging, timed.

## EXIT CRITERIA (demo scenario)
Doctor dictates a consult → SOAP draft in <60 s → edits one medication (correction captured) → signs with `ai_generated` provenance visible → manager asks command bar "why is OT 3 delayed?" → cited answer naming the blocker (missing crossmatch) with an Operations-Agent proposal to task blood bank → manager approves in approval UI → task created → rail shows the item resolving → auditor opens AI trace: model, cost, prompts, approvals all visible → kill switch exercised on a dummy capability in <60 s.

## ROLLOUT / ROLLBACK
Every AI capability behind a flag, enabled per-doctor opt-in first, pilot cohort 2 weeks before general enable. Rollback = flag off; scribe data (signed notes) persists as normal records.

## OPEN DECISIONS
- STT + LLM default providers under India-region/zero-retention terms (requests out week 1 — procurement lead time is the chunk's critical path). **Custom/self-hosted providers are mandatory routing targets regardless of defaults (§8 rule 8)** — sovereign/on-prem deployments (chunk 8) will require them.
- Recording hardware/mic standard for consult rooms.
