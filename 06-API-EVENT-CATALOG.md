# API + EVENT CATALOG — unified inventory
**Rule:** the chunk file is the contract at build time; this catalog is the whole-system index. Adding an endpoint/event = add here + in the chunk, or CI fails the catalog test.

## 1. API conventions (see 01-ENGINEERING-STANDARDS §3)
REST `/v1` · envelope `{data, error{code,message,details}, meta{page}}` · cursor pagination · `Idempotency-Key` on all POST · tenant from token, never body · scope annotations per endpoint.

## 2. API inventory (by module — methods elided where obvious CRUD)

| Module | Endpoints |
|---|---|
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
| system | `/health`, `/ready`, `/metrics` (internal), `/audit/query`, `/admin/retention · legal-holds · erasure` |

## 3. Event catalog (envelope v1: `id, tenant, type, version, occurredAt, actor{type,id}, subject{type,id}, payload, correlationId, causationId`)

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
