# Section 05.05 — AI Scribe + report drafting — gate-run report

> **Update 2026-11-27 (PR #159).** The assessment below is the 2026-11-14 gate run and is kept as history. Since then:
> - **Consent gate + recording indicator** are built: a patient session requires a granted `treatment`/`data_sharing` consent (patient match, expiry checked) and the consent id is stored on the session (V111).
> - **Draft corrections are eval data**: clinician edits are kept in `scribe.draft_correction` with original/edited text and characters changed; an unchanged re-save writes nothing. `GET /api/v1/scribe/sessions/{id}/corrections` is hospital-scoped.
> - **Audio is never persisted** - it is transcribed in memory - so the "30-day audio purge" has nothing to purge; that is a privacy property, not a missing job.
> - **Still owed (provider-gated, `exp:E8`)**: streaming STT, diarization, en/hi/Hinglish, structured radiology/lab report drafting, and the human-paced eval set.
> - Evidence: PIT `scribeConsentCorrectionsCoverage`; full chain 116 PIT + 57 unit, JaCoCo 85.05%.

Static read-only assessment. Baseline commit `886c51c`, CI run `43548016617` (all 6 CI gates green; merged instruction coverage 80.13%). No code, ledger, chunk file, or `.gate/` content changed by this report.

## 1. Scope
Chunk file §05.05: session record → **streaming** STT (en/hi/Hinglish) → diarization → SOAP **AI-draft** (`ai_generated` provenance) → clinician edit → signature converts to legal record; corrections captured as eval data; per-encounter consent flag + recording indicator; 30 d audio purge; **+ structured radiology/lab report drafting (SC-4)**. Depends on 05.01, 05.02. Present at baseline: 6-stage scribe machine + STT providers + sign/delete. **Streaming, diarization, languages, consent, purge, eval-data capture, radiology/lab absent.**

## 2. Built
- `backend/squamed-core/src/main/kotlin/com/squamed/core/scribe/service/ScribeService.kt` — 6-stage session machine (create → transcribe → draft → edit → sign → delete).
- `backend/squamed-core/src/main/kotlin/com/squamed/core/scribe/service/SpeechToTextProviders.kt` — Echo + OpenAI Whisper providers (config `squamed.ai.stt.provider`).
- `V28__scribe.sql` — `scribe.scribe_session`.
- `V111__scribe_consent_corrections.sql` - consent id + recording stamp on the session, `scribe.draft_correction` for clinician edits.
- `apps/web` scribe page (532 LOC).

## 3. Data/API/event contracts delivered
- Tables: scribe_session (stage, draft, consent ref, recording stamp, provenance `ai_generated`), scribe.draft_correction (original vs edited, chars changed).
- API: 6-stage lifecycle endpoints (create→…→sign converts draft to legal record; delete); audio upload drives STT stage. Consent is required for a patient session; `GET /sessions/{id}/corrections` returns the captured edits.
- Provider contract: STT provider switchable via config; Echo + Whisper-compatible.
- Events: none. Audio is transcribed in memory and never persisted, so there is no audio to purge; consent is checked at session creation.

## 4. Gates 1–12
| # | Gate | Result | Evidence |
|---|---|---|---|
| 1 | Functional completeness | **FAIL (PARTIAL)** | Present: 6-stage machine create→sign→delete, STT provider abstraction (Echo/Whisper), AI-draft with `ai_generated` provenance, sign→legal record. Missing: streaming STT, diarization (label-splitter only), en/hi/Hinglish handling, per-encounter consent flag + recording indicator, 30 d audio purge, corrections-as-eval-data (05.02 dependency), radiology/lab drafting (SC-4). |
| 2 | Zero-stub scan | PASS | CI run 43548016617 (gate 2 green); SC-9 allowlist. |
| 3 | Types | PASS | CI run 43548016617 (gate 3 green). |
| 4 | Lint + format | PASS | CI run 43548016617 (gate 4 green). |
| 5 | Tests | PASS | CI run 43548016617 (gate 5 green); merged instruction coverage 80.13% ≥ 0.80. |
| 6 | Accessibility | PASS | CI run 43548016617 (gate 6 green). Caveat: scribe page (532 LOC) **not** in axe `SCAN_ROUTES`. |
| 7 | Design system | PASS | CI run 43548016617 (gate 7 green). |
| 8 | Contracts | **FAIL** | Schema-registry harness not yet wired (00.02 scope). |
| 9 | Data | **FAIL** | RLS cross-tenant suite ABSENT until 00.04 (ADR-13) — tenant-scoped scribe tables. |
| 10 | Audit | PASS | Scribe lifecycle writes controller-mediated; `AuditAspect.kt:22-23` covers them. |
| 11 | Docs | PASS | This report re-checks chunk file §05.05; ledger untouched; gate-parity green. |
| 12 | Exit item | PASS | Named PIT slices: scribe leg of `aiGatewayAndInteropJourney` (6 stages create→sign→delete) + `scribeAudioUploadDrivesSttStage` + `queueAndScribeAndAiCoverage`, green in run 43548016617. |

## 5. Tests
- PIT: scribe leg of `aiGatewayAndInteropJourney`; `scribeAudioUploadDrivesSttStage`; `queueAndScribeAndAiCoverage`. Green in run 43548016617.
- Unit: `ScribePipelineTest` (9 tests), `ScribeSttTest` (3 tests). Green in run 43548016617.
- Coverage: merged instruction 80.13%.

## 6. Accessibility
axe green in CI; scribe page outside `SCAN_ROUTES` — not page-axe-verified; recording-indicator UI (consent affordance) itself missing.

## 7. Issues
- No consent flag / recording indicator — recording-legality (DPDP) gap — **open**.
- No 30 d audio purge job — retention compliance gap — open.
- Diarization is label-splitting, not speaker diarization — open.
- Corrections not captured as eval data (depends on unbuilt 05.02) — open.

## 8. External blockers
⛔ STT/LLM provider contracts (procurement = critical path per chunk file); Echo/Whisper-compatible config keeps build unblocked.

## 9. Deviations from chunk file
None logged; chunk file not edited.

## 10. Exit-demo slice
`gradlew.bat :squamed-core:integrationTest --tests "*PlatformIntegrationTests*scribeAudioUploadDrivesSttStage*" --tests "*PlatformIntegrationTests*aiGatewayAndInteropJourney*"` + `gradlew.bat :squamed-core:test --tests "*ScribePipelineTest*" --tests "*ScribeSttTest*"` — last run green in CI run 43548016617.
