# SQUAMED — Phase 1 Completion Report

Repository verification report for Phase 1: Foundation MVP.
Date: 2026-09-08 · Branch: master

## 1. Verification summary

| Gate | Result | Evidence |
| --- | --- | --- |
| Backend integration suite | PASS · 67 tests, 0 failures | `gradlew test` (external mode, live services) |
| JaCoCo coverage gate | PASS · instruction ≥ 0.80 (actual 80.11%) | `gradlew jacocoTestCoverageVerification` |
| LINE coverage | 85.21% (4,760 / 5,586) | `build/reports/jacoco/test` |
| BRANCH coverage | 52.13% (1,552 / 2,977) | `build/reports/jacoco/test` |
| ktlint | PASS · 0 violations | `gradlew ktlintCheck` (scoped to sources, reports JSON) |
| Kotlin warnings-as-errors | PASS · 0 warnings | `compilerOptions.allWarningsAsErrors = true` |
| Boot jar | 107.7 MB · `squamed-core-1.0.0.jar` | `gradlew bootJar` |
| Prod compose manifest | VALID | `docker compose -f docker-compose.prod.yml config` |
| Web E2E specs | 2 specs authored, run in CI | `tests/e2e/login.spec.ts`, `tests/e2e/api-smoke.spec.ts` |

## 2. Bugs fixed by the Testing track

Discovered and fixed through contract-first integration tests:

1. **MFA secret never persisted** — `MfaService.beginMfaSetup` was `@Transactional(readOnly = true)`; the TOTP secret was generated but never saved. Fixed to read-write.
2. **Login returned 200 for invalid credentials** — `AuthController.login` now returns 401 with `INVALID_CREDENTIALS`; `AuthService` guards missing username/password (`USERNAME_REQUIRED`, `PASSWORD_REQUIRED`) which previously crashed with a 500 (BCrypt NPE).
3. **Malformed/missing JSON body → 500** — added `HttpMessageNotReadableException` handler returning 400 `MALFORMED_BODY`.
4. **Garbage HL7 input accepted** — `Hl7Service.parse` now requires an `MSH` segment and returns 400 `INVALID_HL7_MESSAGE`.

## 3. Production Ready

- `ProductionHygiene` fail-fast guard (prod profile): rejects blank/short/development-values for `JWT_SECRET`, `DB_PASSWORD`, `BOOTSTRAP_ADMIN_*` at startup. 8 unit tests.
- Hardened `application-prod.yml`: connection pool bounds, thread bounds, `flyway.clean-disabled`, health probes, no stack traces/messages in errors, `springdoc` disabled, structured JSON logging, TTL env vars.
- `Dockerfile`: multi-stage (21-jdk → 21-jre), non-root `squamed` user, HEALTHCHECK on `/actuator/health`, `.dockerignore`.
- `infrastructure/docker/docker-compose.prod.yml`: backend + postgres + redis + kafka + opensearch + prometheus + alertmanager; health-gated startup; `SQUAMED_IMAGE` override for CI rollouts; secrets via environment.
- Security headers enforced on all responses via `SecurityHeadersFilter` (nosniff, DENY frame, CSP `frame-ancestors 'none'`, HSTS, `Referrer-Policy: no-referrer`) — covered by `hardenedSecurityHeadersArePresent`.

## 4. Code Quality

- ktlint wired into `check` (`ktlintCheck`) with `ktlintFormat` for automatic fixes; `.editorconfig` documents 2-space indent, 160 max line length, and the disabled `filename` rule (bundled DTO/entity files).
- Compiler `allWarningsAsErrors` → zero-warning policy.
- Whole backend formatted; gate green.

## 5. Security track

Covered by `SecurityTests` + integration journeys: unauthenticated/tampered/forged tokens rejected; role-less users denied (403); MFA lifecycle; account lockout after 5 failures; injection payloads neutralized (SQL and OpenSearch query); clean error contract; security headers.

## 6. Wired audit

`docs/wired-audit.md` maps all 22 Phase 1 controllers to contract-tested surfaces, and proves the event-driven pathway (patient create → Kafka → OpenSearch index → search returns the patient) plus audit-on-write, REST/WS/HL7/FHIR transports.

## 7. CI/CD

- `ci.yml`: typecheck, lint, unit tests, build, **backend quality gates** (test + jacoco coverage + ktlint, jacoco HTML artifact), **bootJar artifact**, and an **E2E job** that boots infrastructure + backend and runs Playwright.
- `deploy-staging.yml`: GHCR build/push (`staging-<sha>`), SSH rollout of `docker-compose.prod.yml` with secrets, health wait, and post-deploy smoke via `scripts/phase1-e2e.ps1`.
- `deploy-production.yml`: approval-gated (tag or `APPROVE` dispatch), the same pipeline against the production environment.

## 8. Monitoring

- Micrometer Prometheus registry (`/actuator/prometheus`) wired into the backend; `build-info` exposes version/stamp.
- `prometheus.yml` (dev) + `prometheus.prod.yml` (in-network service-name targets) scrape backend, postgres-exporter, redis-exporter.
- `alerts.yml`: 5 SLO-aligned alerts (backend down, 5xx rate, error-log burst, DB pool saturation, heap pressure) → Alertmanager (`config/alertmanager.yml`) with severity routing + inhibition (host webhook is the documented integration point).

## 9. Evidence artifacts

- Coverage: `backend/squamed-core/build/reports/jacoco/test/html`
- Tests: `backend/squamed-core/build/reports/tests/test/index.html`
- ktlint: `backend/squamed-core/build/reports/ktlint/ktlintCheck.json`
- Wiring: `docs/wired-audit.md`
- Smoke: `scripts/phase1-e2e.ps1`

## 10. Known deferred items (by design)

- Local/exploratory E2E runs need browsers (`npx playwright install`) and a running web dev server; CI installs them per job.
- Alertmanager webhook URL is the deployment-defined integration point (`host.docker.internal:9095`) — point it at the real pager/chat endpoint.
- MinIO + Temporal intentionally deferred to Phase 2 per blueprint architecture notes.

## 11. Phase 2a addendum — IPD / Nursing / ICU

Delivered with the same contract-first approach and gates:

| Item | State |
| --- | --- |
| Migrations V29 (ipd) / V30 (nursing) / V31 (icu) / V32 (permission seeds) | Applied, idempotent |
| Admission lifecycle (admit, bed allocation/release, transfer, discharge, discharge summary, attendants) + admission.* events | Covered by `inpatientAdmissionLifecycleCoverage` |
| Nursing (assessments, care plans, SBAR handovers, notes, vitals, eMAR, risk assessments) + nursing.* events | Covered by `nursingDocumentationCoverage` |
| ICU (high-frequency vitals, fluid balance, ventilation, infusion, critical alerts w/ acknowledge/resolve) + icu.* events | Covered by `icuMonitoringCoverage` |
| Permissions (`ipd`/`nursing`/`icu` read+write) to SYSTEM_ADMIN, DOCTOR (ipd read), NURSE | Seeded |
| Suite | 70 tests, 0 failures, JaCoCo instruction 80.1% / LINE 85.66% / BRANCH 52.53%, ktlint clean |

## 12. Phase 2b addendum — Emergency and Trauma

| Item | State |
| --- | --- |
| Migrations V33 (emergency schema) / V34 (emergency permission seeds) | Applied, idempotent |
| Registration (arrival modes, ED queue by status/patient, complaint) + `emergency.registered` | Covered by `emergencyWorkflowCoverage` |
| ESI triage (1–5, stat escalation for ESI ≤ 2, idempotency) + `emergency.triaged` | Covered by `emergencyWorkflowCoverage` |
| Trauma workflow (single active, primary→secondary→tertiary→completed ABCD survey) + activate/completed events | Covered by `emergencyWorkflowCoverage` |
| Disposition state machine (admit requires admission link, discharge clears link + stamps time) | Covered by `emergencyWorkflowCoverage` |
| Suite | 71 tests, 0 failures, JaCoCo instruction 80.3% / LINE 85.6% / BRANCH 52.8%, ktlint clean |

## 13. Phase 2c addendum — OT / Surgery

| Item | State |
| --- | --- |
| Migrations V35 (surgery schema, 9 tables incl. ot_room) / V36 (SURGEON role + surgery permission seeds) | Applied, idempotent |
| Surgical scheduling lifecycle + `surgery.scheduled/started/completed` events | Covered by `surgicalWorkflowCoverage` |
| OT room registry + conflict-checked allocation (overlap rejected) | Covered by `surgicalWorkflowCoverage` |
| Team assignment (role whitelist), instruments (count lifecycle), implants (lot/expiry/qty) | Covered by `surgicalWorkflowCoverage` |
| Pre-op checklist with stamped checks, intra-op timeline, post-op record (validated destination) | Covered by `surgicalWorkflowCoverage` |
| Suite | 72 tests, 0 failures, JaCoCo instruction 80.8% / LINE 85.9% / BRANCH 53.4%, ktlint clean |

## 14. Phase 2d addendum — Anaesthesia

| Item | State |
| --- | --- |
| Migrations V37 (anaesthesia schema) / V38 (ANAESTHESIOLOGIST role + permission seeds) | Applied, idempotent |
| Pre-anaesthesia evaluation (ASA I–VI, draft→confirmed flow) + `evaluation_confirmed` event | Covered by `anaesthesiaWorkflowCoverage` |
| Anaesthesia record phase machine (planned→…→completed, airway whitelist) + phase events | Covered by `anaesthesiaWorkflowCoverage` |
| Intra-op monitoring (HR required) and medication log | Covered by `anaesthesiaWorkflowCoverage` |
| Recovery (Aldrete 0–10, discharge gating) + recovery_completed event and PACU record | Covered by `anaesthesiaWorkflowCoverage` |
| Suite | 73 tests, 0 failures, JaCoCo instruction 81.2% / LINE 86.3% / BRANCH 53.8%, ktlint clean |

## 15. Phase 2e addendum — Blood Bank

| Item | State |
| --- | --- |
| Migrations V39 (bloodbank schema, 8 tables) / V40 (permission seeds) | Applied, idempotent |
| Donor registry (blood-group whitelist, status lifecycle) | Covered by `bloodBankWorkflowCoverage` |
| Unit inventory (component whitelist, future expiry, status machine locking issued units) | Covered by `bloodBankWorkflowCoverage` |
| Grouping (unique per unit, confirm stamps) + crossmatch (technique/compatibility) | Covered by `bloodBankWorkflowCoverage` |
| Reservation → release, issue (issuable-only), transfusion lifecycle + reaction reporting | Covered by `bloodBankWorkflowCoverage` |
| Suite | 74 tests, 0 failures, JaCoCo instruction 81.5% / LINE 86.7% / BRANCH 54.2%, ktlint clean |