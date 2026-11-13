# BASELINE AUDIT — static, read-only (product-owner decision input for D16–D19)

**Baseline audited:** `C:\Users\manag\AppData\Local\Temp\opencode\squamed-master-baseline` (cited paths are relative to this root).
**Method:** static analysis only. No builds, tests, installs, or writes were performed. Load-bearing claims cite `file:line`. Results that depend on running gradle (JaCoCo, ktlint) are labeled **claim — not re-runnable**.
**Role of this document:** evidence for open decisions D16–D19 (§5, §7). It makes **no decisions**.

---

## 1. Executive summary

- The baseline is a broad vertical-slice prototype: **30 backend modules (21,270 LOC Kotlin)**, **apps/web 11,780 LOC TSX**, **7,889 LOC packages** (~41k total). It sketches the shape of chunks 0–5, but the **chunk-0 foundation gates the protocol treats as blocking are unmet in code**.
- **Biggest risk — tenancy (D18):** `hospital_id` is nullable and client-supplied (`patient/domain/PatientRecord.kt:13-14`, `task/api/TaskDtos.kt:8`); zero RLS in all 40 migrations; `HospitalScopedEntity.kt` is referenced by no other file.
- **Event fabric deviates twice:** `events/DomainEventPublisher.kt` does direct `KafkaTemplate` sends with catch→log→drop — no transactional outbox, no DLQ/replay — while the chunk-00 contract puts Kafka out of scope in favor of outbox→Redis pub/sub→WS.
- **Audit is not tamper-evident:** `audit/domain/AuditLog.kt` is a plain table — no hash chain/`previous_hash` (ADR-9 gate), no nightly verify, no WORM export.
- **D16:** a working JWT + TOTP + RBAC + session-revocation stack exists in `identity/` (1,345 LOC); the contract's Keycloak/OIDC realm-as-code is absent — keep-vs-migrate is live.
- **D17:** `interop/fhir/FhirService.kt` is 403 lines of **read-only, GET-only** mapping; 06.07 requires HAPI profiles, search params, and writes — keeping it means a later rebuild.
- **D19:** HL7 is a hand-rolled MLLP server bound to `127.0.0.1:2575`, ADT-only; 02.01 requires ASTM, quarantine, zero-silent-drop — Mirth vs major hardening is live.
- **Stub census:** 73 grep hits; only **3 are real stubs** (3 `PlaceholderPage` tsx files); 11 are `toDouble()` false positives; no TODO/FIXME in Kotlin sources.
- **Tests:** 74 `@Test` in tree (37 integration + 37 unit) matches the Phase-2e addendum; the PHASE-1-REPORT "67 integration tests" does not reconcile with the current 37-test integration file; JaCoCo/ktlint claims not re-runnable here.
- **Zero-code sections:** 24 of the mapped chunk 0–6 sections have no corresponding code (§6).

---

## 2. Module → section mapping

Verdicts: **MEETS** = section's core contents exist and match; **PARTIAL** = core flow exists, named contents missing; **ABSENT** = no corresponding code. Backend tests are centralized: `backend/squamed-core/src/test/kotlin/com/squamed/core/it/PlatformIntegrationTests.kt` (37 `@Test`, nested JourneyTests 5, SecurityTests 7, ContractTests 2, CoverageTests 16, Phase2InpatientTests 7) — referenced below as **PIT**.

### 2.1 Backend modules → chunk 0–6 sections

| Module (LOC) | Section(s) | What exists (evidence) | Tests | Verdict |
|---|---|---|---|---|
| identity (1345) | 00.03 | JWT HS256 + refresh, TOTP MFA, RBAC (23 seeded perms + per-migration seeds V5–V40), sessions w/ revocation, 5-fail lockout, permission AOP (`identity/security/JwtService.kt`, `TotpService.kt`, `AuthService.kt`, `SquamedPermissionEvaluator.kt`) | PIT SecurityTests (7), AiIntentServiceTest auth-adjacent | PARTIAL — no Keycloak/OIDC realm-as-code (that IS D16); no device registry |
| — (none) | 00.04 | No tenant entity, no RLS, no cross-tenant denial suite. `common/domain/HospitalScopedEntity.kt` unused | none | **ABSENT** |
| hospitalconfig (956) | 00.05 | Hospital/Campus/Building/Department/Specialty/Ward/Room/Bed/Service CRUD + tree (`HospitalConfigController` per `docs/wired-audit.md:29`) | PIT hospitalConfigValidationBranches | PARTIAL — no config documents (versioned validated JSONB), no config-change audit trail beyond generic audit |
| masterdata (1031) | 00.06 | Drug/lab/img/dx/charge catalogs with filter+pagination (`docs/wired-audit.md:30`) | PIT catalogFilter/Pagination/Validation | PARTIAL — no vendor registry, equipment registry, employee master; price lists live in billing tariffs only |
| patient (1659) | 00.07 | Registration, MRN gen (`patient/service/MrnGenerator.kt`), identifiers, find-or-create | PIT patientLifecycleCoverage | PARTIAL — no ABHA identifier, no photos (MinIO), no guardian/dependent links, no unknown-patient flow evidence |
| patient/EMPI (`EmpiMatcher.kt`) | 00.08 | Deterministic + probabilistic scoring, merge (`patient/service/EmpiMatcher.kt:227`, `PatientService` mergePatient) | PIT empiAndMergeCoverage | PARTIAL — no review queue UI, no reversible unmerge, no two-person rule, no 200-case eval |
| events (273) | 00.09 | Direct Kafka publish, topics `squamed.domain-events`, `task.*`, `notification.*` (`events/DomainEventPublisher.kt`); WS push exists (`/ws`) | PIT journey/coverage tests | PARTIAL (thin) — **no outbox, no dispatcher, no DLQ, no replay CLI**; deviates from chunk-00 contract (outbox→Redis→WS, Kafka out of scope) |
| audit (292) | 00.10 | AOP write interception (`audit/service/AuditAspect.kt`), plain table, `/audit/logs` read | PIT auditTrailWritesForModuleActions | PARTIAL — no hash chain (`audit/domain/AuditLog.kt`, no `previous_hash`), no chain-verify job, no WORM export |
| task (346) | 00.11 | Task CRUD, assign, setStatus; `hospitalId` from client DTO default null (`task/api/TaskDtos.kt:8`) | PIT taskAssignmentWorkflowCoverage | PARTIAL — no SLA escalation rules, no config-driven rules, subject-links thin, no inbox UI beyond web pages |
| notification (194) + communication (755) | 00.12 | In-app notifications; communication channels/messages (`docs/wired-audit.md:26-27`) | PIT notificationPreferences, communicationReadCompliance | PARTIAL — **no consent records/withdrawal/eSign, no email/SMS/WhatsApp adapters, no DND** (chunk-00 requires email adapter live) |
| — (none) | 00.13 | No service worker / IndexedDB store-and-forward (grep zero hits) | — | **ABSENT** |
| search (349) | 00.15 (search part) | OpenSearch index for patients+encounters, query_string; silent-fail→empty (`search/SearchService.kt`) | PIT search coverage | PARTIAL — silent failure mode violates zero-silent-drop spirit; only 2 indexes |
| — (none) | 00.16 | No OTel collector / RED dashboards / SLO burn alerts / DLQ alert routing evidence in repo | — | **ABSENT** |
| scheduling (355) | 01.01 | Appointment CRUD + status, no_show_at, telemedicine flag (`scheduling/domain/Appointment.kt`) | PIT appointmentWorkflowCoverage | PARTIAL — no session templates, slot generation, waitlist, overbooking rules, doctor+room+equipment resource binding |
| receptionqueue (924) | 01.02/01.03 | Token queue call/start/complete/skip/postpone/noShow/board/stats + visit registration | PIT queueWorkflowCoverage, JourneyTests reception | PARTIAL — no config-driven priority classes evidence, no QR queue ID, no re-print, no estimated wait |
| encounter (1004) | 01.04 | Encounter start/complete, vitals, SOAP notes + sign, diagnosis, orders, prescribe | PIT encounterAndVitalsCoverage, ordersAndPrescriptions | PARTIAL — no coded ICD-10/SNOMED evidence, no allergy BLOCK/WARN in prescribe, no eSign/PIN signature machinery, no referral/follow-up booking |
| patient360 (222) | 01.05 | 360 endpoint + web overview page (`apps/web/src/app/(app)/patients/[id]/overview/page.tsx`, 1013 LOC) | PIT patient360Story | PARTIAL — consents/documents/med-trend completeness unverified |
| emergency (529) | 01.09 | Emergency module exists (triage/queue adjacency) | PIT journey | PARTIAL — no printed downtime pack / re-entry tool evidence |
| analytics (474) | 01.11 / 06.06 | `analytics/service/AnalyticsService.kt` (474 LOC), snapshot endpoint (`docs/wired-audit.md:32`) | PIT analyticsSnapshotCoverage | PARTIAL — basic stats only; no event-fed marts, KPI dictionary, command centres |
| laboratory (544) | 02.01–02.04 | create→collect→receive→process→enter→verify→release/reject lifecycle, abnormal flagging (`laboratory/service/LabService.kt:223-227`) | PIT labResultAndAbnormalFlagsCoverage | PARTIAL — no analyzer intake/quarantine (02.01 beyond ADT-only HL7), no barcode/accessioning, no delta checks, no QC (02.03), no branded PDF/TAT (02.04) |
| pharmacy (541) | 02.06–02.07 | addStock/dispense/verifyDispensation/returnOrCancel (`pharmacy/service/PharmacyService.kt`) | PIT pharmacyDispensingAndStockCoverage | PARTIAL — no FEFO, no batch/expiry BLOCK, no trigger-enforced stock ledger, no controlled register; **02.07 medication-safety engine ABSENT** (no interactions/allergy ingredient mapping/max-dose/renal) |
| billing (784) | 03.01–03.02, 03.06 | tariff/charge/invoice/payment/cancel (`billing/service/BillingService.kt`) | PIT billingPaymentCoverage | PARTIAL — no payer-class rate cards, versioned packages, estimates, deposits, role-gated discounts, professional-fee split (03.02); no tax (03.03), no TPA (03.04), no HIM (03.05), no gateway/GL export (03.06) |
| ipd (635) | 04.01, 04.08 | Admit/transfer/discharge, attendants, discharge summary (`docs/wired-audit.md:34`) | PIT Phase2InpatientTests (7) | PARTIAL — no allocation rules/isolation constraints, no bed-hour charging, no dietary/kitchen manifest |
| nursing (905) | 04.02, 04.03 | assessments/careplans/notes/handovers/vitals/MAR/risk-assessments (`docs/wired-audit.md:35`) | PIT nursingDocumentationCoverage | PARTIAL — no barcode patient+drug hard block, no high-alert double-check, no MAR-from-dispensed-med linkage |
| icu (755) | 04.05 | vitals/fluid-balance/ventilation/infusions/alerts (`docs/wired-audit.md:36`) | PIT icuMonitoringCoverage | PARTIAL — no device-fed flowsheets (04.04 dependency absent), no SOFA/GCS calculators evidence |
| surgery (1042) + anaesthesia (852) | 04.06 | OT schedule + intra-op + anaesthesia records | PIT journey | PARTIAL — no WHO checklist 3-stage enforcement, no implants UDI, no PACU/Aldrete evidence |
| bloodbank (1145) | 04.07 | Donor registry, components, crossmatch→issue (`docs/wired-audit.md` CS-10 area) | PIT journey | PARTIAL — wristband bedside BLOCK check and reaction traceability unverified |
| scribe (604) | 05.05 | Session lifecycle recording→transcribe→draft→edit→sign; Echo + OpenAI-Whisper STT providers; rule-based generator | ScribePipelineTest (9), ScribeSttTest (3) | PARTIAL — no diarization, no per-encounter consent flag/recording indicator, no 30 d audio purge, no radiology/lab report drafting |
| ai (1028) | 05.01, 05.03, 05.07 | ModelConfig/AgentConfig/ToolConfig/InteractionLog; chat/command/SSE; router + Echo/OpenAI-compatible providers; intents NAVIGATE/COUNT/SEARCH/WORKFLOW/ANALYTICS/HELP/CHAT | AiIntentServiceTest (10) | PARTIAL — no OIDC client identity for AI, no prompt redaction, no token budgets/caps hard-cut, no kill switch, no citations/grounding enforcement |
| interop (1134) | 02.01 (HL7), 06.07 (FHIR) | FHIR read-only views (D17); MLLP server + connection registry (D19) | Hl7MessagesTest (7), PIT interopAndFhirCoverage | PARTIAL — see §5 D17/D19 |
| workflow (955) | 00.11-adjacent | Definitions/instances/decisions engine, branch coverage | PIT workflowEngineBranchCoverage | PARTIAL — generic workflow engine; not the SLA escalation machinery of 00.11/02.03 |
| common (393) | cross-cutting | ApiResponse envelope, `HospitalScopedEntity.kt` (unused), ProductionHygiene guard (`common/config/ProductionHygiene.kt:25`) | ProductionHygieneTest (8) | PARTIAL |

### 2.2 Frontend areas (7) → sections

| Area (LOC) | Section(s) | What exists | Tests | Verdict |
|---|---|---|---|---|
| apps/web/src (11,780) | 01.x–05.x pages | Pages w/ LOC: ai 1012, workflow 1024, patients/[id]/overview 1013, integrations 945, consult 881, billing 838, pharmacy 644, laboratory 591, scribe 532, reception, appointments, patients, communication, orders (placeholder), master-data (placeholder), settings (placeholder), login | `command-bar.test.tsx`, `sse.test.ts` | PARTIAL — no TV/kiosk/voice surfaces (01.06–01.08), 3 placeholder pages |
| packages/ui (6,967 / 69 files) | 00.14 | ~36 primitives (incl. CommandBar/CommandPalette) + 20 domain comps (ClinicalNoteEditor, OrderEntryForm, PrescriptionBuilder) + 9 layout | — | PARTIAL — no Storybook/visual-regression evidence, density modes unverified |
| packages/design-system (183) | 00.14 | Tokens only | — | PARTIAL — tokens exist; §2–4 of 02-DESIGN-SYSTEM coverage thin |
| packages/auth (332) | 00.03 (frontend) | Login/MFA flow client | — | PARTIAL |
| packages/api-client (121) | 00.02 | Thin client | `client.test.ts` | PARTIAL — no codegen pipeline, no idempotency middleware, no envelope/schema-registry harness |
| packages/schemas (117) | 00.02 | Thin zod-style schemas | `patient.test.ts` | PARTIAL — no event envelope v1 schema |
| packages/domain-types (169) | 00.02 | Shared types (incl. patient consent types mention in `packages/domain-types/src/patient.ts`) | — | PARTIAL |

---

## 3. Stub / TODO census (gate-2 terms)

**Method:** `rg -i "TODO|FIXME|stub|placeholder|mock|not implemented|for now"` over the repo, excluding `*.md` and `package-lock.json` → **73 hits** (identical count source-only and repo-wide). Full raw output preserved at `C:\Users\manag\AppData\Local\Temp\opencode\stub-census-full.txt`; transcribed exactly below, grouped. Zero hits for `TODO`/`FIXME`/`stub`/`not implemented`/`for now` as actual code markers in Kotlin/TS sources — all 73 hits are one of the five groups below.

### 3.1 Real stubs (gate-2 relevant) — 3 hits
| # | Hit |
|---|---|
| 1 | `apps/web/src/app/(app)/master-data/page.tsx:5` — `export default function PlaceholderPage()` (12-line page) |
| 2 | `apps/web/src/app/(app)/orders/page.tsx:5` — same |
| 3 | `apps/web/src/app/(app)/settings/page.tsx:5` — same |

### 3.2 Test-only mocks + test dependency — 5 hits
- `backend/squamed-core/build.gradle.kts:62` — `testImplementation("io.mockk:mockk:1.13.13")`
- `backend/.../ai/service/AiIntentServiceTest.kt:10` — `import io.mockk.every`
- `backend/.../ai/service/AiIntentServiceTest.kt:11` — `import io.mockk.mockk`
- `backend/.../ai/service/AiIntentServiceTest.kt:20` — `mockk<AnalyticsService>(relaxed = true)`
- `backend/.../ai/service/AiIntentServiceTest.kt:21` — `mockk<PatientService>(relaxed = true)`

### 3.3 String literal in production guard — 1 hit
- `backend/.../common/config/ProductionHygiene.kt:25` — `"JWT_SECRET must not use the development placeholder secret for the prod profile"` (the literal is the guard's own error message)

### 3.4 `toDouble()`/`toDoubleOrNull()` false positives ("todo" substring) — 11 hits
- `backend/.../ai/service/AiService.kt:118`
- `backend/.../analytics/service/AnalyticsService.kt:341`
- `backend/.../analytics/service/AnalyticsService.kt:351`
- `backend/.../laboratory/service/LabService.kt:223`
- `backend/.../laboratory/service/LabService.kt:226`
- `backend/.../laboratory/service/LabService.kt:227`
- `backend/.../workflow/service/WorkflowService.kt:496`
- `backend/.../workflow/service/WorkflowService.kt:498`
- `backend/.../workflow/service/WorkflowService.kt:509`
- `backend/.../workflow/service/WorkflowService.kt:510`
- `backend/.../patient/service/EmpiMatcher.kt:227`

### 3.5 Benign TSX `placeholder=` input attributes — 53 hits
apps/web (43): `components/communication/new-channel-modal.tsx:85,93,99` · `app/(app)/appointments/page.tsx:133,416,467` · `components/ai/command-bar.tsx:186` · `app/(app)/integrations/page.tsx:639,990` · `app/(auth)/login/page.tsx:72,97,106` · `app/(app)/ai/page.tsx:649,1025,1031` · `app/(app)/workflow/page.tsx:252,1087` · `app/(app)/reception/page.tsx:156,440` · `app/(app)/scribe/page.tsx:460,513,563` · `app/(app)/consult/page.tsx:382,608,614,620,626,850,856,888,894,913` · `app/(app)/patients/page.tsx:141` · `app/(app)/pharmacy/page.tsx:324,389,445` · `app/(app)/billing/page.tsx:417,518,621` · `app/(app)/communication/page.tsx:448` · `app/(app)/laboratory/page.tsx:376,448,460`
packages/ui (10): `domain/ClinicalNoteEditor.tsx:11,83,92` · `domain/OrderEntryForm.tsx:85,104` · `domain/PrescriptionBuilder.tsx:106` · `primitives/CommandBar.tsx:16,26,97` · `primitives/CommandPalette.tsx:196`

**Gate-2 verdict:** only §3.1 (3 files) are real stubs; every other hit is a UI attribute, a test mock, a guard-message literal, or a `toDouble` substring match. Note: rule-based offline providers (`EchoAiProvider`, `EchoSpeechToTextProvider`, `RuleBasedScribeGenerator`) contain no gate-2 marker and are design choices for keyless dev — not counted as stubs, but flagged for the D16/D17 review since they are the only AI/STT backends wired.

---

## 4. Test census + PHASE-1-REPORT consistency check

### 4.1 Backend tests (current tree) — 74 `@Test`
| File | `@Test` | Notes |
|---|---|---|
| `src/test/kotlin/com/squamed/core/it/PlatformIntegrationTests.kt` | 37 | nested: JourneyTests 5, SecurityTests 7, ContractTests 2, CoverageTests 16, Phase2InpatientTests 7 |
| `.../ai/service/AiIntentServiceTest.kt` | 10 | unit, mockk |
| `.../ProductionHygieneTest.kt` (config) | 8 | unit |
| `.../Hl7MessagesTest.kt` | 7 | unit |
| `.../ScribePipelineTest.kt` | 9 | unit |
| `.../ScribeSttTest.kt` | 3 | unit |
| **Total** | **74** | |

### 4.2 Frontend tests
- `apps/web`: `command-bar.test.tsx`, `sse.test.ts` (2 files)
- `packages/api-client`: `client.test.ts`; `packages/schemas`: `patient.test.ts`
- `tests/e2e`: `login.spec.ts`, `api-smoke.spec.ts` (2 specs)

### 4.3 Consistency vs PHASE-1-REPORT.md claims
| Claim (PHASE-1-REPORT.md) | Static check | Verdict |
|---|---|---|
| "Backend integration suite PASS · 67 tests" (line 10; also `docs/wired-audit.md:3-4`) | Current tree: integration file holds **37** `@Test`; 37 integration + 37 unit = **74** total | **Inconsistent with current tree.** The 67-claim is not reproducible; either tests were later split/consolidated into the 5 unit files or the claim counted differently. The Phase-2e addendum's "74 tests" (line 132) matches the tree only under an all-`@Test` reading |
| "JaCoCo instruction ≥ 0.80 (actual 80.11%)" (line 11; addendum ends 81.5%, line 132) | Requires `gradlew jacocoTestCoverageVerification` | **Claim — not re-runnable** (static audit, no builds allowed) |
| "ktlint PASS · 0 violations" (line 14) | Requires `gradlew ktlintCheck` | **Claim — not re-runnable** |
| Addendum progression 70→71→72→73→74 tests, coverage 80.1→81.5% (lines 88–132) | 74 total `@Test` present in tree | Test **count** consistent at the endpoint; intermediate numbers unverifiable |

---

## 5. ADR evidence packs (D16–D19) — evidence only, no decisions

### D16 — Keycloak/OIDC vs keep JWT+TOTP
**Definition (24-DECISIONS-REGISTER.md):** 00.03 contract specifies Keycloak realm-as-code + OIDC integration for web and API, MFA for admin/auditor, session + device registry, permission middleware.

**What the baseline has (working, self-contained):**
- Issue/verify: `identity/security/JwtService.kt` — HS256 via jjwt, `sid`/`hp`/`typ` claims, 10-min MFA token.
- MFA: `identity/security/TotpService.kt` (hand-rolled RFC 6238 HMAC-SHA1, ±1 step window) + `identity/service/MfaService.kt` (begin/enable, `otpauth://` URL, base32).
- Flows: `identity/service/AuthService.kt` — login / verifyMfa / refresh / logout, BCrypt, **5-fail lockout 15 min**, session records on login.
- Sessions: `identity/domain/SessionRecord.kt` — token hashes, ip/ua, hospital_id, revocation; `identity/security/TokenBlacklistService.kt` — Redis blacklist/session revocation.
- Authorization: `identity/domain/Permission.kt` (domain:action:resource), `Role.kt` + role_permission join, `SquamedPermissionEvaluator.kt` (Redis-cached 60 s, wildcard matching), `PermissionAspect.kt` (`@RequiresPermission` AOP), `JwtAuthenticationFilter.kt` (SquamedPrincipal, ROLE_AUTHENTICATED).
- Data: `db/migration/V1__init_identity.sql` (user/user_profile/role/permission/role_permission with JSONB constraints/user_role with hospital_id+scope+validity/session; 14 role seeds), `V3__seed_permissions.sql` (23 permissions) + per-module permission seeds in V5, V8, V13, V17, V21, V25, V26, V32, V34, V36, V38, V40.
- Config: `identity/security/SecurityConfig.kt` — stateless; permitAll on login/register/refresh/mfa/ws/swagger.

**What is missing vs the contract:** Keycloak realm-as-code, OIDC (web + API) token exchange, external IdP integration, device registry (SessionRecord is close but not a device registry), OIDC client identity for the AI gateway (05.01 dependency).

**Tradeoff paragraph:** Keeping the JWT+TOTP stack preserves ~1.3k LOC of tested auth (SecurityTests 7 green per wired-audit) with zero migration risk and everything the product needs functionally today; but every later integration that assumes OIDC (AI gateway client identity 05.01, future portal SSO, enterprise IdP federation in chunk 6) must be hand-built or retrofitted, and the hand-rolled TOTP is a security-review liability versus a maintained implementation. Adopting Keycloak buys the contract-mandated realm-as-code, standard OIDC, and maintained MFA, at the cost of rewriting login/refresh/MFA/session flows, re-pointing the filter chain and permission evaluator at JWKT, migrating user/role data, and reworking the web auth package — a multi-module change with regression risk across every secured endpoint.

### D17 — HAPI FHIR server vs keep hand-rolled FhirService
**Definition:** 06.07 requires a HAPI FHIR server with Patient/Encounter/Observation/DiagnosticReport/MedicationRequest profiles aligned to India EHR-M, hospital-to-hospital + ABDM flows, profiles validated in CI.

**What the baseline has:**
- `interop/fhir/FhirService.kt` — 403 lines; read-only mappers to `Map` + Bundle for Patient, Practitioner, Organization, Encounter, Condition, Observation, MedicationRequest, ServiceRequest, AllergyIntolerance, DiagnosticReport; MRN as OID `urn:oid:1.2.3.4.5.6.7`, QID identifiers.
- `interop/InteropControllers.kt` — **GET-only** `/api/v1/fhir/*` endpoints; also `/api/v1/hl7` parse/receive/history and `/api/v1/interop` connections CRUD + health.
- No writes, no search-parameter semantics, no profile validation, no ABDM/ABHA hooks.

**Tradeoff paragraph:** The hand-rolled service is cheap to keep — it already proves the 10 read resources, is fully under test (`interopAndFhirCoverage`), and adds no new runtime dependency; but it cannot satisfy 06.07 as written (profiles, CI validation, search, writes, ABDM alignment), so choosing it is really choosing to defer FHIR maturity and accept a likely rebuild when group-hospital or ABDM exchange arrives. HAPI FHIR brings the server, profile validation, search parameters, persistence, and the India-EHR-M packaging path in one step; the costs are a new heavyweight dependency and server, mapping code rewritten from the current Map-based mappers, and integration into the security filter chain and tests — a from-scratch integration with operational footprint.

### D18 — Postgres RLS vs application-enforced hospital_id
**Definition:** 00.04 requires a tenant entity, `tenant_id` on all tables, RLS policies, token→tenant resolution, and a cross-tenant denial test suite.

**What the baseline has:**
- `common/domain/HospitalScopedEntity.kt` exists but is referenced by **no other file** (`rg -l HospitalScopedEntity` → only its own path).
- `common/domain/BaseEntity.kt` — no tenant column.
- `patient/domain/PatientRecord.kt:13-14` — `hospital_id` **nullable**.
- Tenancy is client-supplied: `task/api/TaskDtos.kt:8` (`hospitalId` default null), `patient/service/PatientService.kt:63` (from dto), `PatientService.kt:245-252` and `:479-492` (hospitalId as optional client filter); `TaskController` listTasks has no hospital filter.
- ~40 entity files carry a `hospital_id` column; **zero** `RLS`/`CREATE POLICY`/`ROW LEVEL` occurrences across all 40 migrations (`rg` exit 1); `V6__master_data.sql` has no hospital_id at all.

**Tradeoff paragraph:** Application-level enforcement (make hospital_id NOT NULL, resolve it server-side from the principal, add the cross-tenant denial suite) is the smallest change that closes the current hole — client-controlled tenancy is the single sharpest security defect in the baseline — and it keeps the ORM/Hibernate model untouched; its weakness is that one forgotten filter or native query silently re-opens the hole, which is exactly what RLS exists to prevent. Database RLS on all ~40 tables is defense-in-depth that survives app bugs and satisfies the contract literally, but demands session-variable plumbing (SET app.hospital_id per request), migration rewrites of every table, careful handling of superuser bypass, and re-verification of every integration test — a large, invasive change across the persistence layer. A hybrid (RLS on the most sensitive tables, app-level elsewhere) spreads the cost and complexity of both.

### D19 — Mirth Connect vs keep hand-rolled MLLP
**Definition:** 02.01 requires HL7 v2 channels + ASTM bridge, `analyzer_message_log`, quarantine + alert with **zero silent drops**, ACK policy, version-controlled channel configs, 1k-message sim harness.

**What the baseline has:**
- `interop/hl7/Hl7Service.kt` — hand-rolled MLLP server bound to **`127.0.0.1:2575` (loopback only)**, 4 threads, 30 s timeout, MSH-required parse, ACK build, **ADT-only** PID import via `findOrCreateFromHl7`, `Hl7MessageLog` persistence.
- `interop/config/Hl7ServerLifecycle.kt` — starts on ApplicationReady.
- `interop/service/IntegrationConnectionService.kt` — connection registry, MLLP/TCP health probe, error counting.
- No ASTM, no ORU results flow, no quarantine lane, no channel configuration, no sim harness.

**Tradeoff paragraph:** Extending the hand-rolled server (accept ORU, add quarantine routing, configurable channels, non-loopback bind) is a moderate, well-understood change in code the team owns, and it keeps the deployment single-binary — but delivering the "zero silent drops" guarantee, ASTM bridging, version-controlled channel configs, and a 1k-message sim harness on bespoke socket code is real engineering with patient-safety consequences, and every analyzer onboarding repeats that cost. Mirth Connect provides channels, transforms, ASTM support, retry/quarantine UI, and operational maturity out of the box, matching 02.01's shape almost line for line; the costs are a new stateful service to deploy/monitor/secure, channel configs as external assets, code to bridge Mirth→SQUAMED APIs (or DB), and losing direct control of the parse path that current tests cover.

---

## 6. Gap list — chunk 0–6 sections with zero code

24 sections have no corresponding code or assets in the baseline (verified by grep/module inventory; "no evidence found" = zero grep hits for the section's keywords):

| Section | Missing contents | Evidence basis |
|---|---|---|
| 00.04 Tenancy & RLS | Tenant entity, RLS policies, cross-tenant denial suite | zero RLS in migrations; `HospitalScopedEntity.kt` unused |
| 00.12 (consent core) | Consent records/withdrawal/eSign, email adapter | backend consent grep zero hits (only `packages/domain-types/src/patient.ts` mentions consent) |
| 00.13 Offline groundwork | Service worker, IndexedDB queue, reconnect UX | grep `serviceWorker|manifest|indexedDB` zero hits |
| 00.16 Observability | OTel spans, RED dashboards, SLO burn alerts, DLQ alert routing | no collector/dashboard/alert config in repo |
| 01.06 TV display app | No TV app, offline cache/reconnect banner | no such app dir |
| 01.07 Voice announcement | No TTS engine/audio zones | zero hits |
| 01.08 Kiosk-lite | No kiosk app, thermal printing | zero hits |
| 01.10 Entitlement & whitelabel | Feature gates, per-tenant branding, DLT header/WABA brand | grep `entitlement|whitelabel|branding` zero hits |
| 02.03 Critical results + QC | Westgard QC, SLA escalation, ack trail dashboards | no QC code in laboratory module |
| 02.04 Lab reports + delivery | Branded PDF, amendments, TAT analytics | none |
| 02.05 RIS + PACS | Orthanc MWL, OHIF, radiologist worklist | zero hits |
| 02.07 Medication safety engine | Interactions, allergy ingredient mapping, duplicate therapy, max-dose, renal flag, 200-pair eval | no engine code |
| 02.08 Charge events + eMAR groundwork | `ChargeablePerformed` emission, consumption→ledger, reorder proposals | none |
| 03.03 Tax engine | GST/HSN/e-invoicing/credit notes | none |
| 03.04 Insurance/TPA | Payer registry, pre-auth, claim adapters, denial codes | none |
| 03.05 HIM coding + integrity | ICD-10 worklist, leakage rules, digests | none |
| 04.04 Device integration | Gateway→flowsheets stream, threshold alerts, sim | zero hits |
| 05.02 Eval framework | eval_dataset/eval_run, CI eval gates, gold-set pipeline, leak suite | zero hits |
| 05.04 Intelligence rail | Attention items from governed events, fatigue caps | none |
| 05.06 Three production agents | Ops/Revenue/Patient-comms agents | none |
| 06.02 Telemedicine | WebRTC, guidelines checklist, payment link | only a `telemedicine` flag on appointments |
| 06.03 Patient portal v2 | ABHA login, Web Push, payments | none |
| 06.04 Workforce/HR-lite | Rostering, leave, credential expiry | none |
| 06.05 Ops modules | Procurement, CSSD, facilities, full ED | none |
| 06.08 Resilience + retention | Degraded mode, DR drills, DPDP erasure, sandbox hospital | zero hits |

Also unmet (code exists but exit-gate evidence absent): 00.17 chunk-exit artifacts (load test, backup/restore drill, tamper test), 00.11 SLA escalation, 02.01 ASTM/quarantine (module is ADT-only loopback), 03.06 collections/GL export, 06.01 group semantics (hospital concept exists; group EMPI/consent sharing/config inheritance absent), 06.06 BI marts beyond the basic analytics endpoint, 06.07 HAPI outbound flows.

---

## 7. D16–D19 options, effort, risk

Effort legend (agent-sessions, one session = focused implement + test + wire cycle): **S** ≤ 1, **M** 2–5, **L** > 5. Risk = residual risk *after* the option is done. This table frames the decision; it does not make it.

### D16 — Identity
| Option | Effort | Key risk | What improves | What regresses |
|---|---|---|---|---|
| A. Keep JWT+TOTP stack, add OIDC-compatible endpoints later if needed | **M** (hardening: device registry, TOTP lib swap, key rotation) | Hand-rolled TOTP + HS256 single-secret model must pass security review; OIDC-dependent sections (05.01 AI client identity, 06.x federation) re-costed later | Zero migration risk; 74-test suite untouched; everything works today | Permanent deviation from 00.03 contract wording; every future IdP integration is bespoke |
| B. Migrate to Keycloak realm-as-code + OIDC | **L** (rewrite login/refresh/MFA/session flows, filter chain, evaluator→JWKT, migrate user/role data, web auth package) | Regression risk across every secured endpoint; Keycloak becomes an operational dependency (container, realm import, upgrade path) | Contract-compliant; maintained MFA; federation for chunks 5–6 nearly free | ~1.3k LOC auth replaced; Redis revocation model replaced by Keycloak sessions |

### D17 — FHIR
| Option | Effort | Key risk | What improves | What regresses |
|---|---|---|---|---|
| A. Keep FhirService, extend read resources ad hoc | **S–M** (per-resource mapper ~small) | 06.07 will demand profiles/search/writes → rebuild later anyway; Map-based output is not FHIR-validatable | Zero new deps; tests keep passing | ABDM/India EHR-M alignment never reached with this design |
| B. Adopt HAPI FHIR server + profiled resources | **L** (new server, rewrite mappers to HAPI model, CI profile validation, security wiring) | Heavy new runtime; mapper rewrite touches 10 resources and their tests | 06.07 unblocked; search, writes, CI-validated profiles, ABDM path | Operational footprint; current GET-only tests need rework |

### D18 — Tenancy
| Option | Effort | Key risk | What improves | What regresses |
|---|---|---|---|---|
| A. Application-level: NOT NULL hospital_id, server-side resolution from principal, cross-tenant denial suite | **M** (schema default/backfill + filter audit across ~40 entities + denial tests) | One forgotten filter/native query re-opens the hole (defense rests on developer discipline) | Closes the client-controlled-tenancy defect cheaply; ORM untouched | Does not literally satisfy 00.04's "RLS policies" |
| B. Postgres RLS on all tables + session-var plumbing | **L** (rewrite 40 migrations, per-request `SET app.hospital_id`, superuser/bypass audit, re-verify every integration test) | Migration invasiveness; native queries and admin paths need explicit policies; Hibernate dialect quirks | Contract-compliant; survives app-layer bugs (true defense-in-depth) | Large persistence-layer churn with data-migration risk |
| C. Hybrid: RLS on sensitive tables (patient, billing, audit), app-level elsewhere | **L** (both mechanisms + policy for which is which) | Two models to maintain and document; coverage line is a moving target | Depth where it matters, cost where it counts | Same as A for the app-level remainder |

### D19 — HL7 intake
| Option | Effort | Key risk | What improves | What regresses |
|---|---|---|---|---|
| A. Harden hand-rolled MLLP (ORU support, quarantine lane, channel config, non-loopback bind, 1k sim harness) | **M–L** (safety-critical guarantees on bespoke socket code) | "Zero silent drops" + ACK policy must be proven under load; ASTM bridge is extra; per-analyzer onboarding repeats cost | Single binary; code team owns; existing 7 Hl7MessagesTest extend | Duplicates what Mirth gives for free; ops tooling absent |
| B. Adopt Mirth Connect | **L** (deploy, channel configs as versioned assets, bridge to SQUAMED APIs/DB, re-home parse tests) | New stateful service to secure/monitor/upgrade; message path leaves the codebase | 02.01 shape almost line-for-line; ASTM, retry/quarantine UI, maturity | Current loopback design and Hl7MessageLog flow replaced; deployment complexity ↑ |

### Cross-decision notes (evidence, not recommendations)
- D16 and D18 interact: under Keycloak, tenant resolution rides OIDC claims; with the JWT stack, `hospital_id`/scope must continue to come from `user_role` (`V1__init_identity.sql`) — whichever D18 option is chosen should be tested against the D16 outcome.
- D17 and D19 share the interop module (`interop/`, 1134 LOC) and the same controllers file; sequencing one before the other avoids double rework in `InteropControllers.kt`.
- Event-fabric rework (outbox per chunk-00 contract) is **not** one of D16–D19 but blocks the same sections (00.09, 02.08 charge events, 06.06 marts); flagged because `DomainEventPublisher.kt`'s catch→log→drop undermines every event-fed section in the gap list.
- Audit hash chain (ADR-9) similarly: `AuditLog.kt` needs `previous_hash` + nightly verify before 00.10 can be called MEETS.

---

*End of audit. Sources: baseline tree at `C:\Users\manag\AppData\Local\Temp\opencode\squamed-master-baseline`; planning docs `24-DECISIONS-REGISTER.md`, `03-BUILD-PROTOCOL.md`, `04-CHUNK0-SECTIONS.md`, `14-`–`19-CHUNK*-SECTIONS.md`, `chunk-00-foundation.md` in the workspace root; raw census at `C:\Users\manag\AppData\Local\Temp\opencode\stub-census-full.txt`.*



