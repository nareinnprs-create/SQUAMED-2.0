# Section 07.07 — Allied health + patient experience (chunk 7)

**Verdict: SECTION REVIEW → local gates green (CI run pending).** Therapy referrals/sessions, contact-centre interactions with template agent-assist, international patient desk, and patient-experience feedback with NPS and governed follow-up tasks.

## 1. Scope
Per `chunk-07-full-depth.md` item 9 / `20-CHUNK7-SECTIONS.md` 07.07: physio/diet/OT/speech/rehab/social/psychology documentation + appointments + care-plan integration; contact centre (§43); international patient desk (§44); patient-experience/CRM full (§42) with agent-assist.

## 2. Key artifacts
**Migration `V102`**: `allied.therapy_referral`, `allied.therapy_session`, `cx.contact_interaction`, `cx.intl_case`, `cx.feedback`; `allied:read|write:therapy`, `cx:read|write:cx`; roles `THERAPIST`, `CONTACT_CENTRE_AGENT`, `PATIENT_EXPERIENCE_OFFICER`.

**Backend**
- `allied/` domain/repository/service/controller (8 endpoints under `/api/v1/allied`).
- `cx/` domain/repository/service/controller (12 endpoints under `/api/v1/cx`).

## 3. Gap closure
- ✔ Seven allied disciplines with referral → schedule → in-progress → signed sessions → completion.
- ✔ Completion requires at least one signed session; signing is once.
- ✔ Care-plan and appointment linkage by id on the referral.
- ✔ Contact-centre interaction log (phone/chat/email/walk-in, inbound/outbound) with deterministic template suggested reply (billing/appointment/report/complaint/other).
- ✔ International patient desk: country, language, interpreter flag, coordinator, open/close.
- ✔ Feedback 0–10 with NEW → ACKNOWLEDGED → RESOLVED; detractors (≤6) open a high-priority follow-up task; NPS summary.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | `alliedAndPatientExperienceCoverage` green |
| 2 Zero-stub | PASS | new files grep clean; CI |
| 3 Types | PASS | `compileKotlin` + `compileIntegrationTestKotlin` |
| 4 Lint + format | PASS | `ktlintCheck` clean |
| 5 Tests | PASS (local) | full chain BUILD SUCCESSFUL 10m37s — 106 PIT methods + 56 unit tests, 0 failures; JaCoCo instruction 84.78% (>=0.80) |
| 6 Accessibility | PASS | No UI change |
| 7 Design system | PASS | No UI change |
| 8 Contracts | PASS | snapshot regenerated from live `/api-docs`; codegen stamped `d73d7f38…` |
| 9 Data | PASS | V102 additive; cross-tenant lists empty |
| 10 Audit | PASS | Controller-mediated writes; records carry actor |
| 11 Docs | PASS | This report; LEDGER; tracker |
| 12 Exit item | PASS (partial) | 07.11 patient-experience slice |

## 5. Tests
Full suite: `integrationTest test jacocoTestReport jacocoTestCoverageVerification` — BUILD SUCCESSFUL, 106 PIT methods + 56 unit tests, 0 failures, JaCoCo instruction 84.78%.

Named PIT **`alliedAndPatientExperienceCoverage`**:
- Referral PHYSIO (discipline normalised), invalid discipline 400; schedule once (409 on repeat); start; session draft; completion blocked without a signed session; sign once (409); complete once (409); detail carries the session.
- Interaction opens with a billing-template reply; close once (409).
- Intl case open/close once (409).
- Feedback 9 → no task; feedback 3 → follow-up task; score 11 → 400; acknowledge → resolve once (409); summary total 2, promoters 1, detractors 1, NPS 0.00.
- Cross-tenant referrals/feedback lists empty.

## 6. Accessibility
No UI change.

## 7. Dual review — adversarial pass
1. **Clinical guard** — a referral cannot complete without a signed session.
2. **Once-only finals** — schedule, session sign, referral complete, interaction close, intl close and feedback resolve are each once (409).
3. **Discipline vocabulary** — only the seven allied disciplines are accepted.
4. **Feedback bounds** — score 0–10; detractor follow-up is a governed task, not an ad-hoc write.
5. **Agent-assist** — replies are template-governed strings; no clinical advice and no auto-send.
6. **Tenant** — every list/read is hospital-scoped.
7. **Audit** — records carry the actor; follow-up tasks ride the task engine.

## 8. Deviations from contract
- Seven fixed disciplines; no per-discipline structured assessment forms (free-text session notes).
- Care-plan and appointment links are stored by id; this section does not mutate those aggregates.
- Contact centre is an interaction log with template agent-assist; no telephony/CTI or email-channel integration.
- International desk tracks the case and interpreter flag; no visa/letter document generator.
- Feedback is NPS + follow-up tasks; no survey campaign engine.
- No UI changes (backend only).

## 9. Session trail
- Local Docker compose restarted after an engine stop; named PIT run after the restart.
- Gate 5 surfaced a pre-existing timezone defect: queue entries are written with the server-local date while queue analytics read `LocalDate.now(UTC)`; between 00:00–08:00 local the day keys diverge and `avgWaitMinutes` is null. Root-cause fix: queue analytics now reads the same server-local date basis as queue writes (`AnalyticsService`).
- Gate 5 also surfaced a queue token-lookup defect: `QueueController` passed the raw JWT hospital claim, which is null for SYSTEM_ADMIN, so a token lookup spanned every hospital and hit `NonUniqueResultException` (4 rows) → 500. Root-cause fix: all queue endpoints resolve the tenant with `TenantResolver.pinOrDefault`, matching the rest of the platform.
