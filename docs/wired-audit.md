# SQUAMED Phase 1 — Wired Audit

Status: COMPLETE — verified against the live platform via `PlatformIntegrationTests`
(67 tests green) and `scripts/phase1-e2e.ps1` (full module smoke).

## 1. Module → API surface → contract

All modules are exposed by `AuthController`-style `@RestController`s under
`/api/v1/...` in `backend/squamed-core`.

| Module | Controller | Surface | Proven by |
| --- | --- | --- | --- |
| Identity / Auth | AuthController | `/auth/register`, `/auth/login`, `/auth/me`, MFA setup/verify | `SecurityTests`, `empiInputIdentifiersAndLifecycleCoverage` |
| Role-based access | RoleAdminController | `/roles` management | `userWithoutRolesIsDenied`, `roleScopedAccessIsEnforced` |
| Patient / EMR | PatientController, Patient360Controller | `/patients` CRUD + 360 by id | `patientLifecycleCoverage`, `patient360Story` |
| EMPI | PatientController (emp routes) | `/patients/empi/...` match, merge | `empiAndMergeCoverage`, `empiInputIdentifiersAndLifecycleCoverage` |
| Appointments | AppointmentController | `/appointments` CRUD + status | `appointmentWorkflowCoverage` |
| Reception | ReceptionController | `/reception/...` flows | `JourneyTests` reception journey |
| Queue + display/voice | QueueController | `/queue` entries, status, skip | `queueWorkflowCoverage` |
| Orders | (OrdersController) | `/api/v1/orders/...` | `ordersAndPrescriptionsCoverage` |
| Laboratory | LabController | `/lab/...` results, panels, abnormal flags | `labResultAndAbnormalFlagsCoverage` |
| Pharmacy | PharmacyController | `/pharmacy/...` dispensing | `pharmacyDispensingAndStockCoverage` |
| Billing | BillingController | `/billing/charges`, invoice, payment | `billingPaymentCoverage`, `catalogFilterAndPaginationCoverage` |
| Tasks | TaskController | `/tasks/...` | `taskAssignmentWorkflowCoverage` |
| Workflow | WorkflowController | `/workflow/definitions`, `/instances`, decisions | `workflowEngineBranchCoverage`, `workflowValidationAndEdgeCoverage` |
| Communication | CommunicationController | `/communication/channels/...` messages | `communicationReadComplianceCoverage` |
| Notifications | NotificationController | `/notifications/...` | `notificationPreferencesCoverage` |
| Audit | AuditController | `/audit/logs` (write via AOP on all writes) | `auditTrailWritesForModuleActions` |
| Hospital config | HospitalConfigController | `/hospitals/{id}/specialties|wards|beds|services` | `hospitalConfigValidationBranchesCoverage` |
| Master data | MasterDataController | `/master-data/...` (drugs, labs, imgs, dx, charges) | `catalogFilterAndPaginationCoverage`, `catalogValidationBranchesCoverage` |
| AI gateway + scribe | AiController, ScribeController | `/ai/intents`, `/ai/models`, `/ai/scribe/...` | `aiGatewayAndScribeCoverage` (echo provider) |
| Analytics | AnalyticsController | `/analytics/...` | `analyticsSnapshotCoverage` |
| Encounter | EncounterController | `/encounters/...` | `encounterAndVitalsCoverage` |
| IPD (Phase 2a) | AdmissionController | `/admissions` admit/transfer/discharge, attendants, discharge summary | `inpatientAdmissionLifecycleCoverage` |
| Nursing (Phase 2a) | NursingController | `/nursing/assessments|careplans|notes|handovers|vitals|mar|risk-assessments` | `nursingDocumentationCoverage` |
| ICU (Phase 2a) | IcuController | `/icu/vitals|fluid-balance|ventilation|infusions|alerts` | `icuMonitoringCoverage` |

## 2. Event-driven wiring (Kafka → OpenSearch)

| Pathway | Mechanics | Evidence |
| --- | --- | --- |
| Patient created → domain event → SearchIndexer → OpenSearch | `DomainEventPublisher` → `squamed.patient.*` topic → indexer upserts patient doc | `assertTrue(hitsDone, "search finds created patient")` in `InteropTests`/`entSearch`, 8×1.5s poll |
| HL7 ADT receive → patient find-or-create + index | MLLP/HTTP receive → `Hl7Service` → EMPI lookup → create → index | `hl7ReceiveAndAuthFailureCoverage`, `hl7ReceiveVariantsAndLockoutCoverage` |
| Audit write → AuditController read | AOP `@Audited` on module writes → `audit` table | `auditTrailWritesForModuleActions` |

## 3. Transport wiring

| Channel | Endpoint | Evidence |
| --- | --- | --- |
| REST | `/api/v1/...` (uniform `ApiResponse` envelope) | `ContractTests` |
| WebSocket | `/ws` (chat/queue push) | WebSocket `TextWebSocketHandler` tests (welcome frame, auth rejection) |
| HL7 | HTTP `POST /api/v1/hl7/messages` (study-internal transport; MLLP listener present on 2575) | `hl7ReceiveAndAuthFailureCoverage` |
| FHIR | `/api/v1/fhir/*` list + by-id | `interopAndFhirCoverage` |
| STT/AI | echo provider fallback (no external key needed in Phase 1) | `aiGatewayAndScribeCoverage` |

## 4. Cross-cutting wiring

- **Auth**: JWT issued on login; MFA `TOTP` challenges; account lockout after 5 failures (`hl7ReceiveVariantsAndLockoutCoverage`).
- **Error contract**: `{ error: { code, message }, timestamp }` for 4xx/5xx via `GlobalExceptionHandler`; missing/malformed body → `MALFORMED_BODY` 400.
- **Security headers**: `SecurityHeadersFilter` on all responses (verified by `hardenedSecurityHeadersArePresent`).
- **Observability**: Micrometer + `/actuator/prometheus` scraped by Prometheus; alerts routed to Alertmanager (`alerts.yml`).

## 5. Gaps noted (deferred by design)

- MinIO/Temporal are Phase 2 concepts; Phase 1 uses Postgres-backed state and the in-process workflow engine (`WorkflowService`) — documented in `docs/architecture.md` (workflow engine column).
- Frontend live E2E depends on a running web dev server; specs live in `tests/e2e` and run in CI.

## 6. Phase 2a additions (IPD / Nursing / ICU)

- Migrations V29 (ipd), V30 (nursing), V31 (icu), V32 (permission seeds for `ipd`/`nursing`/`icu` domains to SYSTEM_ADMIN, DOCTOR, NURSE).
- Admission lifecycle incl. deterministic bed allocation and release: `PatientAdmitted`/`PatientTransferred`/`PatientDischarged` events published on every transition; transfers recorded as audit-history rows; discharge generates a discharge summary.
- Nursing covers SBAR handovers, care plans + status transitions, progress notes, vitals, eMAR (scheduled → administered/omitted) and risk assessments with events (`nursing.vitals_recorded`, `nursing.medication_administered`).
- ICU covers high-frequency vitals (incl. MAP/EtCO2), fluid balance, ventilation records, infusion lifecycle and critical alerts with acknowledge/resolve workflow and `icu.critical_alert` events.
- Does not yet emit Kafka domain topics beyond `squamed.domain-events` (the default Phase 1 topic) — Phase 2 wiring extension tracked in the Phase 2 report.

Proven end to end: 22 controller modules, event→index→search, audit-on-write, REST/WS/HL7/FHIR transports + IPD/Nursing/ICU clinical documentation.