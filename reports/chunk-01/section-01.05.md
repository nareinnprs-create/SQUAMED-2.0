# Section 01.05 — Patient 360 v1 (chunk 1)

**Full build pass** on branch `feat/01.05-patient-360-v1` (off merged main `307d802`). Supersedes the baseline assessment (PARTIAL → IN PROGRESS): the missing chunk-line contents — **documents** (upload/store/list/download), **consents** in the aggregate, and the **persistent context bar** — all landed; the pre-existing aggregate panels were left as they were. All 12 gates green — normal CI on a public repo (no `--admin`, no local-CI exception). PR #50 squash-merged as `dd838ac2` after green CI (run **44076206371**, 7/7 jobs; the first run 44075045302 failed Backend gates on `:ktlintCheck` — argument-list-wrapping ×2 in the new PIT journey, fixed by `ktlintFormat` in `c45c697`, §7); merge-log row in this docs PR (ledger row 64).

## 1. Scope
Per `14-CHUNK1-SECTIONS.md`: demographics, allergies, conditions, encounters, meds, vitals trend, documents, consents, tasks; persistent context bar. The baseline tree already had the aggregate (`Patient360Service`: demographics, latest + recent-10 vitals, last-20 encounters, allergies, notes, medical history, last-20 appointments/visits/lab orders/medications, charges, invoices, tasks + open/completed summary), the `GET /api/v1/patient360/{patientId}` route, and the `PatientContextBar` component; missing were **documents**, **consents** in the aggregate, and **persistence** of the context bar across routes.

## 2. Built (current tree)
Backend:
- **`db/migration/V57__patient_documents.sql`** — new `patient.document` table holding **metadata only** (bytes live in object storage): `name`, `category` (default `other`; report | prescription | imaging | referral | discharge | id | other), `content_type`, `size_bytes`, `storage_key`; `hospital_id NOT NULL`, `(patient_id, created_at DESC)` index. Rollback note present. Header states **zero new permission codes** (§9).
- **`patient/domain/PatientDocument.kt` + `repository/PatientDocumentRepository.kt`** — entity + repository (`findByPatientIdOrderByCreatedAtDesc`).
- **`patient/service/PatientDocumentStorage.kt`** — mirrors `PatientPhotoStorage`: S3 API via the AWS SDK v2 sync client (**MinIO in dev, S3 in prod**), `@PostConstruct` headBucket + auto-create, key layout `documents/{hospitalId}/{patientId}/{uuid}.{ext}`; content-type allowlist **pdf / jpeg / png / webp** (else `DOCUMENT_CONTENT_TYPE_UNSUPPORTED` → 400), `DOCUMENT_EMPTY` and `DOCUMENT_TOO_LARGE` (> **20 MB**) guards; `squamed.documents.enabled=false` → 503 `DOCUMENT_STORAGE_DISABLED` so the API contract stays honest.
- **`patient/api/PatientController.kt` + `PatientDocumentDto.kt`** — 3 new endpoints: `POST /api/v1/patients/{id}/documents` (multipart: `file` + `name` + `category`, `patient:write:patient`), `GET /{id}/documents` (`patient:read:patient`), `GET /{id}/documents/{documentId}/download` (`patient:read:patient`; streams the bytes with `Content-Type` + `attachment` filename; a `documentId` not belonging to the patient → `DOCUMENT_NOT_FOUND`); every endpoint runs `requireSameHospital` → 403 foreign.
- **`application.yml`** — `squamed.documents.*` block (enabled/endpoint/region/access-key/secret-key/bucket/path-style, `DOCUMENTS_*` env-overridable; bucket `squamed-patient-documents`).
- **`patient360/service/Patient360Service.kt` + `api/Patient360Dtos.kt`** — `Patient360View` gains `consents` (`ConsentRepository.findByPatientId` → `ConsentView.from(consent, optIns, Instant.now())` — effective status computed at read: **withdrawn wins**, past `expiresAt` → expired) and `documents` (`findByPatientIdOrderByCreatedAtDesc` → `PatientDocumentDto.from`).

Frontend/tooling:
- **`apps/web/src/app/(app)/layout.tsx`** — the route-based patient-context auto-clear effect removed; `PatientContextBar` **persists across routes** and clears only via its ✕ button (doc comment in `patient-context-bar.tsx`).
- **`apps/web/src/app/(app)/patients/[id]/overview/page.tsx`** — **Documents table** (name/category/size/type + download via `apiFetchRaw` blob) + **upload form** (file input + name + category Select) and **Consents table** (purpose + effective status + signer/expiry); the two `<Select>`s carry `aria-label` (`Document category` / `Document name`) after the axe select-name catch (§7).
- **`apps/web/scripts/lib/scan-fixtures.mjs`** — `/patients/{fixed-uuid}/overview` route added with the `PATIENT_360` fixture (documents + consents arrays) → axe + VRT cover the page; new VRT baseline `patient-360.png` (VRT 10/10).
- **`docs/contracts/openapi-v1.json`** regenerated + **`packages/api-client/src/generated/schema.d.ts`** stamped — same PR as the surface change.
- **PIT** `@Test patient360DocumentsAndConsentsJourney` in `PlatformIntegrationTests` (fresh hospitals `P360 H1/H2 $s`, codes `P3A-/P3B-`, per run).
- **`scripts/demo-01.05-patient-360.ps1`** — 20-check exit demo (pwsh, app on :8080). Deviation: it rides this docs PR (untracked when #50 merged), §9.

## 3. Contracts delivered
- **DB**: `V57__patient_documents.sql` — one new hospital-scoped metadata table with rollback note; document bytes never touch the database.
- **API**: the 3 document endpoints above + `Patient360View.documents` / `.consents` fields.
- **Events**: zero new — EventCatalog stays **21 EMITTED** types (PIT-asserted).
- **Metrics**: none new.
- **Permissions**: **zero new codes** — upload rides `patient:write:patient`, list/download/aggregate read `patient:read:patient` (stated in the V57 header).
- **Contract artifacts**: OpenAPI snapshot + codegen stamp in the same PR; PIT `openApiSnapshotMatchesLiveContract` green.

## 4. Gates 1–12
1. PASS — all named missing contents implemented: documents (upload/store/list/download), consents with effective status in the aggregate, persistent context bar; the pre-existing aggregate panels unchanged.
2. PASS — CI: green run **44076206371** on branch head `c45c697` (all 7/7 jobs: Backend gates, Integration gates incl. the coverage chain, Zero-stub, Frontend gates, Accessibility+performance, Gate parity, Detect source trees; the first run 44075045302 failed only Backend gates — ktlint, §7); squash-merged `dd838ac2` (no `--admin`).
3. PASS — types: `npx turbo typecheck` clean; `apps/web` typecheck clean.
4. PASS — lint/format: `ktlintCheck` green (after `c45c697`); web ESLint 0 errors (3 pre-existing warnings).
5. PASS — tests: PIT **64/64** (external mode, real compose PG; the new journey alone passed locally in 2m33s before push; the full-suite citation is CI); unit suites green (turbo 28/28 in CI run 44076206371 — 146 unit tests); coverage chain `integrationTest test jacocoTestReport jacocoTestCoverageVerification` — **the citation is CI's Integration gates job, run 44076206371** (identical command, green on the branch head) — **merged coverage ≥ 0.80 enforced** (§5).
6. PASS — accessibility: `/patients/{id}/overview` now in SCAN_ROUTES → axe-scanned; the first scan caught a **real CRITICAL `select-name`** on the category Select (§7) → fixed with `aria-label`s; re-scan after a real rebuild: zero serious/critical (only the 4 pre-existing moderates on `/` and `/login` remain, unchanged).
7. PASS — VRT: **10/10** against committed baselines on the production build (chromium, serial, 2% threshold); new `/patients/{id}/overview` baseline created (`patient-360.png`).
8. PASS — contracts: OpenAPI snapshot + api-client stamp regenerated in the same PR; EventCatalog 21 EMITTED types asserted in PIT.
9. PASS — data: V57 with rollback note; tenancy enforced at the controller guard (`requireSameHospital` on all three endpoints + the aggregate; PIT cross-tenant 403 ×4); **RLS stays the 00.04/ADR-13 chunk-level seam** (§9).
10. PASS — audit: `AuditAspect` covers the new controller write (document upload is a POST → audit rows; list/download/aggregate are read-only GETs by design).
11. PASS — docs: this report; ledger row 64 + merge-log #49/#50 follow the PR in the standard flow.
12. PASS — exit demo: `scripts/demo-01.05-patient-360.ps1` **20/20** (§10).

## 5. Tests
- **PIT** (external mode): `patient360DocumentsAndConsentsJourney` — fresh hospitals `P360 H1 $s`/`P360 H2 $s` → doctor per hospital → patient in H1 → **documents**: multipart pdf upload → metadata row + object-storage key (`name`/`category`/`contentType`/`sizeBytes` asserted), list contains the upload, txt content-type → 400 → **consents**: treatment consent `granted`, `data_sharing` with past `expiresAt` surfaces `expired`, withdraw wins → `withdrawn` → **aggregate**: `documents` carries the upload, `consents` effective statuses = `{treatment: withdrawn, data_sharing: expired}` → **download**: 200 and bytes match the upload → **cross-tenant**: H2 doctor 403 on list/download/360/foreign-upload → own reads still allowed. Full suite **64/64** (63 pre-existing + this journey).
- **Old tests**: none needed — the change is purely additive (new endpoints + two new aggregate fields; existing aggregate asserts untouched).
- **Frontend**: web vitest 88/88; typecheck + build clean; Gate 6 + Gate 7 green (§4 gates 6/7).
- **Coverage**: repo merged coverage **≥ 0.80 enforced** — the coverage gate lives in the Integration gates job, which runs the identical chain `integrationTest test jacocoTestReport jacocoTestCoverageVerification` and was **green in CI run 44076206371** on the branch head (coverage gate green, run 44076206371). The local full-chain re-run was skipped this section after Docker Desktop crashed 3× under heavy parallel load (§7); the new journey alone passed locally in 2m33s — per the 01.03/01.04 precedent the CI run id is the citation.

## 6. Accessibility
`/patients/{id}/overview` joined SCAN_ROUTES this section, so the page is now axe-scanned and visually baselined. The upload form is labelled house primitives (`Input`/`Select`/`Button`); document/consent statuses are badge text (`withdrawn`/`expired`/`granted`), not color-alone; downloads are real buttons with text labels. The scan caught a real CRITICAL — the category Select had no accessible name — fixed with `aria-label` on both selects (§7). Gate 6's only remaining findings are the 4 pre-existing moderates on `/` and `/login` (unchanged from earlier sections).

## 7. Issues found during build + fixes
- **First CI run failed Backend gates on `:ktlintCheck`** (run 44075045302): argument-list-wrapping ×2 in the new PIT journey's hospital-creation calls (`PlatformIntegrationTests.kt:1182/1184` — the house pattern is one argument per line). Fix: `ktlintFormat` → commit `c45c697` → re-run green. Honesty note: `ktlintCheck` was not run locally before the push (§9).
- **axe `select-name` CRITICAL on the overview** — the upload form's category `<Select>` rendered with visible label text beside it but **no accessible name** (the label was not associated with the control). Fix: `aria-label="Document category"` / `aria-label="Document name"` flowing through `...rest`. Lesson: a visible label next to a control is not an accessible name — associate or label explicitly.
- **Stale `.next` build masked the fix on the first re-scan** — the gate scripts serve the **existing production build** (`scan-server.mjs` reuses `.next`), and turbo had cached the pre-fix build; the re-scan still reported the violation. Rule: **rebuild (uncached) before every re-scan**; after a real rebuild the scan went green.
- **Docker Desktop crashed 3× this session (#6, #7, #8) plus a Gradle daemon disappearance**, all under heavy parallel load (PIT + bootRun + web production build at once). Crash #8 hit mid-demo: the app accepted connections but hung (its dependencies vanished → first demo run 0/20, scrapped). Recovery each time: Docker Desktop restart → `docker info` stable ×3 → `docker start squamed-*` → PG healthy; the app self-recovered after containers returned. Rule reinforced: keep the dev app down while running heavy suites.
- **Demo-tooling traps (tool, not product)**: pwsh `Invoke-RestMethod -Form` guesses the part content type from the extension and sent `application/octet-stream` for the pdf → 400 `DOCUMENT_CONTENT_TYPE_UNSUPPORTED`; `curl.exe -F "file=path"` **without `@`** sends a plain text field instead of a file part → 500 `MissingServletRequestPartException` ("Required part 'file' is not present"). Working form: `curl.exe -F "file=@$filePath;type=$type" -F "name=..." -F "category=..."`.
- **Gradle daemon disappearance ("daemon unexpectedly exited")** killed the first `bootRun` after the PIT load; recovered by re-booting before the demo.
- **Playwright/VRT mechanics unchanged from 01.04** — new baseline written via `--update-snapshots=missing` with the `--workspace apps/web` form, then verified green in compare mode.

## 8. External blockers
None new. In-app document **preview/rendering** is not a chunk-line word (§9 — v1 is download-only). E1/E2/E3 unchanged from the planning ledger.

## 9. Deviations from chunk file
- **Zero new permission codes** — documents and consents ride the existing `patient:read:patient` / `patient:write:patient`; the V57 header states it deliberately. No separate document/consent permission model was requested by the chunk line.
- **Download-only, no in-app preview** — the chunk line says "documents"; v1 ships upload/list/download as raw bytes. A preview pane (PDF/image rendering) is not named by the chunk line and is deferred rather than built half-way.
- **The 360 consents surface is read-only** — grant/withdraw ride the existing consent endpoints (`ConsentService`, purposes fixed to `treatment` / `data_sharing` / `communications` — no new purpose values); the aggregate computes the effective status at read (withdrawn wins, expiry applied).
- **Demo script rides this docs PR, not the feature PR** — it was untracked when PR #50 merged (CI was already green on the code PR and the script is CI-neutral); one honest difference from the 01.04 pattern, recorded here and in ledger row 64.
- **First CI run failed on ktlint (formatting only)** — `ktlintCheck` was not run locally before the push; fixed by `ktlintFormat` in `c45c697`. Recorded per the no-silent-deviation rule; no logic changed.
- **Local full coverage-chain re-run skipped** — after Docker crashes #6–#8 the chain was cited from CI run 44076206371 (identical command, green) instead of re-run locally; the new journey alone passed locally in 2m33s.

## 10. Exit-demo slice
`pwsh scripts/demo-01.05-patient-360.ps1` against the app on :8080 (compose dev stack up): admin login → two fresh hospitals `P360 Demo $s`/`P360 F $s` + one doctor each (foreign doctor for tenancy) → patient registered with MRN → **pdf upload** (curl.exe multipart with explicit part type) 200 + metadata stored (name/category/contentType/sizeBytes) → document list contains the upload → **txt upload rejected 400** → consents: treatment `granted`, `data_sharing` with past `expiresAt` surfaces `expired`, withdraw wins → `withdrawn` → **360 aggregate** carries the document + effective consent statuses (`treatment=withdrawn`, `data_sharing=expired`) → **download 200 + bytes match the upload** → foreign doctor **403 ×4** (list/download/360/foreign upload) → own-tenant reads allowed ×2. **20 passed, 0 failed**, live 2026-11-17. **PR #50 squash-merged** as `dd838ac2` after green CI (run 44076206371, 7/7 jobs, no `--admin`), 2026-11-17 11:52 UTC.
