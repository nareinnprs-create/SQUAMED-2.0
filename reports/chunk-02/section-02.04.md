# Section 02.04 — Lab reports + delivery (chunk 2)

**Gate-run assessment of the CURRENT tree** @ PR #72 (merged baseline + wiring PRs #2–#11 + Wave-3 sections 02.01/02.02 via #66 + 02.03 via #70 + 02.06 via #68 + this PR). Verdict after verification: **COMPLETE → GATE-12 PASS**. CI evidence: **run 44569047321 — all 7 gates green** (zero-stub, parity, source-tree, backend ktlint+test+ArchUnit, integration compose+PIT+coverage, frontend, a11y/perf). Named PIT `labReportsDeliveryCoverage` green in the same run.

## 1. Scope
Per `15-CHUNK2-SECTIONS.md`: branded PDF, age/sex reference ranges, amendments versioned, delivery counter/app/notification, TAT analytics. Depends on 02.02 (released lab orders drive publishing; report rows amend released results) and 02.03 (amended values re-run critical detection).

## 2. Key artifacts
**Migration `V66__lab_reports_delivery.sql`** (additive; rollback note inside):
- `laboratory.lab_reference_range` — hospital + test_code + sex (`M|F|ANY`) + age window (`age_min_years`/`age_max_years`) + `range_text`, `is_active`.
- `laboratory.lab_report` — `report_version INTEGER NOT NULL` (deliberately **not** the BaseEntity `version` optimistic-lock column), `storage_key`, `published_by/at`, `delivered_via COUNTER|APP`, `delivered_to/at`.
- `laboratory.lab_result_amendment` — lab_order/lab_test_request scoped, `old_value` preserved, `new_value`, `reason` (required), `amended_by/at`.

**Storage + PDF:**
- `LabReportStorage` mirrors `PatientDocumentStorage` — S3 API, MinIO in dev, props `squamed.lab-reports.*`, bucket `squamed-lab-reports`, key `lab-reports/$hospitalId/$orderId/v$version-<uuid>.pdf`; disabled fails with an honest error so the API contract stays truthful.
- `LabReportPdf` — hand-rolled PDF 1.4 writer, **no PDF dependency**: 6 objects, Helvetica core fonts, hospital-branded header, patient block, test table (TEST/RESULT/UNIT/REFERENCE/FLAG), amendment trail, footer. Uncompressed content stream so PIT asserts raw bytes; ASCII sanitize + `(` `)` `\` escape + `fit()` truncation.

**Service (`LabService.kt`):**
- **Structured age/sex range** in `enterResults`: manual entry wins → `resolveStructuredRange` (active ranges for hospital+test filtered to the patient's age window and sex; exact sex preferred over `ANY`, most-specific age span wins) → prior string fallback (02.02 behavior preserved).
- **`publishReport`**: released-only gate (**`LAB_ORDER_NOT_RELEASED`**), next version = top+1, renders current released state, stores the PDF, custody `REPORT_PUBLISHED`, event **`lab.report.published`** (repo convention dotted lowercase) with `recipientIds = orderedBy` — notification fan-out stays with the chunk-00 notifier (00.12).
- **`getReportBytes`** → `LabReportDownload(orderNumber, reportVersion, bytes)`; **`listReports`** = version history.
- **`deliverReport`**: via must be `COUNTER|APP` else **`DELIVERY_VIA_INVALID`**; single stamp — second delivery → **`REPORT_ALREADY_DELIVERED`**; custody `REPORT_DELIVERED`.
- **`amendResult`**: blank reason → **`AMENDMENT_REASON_REQUIRED`**; released-only; old value preserved in the versioned amendment row; recomputes `isAbnormal` and **re-runs `detectCriticalResult`** (an amended value can open a critical); custody `AMENDED`.
- **`createReferenceRange`** validation: **`REFERENCE_RANGE_SEX_INVALID`** / **`REFERENCE_RANGE_AGE_INVALID`** / **`REFERENCE_RANGE_TEXT_REQUIRED`**.
- **`tatAnalytics(days=30)`**: ALL row + one row per test code; TAT = `orderedAt → releasedAt` minutes over the window (count/avg/min/max).

**API (`LabController.kt`)** — 9 new endpoints under `/api/v1/lab`: `POST|GET /orders/{id}/report` (GET streams `application/pdf` with `Content-Disposition: attachment; filename="$orderNumber-v$version.pdf"`), `GET /orders/{id}/reports`, `POST /reports/{reportId}/deliver`, `POST /orders/{id}/results/{testRequestId}/amend`, `GET /orders/{id}/amendments`, `POST /reference-ranges` (CREATED; pinned via `tenantResolver.pinOrDefault`), `GET /reference-ranges?testCode=`, `GET /tat?days=30`; all reuse **V17** permission codes (`laboratory:read:lab` / `laboratory:verify:lab`) — no new codes seeded.

**Contracts** — `docs/contracts/openapi-v1.json` regenerated from live `/api-docs` (new schemas `TatRowView`, `ReferenceRangeView`, `LabReportView`, `LabResultAmendmentView`); `packages/api-client/src/generated/schema.d.ts` re-stamped (sha256 f06855bc…99ccd). Events: `lab.report.published` new.

## 3. Gap closure
- ✔ Branded PDF report generation, versioned (republish after amendment = v+1, history listed)
- ✔ Age/sex structured reference ranges with age-window + sex selection and string fallback
- ✔ Amendments versioned: old value preserved, reason mandatory, released-only
- ✔ Delivery counter/app with single-stamp + `lab.report.published` notification event
- ✔ TAT analytics (ALL + per test code over a window)
- ✔ PIT 02.04: `labReportsDeliveryCoverage` PASS

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | All named contents implemented; PIT walk: structured age/sex range selection (0-10 band ignored for a 38yo), released-only publish gate, v1 PDF bytes (header/order number/range/flag), COUNTER delivery + double-delivery + invalid via, amend (old preserved, reason required), republish v2 with amendment trail, male manual fallback + APP delivery, TAT rows |
| 2 Zero-stub | PASS | CI branch run (PR #72) |
| 3 Types | PASS | CI branch run (PR #72) |
| 4 Lint + format | PASS | CI branch run (PR #72) (ktlintFormat produced no changes) |
| 5 Tests | PASS | CI branch run (PR #72); named PIT below. Role-mapping legs deferred to 02.09 (§9) |
| 6 Accessibility | PASS | CI branch run (PR #72) (axe 0 serious/critical). Web surface untouched by this PR |
| 7 Design system | PASS | CI branch run (PR #72) |
| 8 Contracts | PASS | OpenAPI snapshot regenerated + committed (same PR); codegen stamped; PIT `openApiSnapshotMatchesLiveContract` green in the same run |
| 9 Data | PASS | V66 additive with rollback note; tenant pinning: reference-range create via `tenantResolver.pinOrDefault`, cross-hospital reads null-safe (`:hid is null or …`), `requireSameHospital` on report/amendment reads + delivery mutations. RLS stage-2 remains repo-wide ADR-13 debt (not this section) |
| 10 Audit | PASS | AuditAspect covers the new write endpoints (controller-intercepted); amendments are an auditable versioned trail; publish/deliver/amend recorded in the custody trail |
| 11 Docs | PASS | This report; LEDGER row 02.04 COMPLETE; tracker updated |
| 12 Exit item | PASS (partial) | Report delivery demo slice at 02.09 chunk exit (per chunk file); this section supplies the PIT evidence |

## 5. Tests
Named PIT (green in the PR #72 branch run): **`labReportsDeliveryCoverage`** (new, 02.04): reference ranges for `REF-*` (F 11-200 "3.5-4.6" + F 0-10 "2.0-3.0"; invalid sex 4xx) → female 38y order, no manual range → structured "3.5-4.6" applied, 4.8 abnormal (0-10 band proves age windowing) → publish on verified-only order 4xx `LAB_ORDER_NOT_RELEASED` → release → publish v1 → PDF bytes carry `%PDF-1.4` + order number + `3.5-4.6` + `ABNORMAL` → COUNTER delivery stamps once, second 4xx `REPORT_ALREADY_DELIVERED`, `COURIER` 4xx `DELIVERY_VIA_INVALID` → amend blank reason 4xx `AMENDMENT_REASON_REQUIRED` → amend 4.8→4.0 "transcription corrected" (old 4.8 preserved, 4.0 no longer abnormal) → republish v2 carries `AMENDED` trail, history lists 2 versions latest-first → male order manual "3.5-5.1" fallback (4.8 normal) → publish v1 + APP delivery with recipient → TAT: ALL row ≥2 released orders, per-test row count 2 with avg/min/max fields. Regression legs green locally in the same session: `labWorkflowCoreCoverage`, `clinicalEncounterAndPharmacyCoverage`, `criticalResultsQcCoverage`, `fullPatientRevenueJourney`.

## 6. Accessibility
Repo-wide axe gate green (PR #72 branch run); web surface untouched by this PR — no per-page axe delta.

## 7. Issues found during build + fixes
- **BaseEntity `version` collision**: entity `version` is the `@Version` optimistic-lock column repo-wide — the report's version column is named **`report_version`** to avoid lock-column interference.
- **PIT harness `JsonNode.size`**: raw array nodes need `.size()` (three spots compile-fixed); filtered Kotlin lists keep `.size`.
- **Nullable `LabOrder.orderedAt/releasedAt`**: `tatAnalytics` filters with safe-call (`releasedAt?.isAfter(since) == true`) and defaults (`orderedAt ?: EPOCH`, `releasedAt ?: now`) in the duration.
- Missing `ResponseEntity` (controller) / `java.util.UUID` (entity) imports caught by compile.

## 8. External blockers
None for this section's code. Real-printer/PDF-viewer fidelity (fonts, pagination, digital signature) stays out of scope — the PDF is deliberately minimal and dependency-free (§9).

## 9. Deviations from chunk file
- **Minimal hand-rolled PDF 1.4** (no PDF library): Helvetica core fonts, ASCII-only (non-ASCII → `?`), single page, ≤24 result rows + ≤8 amendment lines (truncated). Branded layout is honest but not print-shop grade; a library-based renderer is a straight swap inside `LabReportPdf` if ever needed.
- **Delivery is a single stamp** on the report row (`COUNTER|APP` + recipient + timestamp) — no per-recipient delivery log; notification fan-out (counter/app push) stays with the 00.12 engine via `lab.report.published`.
- **TAT semantics**: order-level `orderedAt → releasedAt` attributed to every test on the order; no per-test stage timings (collection/verification milestones not split).
- **Reference ranges are not themselves versioned** — `is_active` rows superseded by adding a new row; selection picks the most specific active match at result-entry time.
- Full role verification (counter clerk vs pathologist on deliver/publish) deferred to 02.09 with the other role legs.

## 10. Exit-demo slice
PIT `labReportsDeliveryCoverage` (PR #72 branch run); 02.09 chunk exit assembles the lab report delivery slice (publish → download → counter delivery).
