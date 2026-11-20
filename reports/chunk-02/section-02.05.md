# Section 02.05 — RIS-lite + imaging (chunk 2)

**Gate-run assessment of the CURRENT tree** @ PR #78 (merged baseline + wiring PRs #2–#11 + Wave-3 sections 02.01/02.02 via #66 + 02.03 via #70 + 02.04 via #72 + 02.06 via #68 + 02.07 via #74 + 02.08 via #76 + this PR). Verdict after verification: **COMPLETE → GATE-12 PASS (partial: exit-demo slice owed at 02.09 chunk exit)**. CI evidence: **run 44699529138 — all 7 gates green** (docs commit on the same branch also green). Named PIT `radiologyRisLiteCoverage` green in the same run.

## 1. Scope
Per `15-CHUNK2-SECTIONS.md` 02.05: Orthanc MWL provider, study completion, radiologist worklist, structured report editor + coding, OHIF embedded (scoped tokens), critical-findings escalation, Patient 360 attach. Buy-list integration: **PACS = Orthanc, viewer = OHIF** — no DICOM storage or rendering is built here.

## 2. Key artifacts
**Migration `V70__radiology_ris_lite.sql`** (additive; rollback note inside):
- `radiology.accession_seq` + `rad_order` (accession `RAD-yyyyMMdd-NNNN` from the sequence, modality, body part, priority, status machine ordered→scheduled→in_progress→completed→reported→signed), `study` (unique study instance UID, Orthanc study id, series count), `report` (report_version, template, findings/impression, ICD/SNOMED CSV, sign, amends_report_id + amendment_reason; **unique `(order_id, report_version)`**), `critical_finding` (severity, SLA, OPEN|ACKNOWLEDGED, ack trail).
- Permissions `radiology:read:worklist`, `order:study`, `draft:report`, `sign:report`, `ack:critical` → RADIOLOGIST + DOCTOR (doctor without sign/ack) + SYSTEM_ADMIN.

**API (12 endpoints):**
- Orders + MWL feed (`GET /radiology/mwl?modality=` — the JSON feed Orthanc's MWL channel consumes: accession, modality, body part, priority, patient name).
- Study completion with Orthanc refs → `StudyCompleted` + `billing.chargeable_performed` (`chargeCategory: IMAGING`, accession + modality/body part as the charge line).
- Radiologist worklist with study + report state.
- Structured draft (`POST /orders/{id}/reports`, `PUT /reports/{id}`) with template + ICD/SNOMED codes; one open draft per order (`RAD_REPORT_DRAFT_EXISTS`).
- Sign (radiologist) → `RadiologyReportSigned`; amendments are new signed versions pointing at the original with a mandatory reason (`AMENDMENT_REASON_REQUIRED`).
- Critical findings: raise on a signed report, SLA minutes, read-evaluated escalation (≥2× SLA → cmo, ≥SLA → dept_head — 02.03 parity), ack with note, double-ack rejected.
- Viewer token: 5-minute JWT scoped to one study (`typ=viewer`, `studyUid`); `parseAccessToken` now rejects viewer tokens so they can never authenticate API calls.
- OpenAPI snapshot regenerated (sha256 `a408b879…`) + codegen stamped.

**Events:** catalog 24 → 26 — `StudyCompleted` (`radiology.study_completed`), `RadiologyReportSigned` (`radiology.report_signed`), both EMITTED; the imaging charge event closes the 02.08 imaging deviation.

**Patient 360:** `Patient360View.imaging` — per order: accession, modality, body part, status, study UID, latest signed report version + impression.

**Web:** `/radiology` workspace (nav after Laboratory) — worklist, study completion, report form (template/findings/impression/ICD/SNOMED), sign, critical finding raise/ack, OHIF embed through the scoped token with a metadata/token fallback when `squamed.radiology.ohif-url` is unset.

## 3. Gap closure
- ✔ Modality worklist feed (Orthanc MWL channel) with sequence accessions
- ✔ Study completion with Orthanc refs + imaging charge hook
- ✔ Radiologist worklist with study/report state
- ✔ Structured report editor + ICD/SNOMED coding, sign, versioned amendments
- ✔ Critical-findings escalation with SLA + ack trail
- ✔ OHIF embedding via study-scoped short-lived tokens
- ✔ Patient 360 imaging attach
- ✔ PIT 02.05: `radiologyRisLiteCoverage` PASS

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | PIT walk below covers every contract item |
| 2 Zero-stub | PASS | CI branch run 44699529138 |
| 3 Types | PASS | CI branch run 44699529138 |
| 4 Lint + format | PASS | CI branch run 44699529138 |
| 5 Tests | PASS | CI branch run 44699529138; named PIT below |
| 6 Accessibility | PASS | CI branch run 44699529138 (axe 0 serious/critical; new route follows the pharmacy/laboratory precedent — not in the fixed scan list) |
| 7 Design system | PASS | CI branch run 44699529138 (existing `@squamed/ui` primitives only) |
| 8 Contracts | PASS | Snapshot regenerated + committed (LF); codegen stamp a408b879…; api-client parity green |
| 9 Data | PASS | V70 additive + rollback note; accession from a DB sequence; unique study UID + `(order_id, report_version)`; RLS stage-2 remains repo-wide ADR-13 debt |
| 10 Audit | PASS | Order/study/report/critical rows carry actor + timestamps; sign and amendment record signer + reason; events carry the stamped envelope |
| 11 Docs | PASS | This report; LEDGER row 02.05 COMPLETE; tracker updated |
| 12 Exit item | PASS (partial) | Imaging slice of the 02.09 exit demo (order → study → report → critical ack → viewer token); this section supplies the PIT evidence |

## 5. Tests
Named PIT **`radiologyRisLiteCoverage`** (new): hospital-scoped RADIOLOGIST + DOCTOR registered; patient + encounter; CT chest order → accession `RAD-…` + MWL row (patient name, urgent priority); study completion (uid, orthanc id, 3 series) → `StudyCompleted` and `billing.chargeable_performed` (`IMAGING`) in the outbox; worklist `status=completed` shows the order; draft report with ICD `R05` + SNOMED `267036007`; second open draft → 400 `RAD_REPORT_DRAFT_EXISTS`; DOCTOR sign → 403; RADIOLOGIST sign → v1 + signer + `RadiologyReportSigned`; amendment blank reason → 400 `AMENDMENT_REASON_REQUIRED`; amendment → v2 + `amendsReportId` + reason; critical finding OPEN with level 0 + ack note + double-ack 400; viewer token claims (`typ=viewer`, `studyUid`, 300 s TTL, `expiresInSeconds`); Patient 360 `imaging` row carries the accession, study UID and signed v2; catalog entries `StudyCompleted`/`RadiologyReportSigned` EMITTED.

Regression legs re-run green: `eventFabricCoverage` and `consentNotificationCoverage` (catalog 26).

## 6. Accessibility
Repo-wide axe gate green (PR #78 branch run); the new page uses existing UI primitives and semantic headings; it is not part of the fixed axe/VRT route list (same as pharmacy/laboratory).

## 7. Dual review — adversarial pass
1. **Accession wrap:** sequence is global; `seq % 10000` inside a date can only collide at 10k studies in one day (UNIQUE violation → 500). Accepted; per-day sequence reset is a deployment nicety.
2. **MWL feed auth:** Orthanc must call with a service account holding `radiology:read:worklist`. Deployment configuration; the endpoint is permission-gated.
3. **Study completion idempotency:** status guard rejects a second completion (`RAD_ORDER_ALREADY_COMPLETED`); a duplicate study UID from another order hits the unique constraint (500 rather than a clean 409) — rare, no data corruption.
4. **Charge event single-fire:** completion is the only emission point and the status machine prevents re-entry.
5. **Report drafts:** one open draft per order enforced (`RAD_REPORT_DRAFT_EXISTS`); drafts can be edited freely, signing freezes the version.
6. **Amendment chain:** every amendment points at the original signed report (not the previous amendment) and versions increment monotonically; the unique `(order_id, report_version)` blocks concurrent duplicate versions.
7. **Sign permission:** DOCTOR is deliberately excluded from `radiology:sign:report`/`ack:critical` in V70; SYSTEM_ADMIN keeps all-access (platform admin).
8. **Critical escalation:** read-evaluated (no persisted escalation rows) — 02.03 parity; ack is a single flip with a note, no append-only trail (unlike lab critical results). Named for chunk-4 if radiology needs the full ack trail.
9. **Viewer token:** signed with the platform JWT key, 5-minute TTL, study-scoped claims, and rejected by the API access-token parser. OHIF-side validation/config and single-use semantics are deployment concerns.
10. **Patient 360:** imaging query is per order capped at 20 (small N); null-hospital admin reads pass the 00.04 tenant convention.
11. **Cross-test interference:** accessions and study UIDs are unique per run; the PIT registers its own users; catalog counts updated in both existing assertions.
12. **Null-hospital order creation:** `pinOrDefault` falls back to the primary hospital for an unpinned admin — consistent with lab order creation.

## 9. Deviations from contract
- **DICOM MWL is a JSON feed**, not a DICOM SCP: Orthanc's MWL channel is deployment configuration (ADR-14 precedent — buy-list integration, not builds). The feed carries the accession + patient + modality fields the channel needs.
- **Orthanc and OHIF containers are not part of CI compose**; the PIT asserts the RIS side (order → feed → study refs → report → token) and the viewer URL is config-driven.
- **No radiology critical-findings event** in the chunk event list — the escalation stays internal with SLA + ack, matching the 02.03 read-evaluated model.
- Structured templates are a `template_code` + free-text findings/impression (template library management is not in this chunk).

## 8. Session trail
- Local PIT round 1 blocked twice by Docker Desktop deaths (context-load failures); restarted each time.
- Flyway checksum mismatch after the V69 comment edit (zero-stub fix landed post-apply) → dev-DB row reset, re-applied.
- Dual-review hardening before CI: duplicate-draft guard + unique `(order_id, report_version)`; PIT leg added for the draft guard; V70 row reset and re-applied.
- CI run 44698936249 was cancelled by the hardening push; run 44699529138 (26feae9) all 7 gates green.
- Commit briefly landed on `main` (branch not created first) — moved to `build/chunk-02-ris-lite`, main reset to origin.
