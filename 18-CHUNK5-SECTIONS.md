# CHUNK 5 — SECTION GRAPH (AI intelligence)
Ledger source for wave 6. Est. in **agent-plan weeks** (~8 wk total; the gold-set recordings are human-paced — the only wave where humans are on the critical path). Contract: `chunk-05-ai-intelligence.md`.

| # | Section | Contents | Depends on | Est. | Notes |
|---|---|---|---|---|---|
| 05.01 ⛔ | AI gateway | OIDC client identity, effective-scope enforcement, prompt redaction layer, provider router (cloud/India-region **+ custom/self-hosted OpenAI-compatible endpoints as first-class targets**, §8 rule 8), token budgets, daily tenant caps **hard-cut at 100%**, full trace (model/prompt version/tokens/cost/latency), kill switch <1 min | chunk 4 exit | 1.5 | Dual review (AI + security) |
| 05.02 ⛔ | Eval framework | `eval_dataset`/`eval_run` model, harness + CI eval gates, scribe gold-set ingestion pipeline, command-bar permission-leak suite (incl. adversarial probes), 500-sim agent harness | 05.01 | 1.0 | ⛔ external: 200 recorded consults across 5 specialties — clinical lead, human-paced |
| 05.03 | Command bar (⌘K) | Intent routing: navigation / permission-aware retrieval with citations (grounding mandatory) / action-drafting → approval UI; no ungrounded clinical answers possible | 05.01 | 1.5 | |
| 05.04 | Intelligence rail | Attention items from governed events (critical unacked, discharge blockers, OT delays, denials), act/inspect wired to real workflows, fatigue caps per role/shift, dismissal tracking | 05.03 | 0.5 | |
| 05.05 ⛔ | AI Scribe + report drafting | Session record → streaming STT (en/hi/Hinglish) → diarization → SOAP **AI-draft** (`ai_generated` provenance) → clinician edit → signature converts to legal record; corrections captured as eval data; per-encounter consent flag + recording indicator; 30 d audio purge; **+ structured radiology/lab report drafting from findings dictation (same machinery, SC-4)** | 05.01, 05.02 | 2.0 | ⛔ external: STT/LLM provider contracts (procurement = critical path) |
| 05.06 | Three production agents | Operations (discharge blockers, bed turnover, OT-delay reports), Revenue (integrity triage drafts, denial clustering, doc-gap nudges), Patient-comms (template-governed, consent-checked, no free-form advice) — propose-only, execution via governed APIs | 05.03 | 1.0 | |
| 05.07 | Governance registry | Model/prompt/agent registries with scopes, eval results attached to releases, incident form, cost dashboard per tenant per capability | 05.01 | 0.5 | |
| 05.08 ⛔ | **Chunk exit** | Demo scenario, scribe eval (≥95% gold facts, **0 critical-field fabrications**), leak suite 0 leaks, agent sim unauthorized-action 0, cap-trigger + kill-switch drill (60 s), per-doctor opt-in rollout plan, dual review | all | 0.5 | |

**Lanes (≈8 wk):** (05.01→05.02→05.05) · (05.03→05.04→05.06) · (05.07) · exit last. Raw sum 7.5 wk; gold-set recording and provider procurement pace the wave, not code.
