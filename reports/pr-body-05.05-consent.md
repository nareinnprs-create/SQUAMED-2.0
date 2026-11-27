## 05.05 — scribe consent gate + draft corrections as eval data

Two of 05.05's gaps that do not depend on the `exp:E8` STT/LLM provider contracts. The section stays **IN PROGRESS** — streaming, diarization, en/hi/Hinglish and the report-drafting half still need the provider work.

### Schema (`V111`, additive)
- `scribe.scribe_session.consent_id`, `recording_started_at`.
- `scribe.draft_correction` (session, original draft, edited draft, chars changed).

### Behaviour
- **Consent gate:** creating a scribe session for a patient now requires a granted `treatment` (or `data_sharing`) consent that belongs to that patient and has not expired — `SCRIBE_CONSENT_REQUIRED` / `SCRIBE_CONSENT_EXPIRED` otherwise. The consent id is stored on the session, so the recording is traceable to the artefact that authorised it.
- **Recording indicator:** the session view exposes `recording` (started and not yet signed) and the consent id.
- **Corrections as eval data:** a clinician edit of the AI draft writes a `draft_correction` row with the original and edited text and the number of characters between the shared prefix and suffix. Saving an unchanged draft writes nothing, so the eval set is not padded with no-op saves.
- **`GET /api/v1/scribe/sessions/{id}/corrections`** returns them, hospital-scoped.

### Evidence
- New PIT `scribeConsentCorrectionsCoverage`: no consent → 400 `SCRIBE_CONSENT_REQUIRED`; with a treatment consent → session records the consent id and `recording=false`; start-recording flips it true; draft → edit → identical re-save produces exactly **one** correction with `charsChanged > 0` and the original preserved; signing flips `recording` false; a second hospital is denied.
- Full chain: 116 PIT + 57 unit, 0 failures, JaCoCo 85.05%.

### Also fixed here: the unbounded FHIR list
The full chain kept failing on `GET /api/v1/fhir/Practitioner` (read timeout) because the list mapped every user before returning. FHIR list endpoints now take `_count` (default 100, max 500) and Patient/Practitioner page at the query (`PageRequest`) rather than after mapping, so the cost is bounded too. A PIT assertion pins `_count=1` → one entry.

### Still owed in 05.05
- Streaming STT, diarization, en/hi/Hinglish, and structured radiology/lab report drafting — all provider-dependent (`exp:E8`).
- Audio is transcribed in memory and never persisted, so the "30-day audio purge" has nothing to purge; that is a privacy property, not a missing job, and is recorded as such.
