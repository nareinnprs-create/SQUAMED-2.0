## 07.07 — Allied health + patient experience

Therapy referrals/sessions, contact-centre interactions with template agent-assist, international patient desk, and patient-experience feedback with NPS and governed follow-up tasks.

### Schema (`V102`, additive)
- `allied.therapy_referral`, `allied.therapy_session`.
- `cx.contact_interaction`, `cx.intl_case`, `cx.feedback`.
- Permissions `allied:read|write:therapy`, `cx:read|write:cx`; roles `THERAPIST`, `CONTACT_CENTRE_AGENT`, `PATIENT_EXPERIENCE_OFFICER`.

### Backend
- **`/api/v1/allied`:** referral (7 disciplines) → schedule → start → session draft → sign-once → completion (requires a signed session); care-plan/appointment ids stored on the referral.
- **`/api/v1/cx`:** interaction log with deterministic template suggested reply; close-once; international case open/close; feedback 0–10 with acknowledge/resolve and detractor follow-up task; NPS summary.

### Evidence
- New PIT `alliedAndPatientExperienceCoverage`: referral lifecycle incl. completion blocked before sign, schedule/sign/complete once (409); interaction template + close-once; intl case close-once; feedback promoter/detractor task, score bounds, resolve-once, NPS 0.00; cross-tenant lists empty.
- Also fixes two pre-existing defects surfaced by the full suite: (a) queue analytics read `LocalDate.now(UTC)` while queue writes use the server-local date, so `avgWaitMinutes` was null between 00:00–08:00 local; (b) `QueueController` passed the raw JWT hospital claim, null for SYSTEM_ADMIN, so token lookups spanned hospitals and hit `NonUniqueResultException` → 500. Both now use the same local-date basis / `TenantResolver.pinOrDefault` as the rest of the platform.
- OpenAPI snapshot regenerated, codegen stamped; api-client 5/5.

### Deviations
- Free-text session notes (no per-discipline structured forms).
- Care-plan/appointment links stored by id; aggregates not mutated here.
- Interaction log + template agent-assist; no CTI/email integration.
- International desk tracks case/interpreter; no visa/letter generator.
- No survey campaign engine; no UI changes.
