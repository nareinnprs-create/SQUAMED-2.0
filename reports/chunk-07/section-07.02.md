# Section 07.02 — Voice operating interface (chunk 7)

**Verdict: COMPLETE → GATE-12 PASS (partial: 07.11 voice slice).** Gate-run assessment of the CURRENT tree @ PR #131. CI evidence: **run 45646897017 — all 7 gates green**.

## 1. Scope
Per `chunk-07-full-depth.md` item 2 / `20-CHUNK7-SECTIONS.md` 07.02: governed voice task execution (approve-by-voice for low-risk only; high-risk categories hard-excluded → approval UI), multilingual intent layer, screen-aware context, human escalation; removes the dictation-only limitation from chunk 5.

## 2. Key artifacts
**Migration `V101`**: `ai.voice_command` (transcript, locale, screen, intent, confidence, risk class, status, voice-approval flag, response, target/search, escalation, action draft).

**Backend**
- `ai/domain/VoiceCommand.kt`, `ai/repository/VoiceCommandRepository.kt`, `ai/service/AiVoice.kt`, `ai/api/AiVoiceController.kt` (4 endpoints under `/api/v1/ai/voice`).
- Reuses the 05.03 action-draft approval machinery for low-risk task drafts.

## 3. Gap closure
- ✔ Voice command parsing for navigate / search / create-task / help with en + hi keyword sets.
- ✔ Screen-aware navigation (same-screen response) and optional patient context field.
- ✔ Low-risk task drafts are voice-approvable; approval executes through the governed task service with the approver's scopes.
- ✔ High-risk categories (orders, medications, discharge, payments/refunds/invoices, cash collection, discounts) are hard-excluded from voice approval; approval returns 403.
- ✔ Unknown/low-confidence input escalates to a human with a recorded reason.
- ✔ Reject path and command history.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | `voiceOperatingInterfaceCoverage` green |
| 2 Zero-stub | PASS | CI run 45646897017 |
| 3 Types | PASS | CI run 45646897017 |
| 4 Lint + format | PASS | CI run 45646897017 |
| 5 Tests | PASS | CI run 45646897017; 105 PIT methods + 56 unit tests, 0 failures; JaCoCo instruction 84.82% (>=0.80) |
| 6 Accessibility | PASS | No UI change (voice is a client channel) |
| 7 Design system | PASS | No UI change |
| 8 Contracts | PASS | snapshot regenerated from live `/api-docs`; codegen stamped `73647fde…` |
| 9 Data | PASS | V101 additive; cross-tenant command list empty |
| 10 Audit | PASS | Controller-mediated writes; commands carry user |
| 11 Docs | PASS | This report; LEDGER; tracker |
| 12 Exit item | PASS (partial) | 07.11 voice slice |

## 5. Tests
Full suite: `integrationTest test jacocoTestReport jacocoTestCoverageVerification` — BUILD SUCCESSFUL, 105 PIT methods + 56 unit tests, 0 failures, JaCoCo instruction 84.82%.

Named PIT **`voiceOperatingInterfaceCoverage`**:
- "open laboratory" → EXECUTED navigate/laboratory; same-screen context says "already".
- Hindi "प्रयोगशाला खोलो" → navigate/laboratory.
- "find patient Ahmed" → EXECUTED search term.
- "create task Review critical result" → PENDING_APPROVAL with a draft; approve-by-voice → EXECUTED; second approve 409.
- Reject path → REJECTED with reason.
- "discharge patient now" → HIGH, voice approval blocked, approve 403; Hindi "दवा ऑर्डर करो" → HIGH.
- Unknown input → ESCALATED at confidence 0.40 with a reason; "help" executes.
- Cross-tenant list empty; blank transcript 400.

## 6. Accessibility
No UI change; voice is an additional channel. Keyboard path for approvals remains the existing task UI.

## 7. Dual review — adversarial pass
1. **High-risk exclusion** — clinical orders, meds, discharge, payments/refunds/invoices, cash collection and discounts can never be voice-approved.
2. **Governed execution** — low-risk approval runs `AiActionService.approve` (task service + approver scopes), not a direct write.
3. **Once-only decisions** — approve/reject require PENDING_APPROVAL (409 otherwise).
4. **Confidence + escalation** — unmatched input never executes; it escalates with a reason.
5. **Locale** — en + hi keyword sets; locale stored for audit.
6. **Tenant** — commands are hospital-scoped; cross-tenant list is empty.
7. **Audit** — commands carry the speaker, transcript, decision and draft link.

## 8. Deviations from contract
- The backend accepts a transcript; on-device/browser speech capture is the client channel (no audio streaming added).
- Intent parsing is deterministic keyword-based (en + hi), not an LLM intent model.
- Screen-aware context only prevents redundant navigation; it does not sync full UI state.
- Escalation is recorded; human call routing/paging is not part of this slice.
- No Arabic locale yet (locale field is free-form).

## 9. Session trail
- Local Docker compose restarted after an engine stop; named PIT green on the re-run.
