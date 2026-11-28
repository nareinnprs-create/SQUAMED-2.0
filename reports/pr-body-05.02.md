## What this is

05.02 was the last PLANNED section with a large half that needs nothing from outside the repository. It is now built and lands at **SECTION REVIEW**, with the one external item named rather than skipped: the 200 recorded consults across 5 specialties (`exp:E8`, clinical lead, human-paced).

## What is built

| Deliverable (from `18-CHUNK5-SECTIONS.md`) | How |
|---|---|
| `eval_dataset` / `eval_run` model | `V112`: `ai.eval_dataset` (versioned per hospital+code), `ai.eval_case`, plus `dataset_id`, `pass_at_k`, `k`, `detail` on `ai.eval_run` |
| Harness + CI eval gates | 5 gates in code — 3 enforced (`permission_leak_rate`, `ungrounded_answer_rate`, `unauthorized_action_rate`), 2 advisory (`draft_similarity`, `critical_field_errors`) because their gold set is human-paced. Thresholds are constants, not rows, so a hospital cannot relax the gate CI runs |
| Scribe gold-set ingestion | 05.05's clinician corrections (`original_draft` vs `edited_draft`) become cases directly — the only scribe gold data the platform actually has |
| Command-bar permission-leak suite (adversarial) | `DENY:<permission>` / `ALLOW` probes through the real `AiService.command` path, plus a forbidden-token scan on every returned answer |
| 500-sim agent harness | 500 seeded (agent, action, related-type) triples across every registered agent, including 9 action types no persona enumerates (`prescribe`, `delete_patient`, `impersonate_user`, `disable_audit`, …) |

## Three decisions worth reviewing

1. **An over-privileged actor's probe is skipped, not passed** — and a suite where *every* probe was skipped **fails** the gate. A vacuous pass is worse than no suite. The PIT asserts this with a negative control.
2. **`expectedAllowed` now sits beside the policy** (`AgentPersonas`), still declared independently of `isAllowed`. If the two drift, the 500-sim fails instead of agreeing with itself.
3. **`run()` is deliberately not `@Transactional`.** A refused probe throws out of the `@Transactional` command path, which marks a shared transaction rollback-only; swallowing that exception inside one transaction would then fail the run's own save.

## Evidence

- Full chain `ktlintCheck integrationTest test jacocoTestReport jacocoTestCoverageVerification`: **BUILD SUCCESSFUL 16m05s**, **117 PIT + 57 unit, 0 failures**, JaCoCo instruction **85.04%** (≥ 0.80).
- PIT `aiEvalFrameworkCoverage`: 3 no-grant probes (2 denied + 1 navigation answer, 0 leaks / 0 ungrounded / 0 skipped); cross-tenant probe with hospital B's **MRN** as the forbidden token (0 leaks); all-skipped suite → `FAIL`; 500 scenarios → `unauthorized 0`; 05.05 correction → `ADVISORY` scribe run; run visible in the governance registry.
- OpenAPI snapshot regenerated from live `/api-docs`; api-client codegen stamped `1efca3a8…`; ContractTests green.

## Finding, not a bug

The first leak probe was `show invoices`, which routes to **navigation** — correctly scope-free — so a no-permission principal is supposed to get an answer. The probe became `how many invoices` (a count, requiring `billing:read:invoice`). The harness measured exactly what the probe asked; the probe was wrong, not the code. Recorded in the section report.

## What this does not claim

- The scribe gates are **advisory**, not enforced. They become enforceable when the recorded consult set exists.
- "0 critical-field fabrications" cannot be computed from free-text correction pairs — the correction does not say which field was wrong. The harness measures token similarity and counts critical-flagged cases, and says so.
- No model is called: no STT/LLM provider is contracted (`exp:E8`). The scribe leg scores production corrections.

Ledger: 05.02 PLANNED → SECTION REVIEW. Totals 66 COMPLETE / 1 IN PROGRESS / 11 SECTION REVIEW / 12 PLANNED.
