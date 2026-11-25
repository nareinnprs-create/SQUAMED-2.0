## 07.02 — Voice operating interface

Governed voice commands with multilingual intent parsing, screen-aware context, low-risk voice approval and hard-excluded high-risk categories.

### Schema (`V101`, additive)
- `ai.voice_command`: transcript, locale, screen, patient, intent, confidence, risk class, status, voice-approval flag, response, target/search, escalation, action-draft link.

### Backend (`/api/v1/ai/voice`)
- **Command:** parses navigate / search / create-task / help (en + hi); screen-aware same-screen response.
- **Low-risk:** navigation/search/help execute; task drafts are voice-approvable and execute through the governed task service with the approver's scopes.
- **High-risk:** orders, medications, discharge, payments/refunds/invoices, cash collection and discounts are hard-excluded from voice approval (403), approval UI only.
- **Escalation:** unmatched/low-confidence input escalates with a reason; reject path recorded.

### Evidence
- New PIT `voiceOperatingInterfaceCoverage`: navigate + screen-aware + Hindi navigate; search term; task draft approved by voice (409 on repeat) and reject path; discharge/Hindi medication → HIGH + 403; unknown → ESCALATED 0.40; help executes; cross-tenant list empty; blank transcript 400.
- OpenAPI snapshot regenerated, codegen stamped; api-client 5/5.

### Deviations
- Backend accepts a transcript; browser speech capture is the client channel.
- Deterministic keyword intent parsing (en + hi), not an LLM intent model.
- Screen-aware context prevents redundant navigation only.
- Escalation is recorded; human call routing/paging is out of scope.
- No Arabic locale yet (locale field free-form).
