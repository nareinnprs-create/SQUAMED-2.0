# API + EVENT CATALOG — unified inventory
**Rule:** the chunk file is the contract at build time; this catalog is the whole-system index. Enforcement is **not** a "catalog test": what actually fails CI on contract drift is the PIT `openApiSnapshotMatchesLiveContract` (committed snapshot `docs/contracts/openapi-v1.json` must byte-match live `/api-docs` minus `servers`) and `openApiExposesAllModuleSurfaces`. Adding/changing an endpoint or event = update code + **regenerate the snapshot and `@squamed/api-client` codegen in the same PR** (protocol: `docs/development/contract-codegen.md`).

## 1. API conventions (see 01-ENGINEERING-STANDARDS §3 and `docs/api/openapi-style-guide.md`)
REST base path **`/api/v1`**. Success envelope: `{data, success, meta?}` (`ApiResponse.ok`; `meta` omitted on simple results). Errors are a **separate envelope**: `{error:{code, message, details}, timestamp}` (`GlobalExceptionHandler`).
- **Offset pagination** (catalogs/child lists): `?page=&size=` → `meta: {page, size, total, pages}`. **Cursor pagination** (append-only streams, currently `/audit/logs`): `?limit=&cursor=` → `meta: {limit, hasNext, nextCursor}`; invalid cursor → 400 `INVALID_CURSOR`. Patient search and child lists are deliberately unpaged.
- **Idempotency**: `Idempotency-Key` accepted on POST/PUT/PATCH/DELETE (optional, ≤255 chars); same key + identical payload replays the stored response byte-for-byte; same key + different payload → 409 `IDEMPOTENCY_CONFLICT`; 5xx responses are not stored; nightly purge.
- **Tenant from token, never body**: JWT claim `hp` (hospital) resolved at login from `user_role`; client-supplied `hospitalId` honored only for platform admins (`hp = null`).
- **Tenancy mechanics** (`common/tenant/`): `TenantResolver.pin(principal, explicit)` pins hospital-bound users and lets admins choose; `pinOrDefault` falls back to the primary (first-created) hospital for principal-less writes, else `NO_HOSPITAL_CONFIGURED`; `TenantGuards.requireSameHospital(principal, row)` denies hospital-bound users on rows whose `hospital_id` is NULL or foreign → `AccessDeniedException("TENANT_FORBIDDEN")` (403, body code `ACCESS_DENIED`, message `TENANT_FORBIDDEN`); platform admins (`hp = null`) are intentionally unbounded. Path-based guard exists only for `/api/v1/hospital-config/hospitals/{id}/**`.
- **Error-code convention**: fixed handler codes `VALIDATION_ERROR`, `MALFORMED_BODY`, `INTERNAL_ERROR`, `ACCESS_DENIED`; domain errors are `throw IllegalArgumentException("UPPER_SNAKE_CODE")` → 400 with the **message as the code** (e.g. `TENANT_FORBIDDEN` reaches the wire as `{code:"ACCESS_DENIED", message:"TENANT_FORBIDDEN"}`); `IllegalStateException` → 409. No ad-hoc shapes.
- **Permissions**: `@RequiresPermission("<domain>:<action>:<resource>")` per endpoint (registry details in 07 §4a).

## 2. API inventory (by module — methods elided where obvious CRUD)

| Module | Endpoints |
|---|---|
| auth | `POST /auth/register` (email, password, roles, optional hospitalId), `POST /auth/login` (**field `username`** — matched against email OR phone), `POST /auth/refresh`, `POST /auth/logout`; bootstrap admin `admin@squamed.local` / `Admin@12345` (dev/PIT only) |
| patients | `POST /patients`, `GET /patients/{id}`, `PATCH /patients/{id}`, `GET /patients/search`, `POST /patients/merge`, `POST /patients/{id}/unmerge`, `/patients/{id}/identifiers`, `/patients/{id}/guardians`, `POST /empi/review/{id}:decide`, `GET /empi/review` |
| config | `/config/hospitals · locations · beds · services · price-lists · workflows (CRUD + versioning)` |
| tasks | `/tasks`, `/tasks/{id}:assign:complete:escalate`, `GET /tasks?assignee=me` |
| consents | `/consents`, `/consents/{id}:withdraw`, `/consents/{id}/signature` |
| notifications | `POST /notifications:send`, `GET /notifications`, channel opt-ins |
| scheduling | `/appointments (+cancel/reschedule/waitlist)`, `/schedules/{doctorId}/slots`, `/resources` |
| queue | `POST /queues/{deptId}/tokens`, `/tokens/{id}:call:transfer:complete`, `GET /queues/{deptId}/live` |
| encounters | `/encounters`, `/encounters/{id}/note:sign`, `/encounters/{id}/orders`, `/encounters/{id}/vitals`, `/diagnoses`, `/allergies`, `/conditions`, `/referrals` |
| prescriptions | `/prescriptions`, `/prescriptions/{id}:dispense` (pharmacy) |
| lab | `/labs/orders/{id}:collect:accession:enter-results:validate:reject`, `/labs/specimens/{barcode}`, `/labs/reports/{id}:publish`, `/labs/qc` |
| radiology | `/radiology/worklist`, `/studies/{id}:complete`, `/studies/{id}/report:sign` |
| pharmacy | `/pharmacy/indents`, `/pharmacy/dispense`, `/pharmacy/stock:adjust`, `/pharmacy/controlled:log`, `/pharmacy/expiry` |
| safety | `POST /medication-safety:check`, `GET /medication-safety/rules` |
| billing | `/billing/estimates · bills · credit-notes`, `/bills/{id}:finalize:settle`, `/payments:collect:reconcile`, `/finance/journal:export` |
| insurance | `/insurance/policies · preauth`, `/claims:assemble:submit`, `/claims/{id}/denials` |
| him | `/him/coding-queue`, `/him/queries`, `/integrity/findings:triage` |
| ipd | `/admissions (+transfer:plan-discharge:discharge)`, `/beds:board:allocate:mark-cleaning` |
| nursing | `/nursing/{admissionId}/mar · flowsheets · handover · risks` |
| surgery | `/ot/schedule`, `/ot/cases/{id}:checklist:record:close`, `/anaesthesia/{caseId}:evaluate:record:pacu` |
| bloodbank | `/bloodbank/donors · inventory · crossmatch`, `/bloodbank/issue` |
| devices | `WS /devices/stream:subscribe`, `/devices/thresholds` |
| ai | `POST /ai/command:ask:draft-action`, `/ai/rail:items`, `/ai/scribe/sessions:start:stop:draft:finalize`, `/ai/reports/{studyId}:draft`, `POST /ai/agents/{id}:propose` |
| governance | `/governance/models · prompts · agents · incidents · costs`, `POST /governance/kill-switch` |
| workforce | `/hr/rosters · leave · credentials` |
| ops | `/procurement/requisitions · pos · grns`, `/cssd/trays · cycles`, `/facilities/assets · work-orders`, `/ed/encounters` |
| group | `/group/hospitals · config:inherit · referrals`, `/patients/{id}:share-with-hospital` |
| portal | `/portal/*` (patient-scoped, own-data ABAC) |
| analytics | `/bi/dashboards · kpis · exports` |
| interop | `/fhir/*` (HAPI), `POST /abdm/*` (link/consent/push) |
| ecosystem | `/developers/apps · keys`, `/marketplace/listings · install` |
| realtime | `WS /realtime` (scoped event stream), `WS /devices/stream` |
| system | `/health`, `/ready`, `/metrics` (internal), `/audit/logs`, `/audit/verify`, `/audit/export`, `/admin/events/outbox|errors/replay` (event replay admin API) |

## 3. Event catalog (envelope v1 — wire shape validated by PIT `eventEnvelopeV1ValidatesPublishedShape` against `resources/schemas/event-envelope-v1.schema.json`)

**Envelope fields (as shipped):** `eventId, eventType, occurredAt, source, entityType, entityId, hospitalId, payload, recipientIds?, version, actorType, actorId, correlationId, causationId`. Catalog event names are PascalCase in docs; **wire `eventType` is lowercase dotted** (`patient.created`) mapped in `events/EventCatalog.kt`.

**Transport:** publishing = inject `DomainEventPublisher` and call `publish(DomainEvent)` **inside the caller's transaction** (outbox row commits with the business change). `EventDispatcher` relays outbox → **Kafka** (`squamed.domain-events` + per-type `squamed.task.*`/`squamed.notification.*` topics, live since chunk 0) and **Redis** channel `squamed.events` → WebSocket fan-out. DLQ + integration-error queue with admin replay REST API (`/api/v1/admin/events/...`), not a CLI.

| Domain | Events |
|---|---|
| patient | `PatientRegistered, PatientUpdated, PatientMerged, PatientUnmerged, AllergyRecorded, ConsentCaptured, ConsentWithdrawn` |
| access | `AppointmentCreated, AppointmentCancelled, AppointmentRescheduled, PatientCheckedIn, TokenIssued, TokenCalled, QueueEscalated` |
| clinical | `ConsultationStarted, EncounterSigned, OrderCreated, PrescriptionIssued, ResultEntered, ResultValidated, CriticalResultDetected, CriticalResultAcknowledged, ReportPublished, StudyCompleted, RadiologyReportSigned, VitalThresholdBreached` |
| medication | `DoseScheduled, MedicationDispensed, DoseAdministered, DoseOmitted, ControlledSubstanceDispensed, StockLowDetected, BatchExpiring, InteractionBlocked, InteractionOverridden` |
| revenue | `ChargeCaptured, EstimateCreated, BillFinalized, BillSettled, DepositTaken, DiscountApproved, CreditNoteIssued, PreauthRequested, PreauthApproved, PreauthRejected, ClaimSubmitted, ClaimQueried, ClaimDenied, ClaimSettled, CodingCompleted, IntegrityFindingDetected, IntegrityFindingResolved, PaymentReconciled` |
| ipd | `AdmissionCreated, PatientAdmitted, BedAssigned, PatientTransferred, CarePlanUpdated, SurgeryScheduled, ChecklistStageCompleted, ProcedureCompleted, ImplantRecorded, PACUDischargeReady, CrossmatchRequested, CrossmatchIssued, TransfusionReactionReported, DietOrderPlaced, DischargePlanned, PatientDischarged, DischargeSummarySigned, HousekeepingTaskCreated` |
| task/ops | `TaskCreated, TaskAssigned, TaskCompleted, TaskEscalated, ConfigChanged, POIssued, GoodsReceived, SterilizationCycleCompleted, WorkOrderCreated, WorkOrderCompleted, EDEncounterClosed, ReferralCreated, ReferralAccepted, RecordSharedConsented, TeleconsultStarted, TeleconsultCompleted, RosterPublished, CredentialExpiring` |
| ai | `AiScribeSessionStarted, AiScribeSessionCompleted, AiDraftCreated, AiDraftSigned, AiActionProposed, AiActionApproved, AiActionRejected, AiActionExpired, AiIncidentReported, CostCapReached, AttentionItemRaised, AttentionItemCleared` |
| platform | `NotificationDispatched, NotificationFailed, AuditChainVerified, AuditChainBreach, FailoverInitiated, FailoverCompleted, ErasureCompleted, MaintenanceWindowStarted` |

**Consumer rules:** every consumer idempotent by `event.id`; version bumps are additive (new optional fields); breaking change = new `type@v2` with dual-publish for one release cycle.
