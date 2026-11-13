# SQUAMED BUILD BLUEPRINT
## Complete Development Execution Plan
### Batch-by-Batch, Sub-Batch-by-Sub-Batch

**Derived from:** SQUAMED Master Product, Technology & Enterprise Blueprint 2026-2030
**Architecture Pattern:** Modular Monolith (extract services only when justified)
**Primary Tech Stack:** TypeScript, React, Next.js, Kotlin, Spring Boot, PostgreSQL, Redis, Kafka, Temporal, OpenSearch, ClickHouse, Python (AI)

---

# PHASE 1: FOUNDATION MVP

**Goal:** Create a genuinely usable hospital platform.
**Master Blueprint Reference:** Section 106

## BATCH 1.1 — Repository and Development Infrastructure

### 1.1.1 — Monorepo Setup
- Initialize monorepo (Turborepo or Nx)
- Root package.json with workspace protocol
- Root tsconfig.json with path aliases
- .gitignore, .editorconfig, LICENSE
- Create full directory tree per repository structure (Section 97)

### 1.1.2 — Frontend Scaffolding (apps/web)
- Next.js 14+ with App Router
- TypeScript strict mode
- Tailwind CSS with Squamed design tokens
- ESLint + Prettier
- Environment config (.env.local, .env.development, .env.staging, .env.production)
- Path aliases, base layout, health check page

### 1.1.3 — Backend Scaffolding (backend/squamed-core)
- Kotlin Spring Boot 3+
- Multi-module Gradle build with strict domain boundaries
- All domain modules (see full module list below)
- Flyway migrations, Swagger/OpenAPI, Actuator, CORS
- Request correlation ID propagation

### 1.1.4 — Database Infrastructure
- PostgreSQL 15+ docker-compose
- Base entity classes (BaseEntity, SoftDeleteEntity, TenantEntity, HospitalEntity)
- UUID primary key strategy
- Schema layout: public, identity, patient, hospital_config, master_data, clinical, appointment, reception, queue, orders, laboratory, pharmacy, billing, tasks, communication, notifications, workflow, audit, ai
- Extensions: uuid-ossp, pgcrypto, pg_trgm

### 1.1.5 — Redis Infrastructure
- Redis 7+ docker-compose
- Use cases: session store, rate limiting, cache, real-time presence, Pub/Sub, distributed locking
- Key naming: squamed:{domain}:{entity}:{id}

### 1.1.6 — Kafka Event Backbone
- Kafka via docker-compose (KRaft mode)
- Topic naming: squamed.{domain}.{event-type}
- Base event envelope schema
- Producer/consumer config, DLQ, Schema Registry
- All Phase 1 topics from domain event catalog

### 1.1.7 — OpenSearch Setup
- OpenSearch 2+ docker-compose
- Index templates: patient search, global search, audit, analytics
- Medical terminology tokenizer, multilingual analyzers, phonetic matching

### 1.1.8 — Object Storage Setup
- MinIO docker-compose
- Bucket structure: squamed-documents, squamed-imaging, squamed-exports, squamed-ai
- Pre-signed URLs, lifecycle policies

### 1.1.9 — Temporal Workflow Engine
- Temporal server docker-compose with UI
- Namespace: squamed-development
- Base workflow activity definitions
- Spring Boot Temporal worker config

### 1.1.10 — Observability Foundation
- OpenTelemetry Collector, Prometheus, Grafana
- Dashboards: app health, DB, Kafka, API latency, error rates
- Structured JSON logging, correlation ID propagation, tracing

### 1.1.11 — CI/CD Pipeline Foundation
- GitHub Actions: lint, typecheck, test, build, deploy-staging, deploy-production
- Branch protection, Docker build, secrets management

### 1.1.12 — Testing Infrastructure
- Vitest (frontend), JUnit 5 + Mockito (backend)
- Testcontainers (PostgreSQL, Redis, Kafka)
- Playwright (E2E), factory pattern, 80% coverage threshold

### 1.1.13 — Development Documentation
- README, CONTRIBUTING, architecture docs, API conventions, ADR template

---

## BATCH 1.2 — Design System Foundation (Section 86, 87, 91)

### 1.2.1 — Design Tokens
- Color tokens: Canvas #F7F8F6, Surface #FFFFFF, Secondary #EEF1EF, Primary text #15191A, Secondary text #596267, Border #DDE2DF, Identity #087F7A, Success #2E7D32, Warning #F57C00, Critical #C62828, Information #1565C0
- Typography: Inter, 10-36px scale, weights 400-700
- Spacing: 2-128px scale, radius, shadows, breakpoints, z-index, transitions

### 1.2.2 — Theme Configuration (Three Visual Modes)
- Clinical (light, calm, readable), Enterprise (dense, analytical), Command (higher contrast, immersive)
- CSS custom properties, React context, toggle, persistence
- NEVER 100% dark mode

### 1.2.3 — Primitives Library
- Button, Input, Select, Checkbox, Radio, Toggle, Textarea
- DatePicker, TimePicker, DateTimePicker, Range, Slider, FileUpload
- Modal, Drawer, Popover, Tooltip, Toast, Tabs, Accordion
- Card, Badge, Avatar, Divider, Skeleton, Spinner, Progress, Alert
- Breadcrumb, Pagination, Table, DataGrid, Command Palette
- Sidebar, Topbar, StatusIndicator, PriorityIndicator, Timeline, Chip
- All with: keyboard nav, screen reader, focus visible, reduced motion, color-independent

### 1.2.4 — Layout Components
- AppShell, PageHeader, ContentPanel, SidePanel, SplitView
- 12-col grid, Container, Stack, Responsive utilities

### 1.2.5 — Domain-Specific Components
- PatientCard, PatientBanner, EncounterTimeline, VitalSignsChart
- MedicationList, AllergyBadge, OrderCard, ResultCard, BillingSummary
- QueueStatusWidget, BedMap, TokenDisplay, ClinicalNoteEditor
- OrderEntryForm, PrescriptionBuilder, LabResultViewer, ChargeLineItem

---

## BATCH 1.3 — Authentication and Authorization (Section 4.3, 67)

### 1.3.1 — Identity Domain Model
- Tables: user, user_profile, role, permission, role_permission, user_role, user_hospital, session, mfa_setup
- Entity classes, repositories

### 1.3.2 — Authentication Service
- Register, Login, Logout, Refresh, Password Reset
- MFA (TOTP, SMS, Email), JWT (15min access + 7 day refresh)
- Token blacklisting, bcrypt, rate limiting, device tracking

### 1.3.3 — Authorization Engine (RBAC + ABAC)
- Role hierarchy, permission middleware, ABAC policies
- Permission annotations, method-level security
- UI hooks: usePermission, useHasRole
- Redis caching, evaluation logging

### 1.3.4 — Frontend Auth Integration
- Auth context, Login/MFA/Reset pages, Protected routes
- Token refresh interceptor, session expiry

---

## BATCH 1.4 — Hospital Configuration Engine (Section 4.1)

### 1.4.1 — Hospital Configuration Domain Model
- Tables: hospital, campus, building, floor, department, specialty, ward, room, bed, consultation_room, ot, icu_unit, diagnostic_unit, pharmacy, working_hours, holiday, shift_rule, service

### 1.4.2 — Hospital Configuration API
- Full CRUD for all entities, hierarchy endpoint, bulk import

### 1.4.3 — Hospital Configuration UI
- Setup wizard, hierarchy management, bed grid, working hours editor, holiday calendar, service catalog

---

## BATCH 1.5 — Master Data Management (Section 4.2)

### 1.5.1 — Master Data Domain Model
- Tables: drug, procedure, diagnosis, lab_test, imaging_procedure, service, price_list, price_list_item, vendor, insurance_tpa, equipment, supplier

### 1.5.2 — Master Data API and UI
- CRUD for all entities, search, import/export, auto-complete for clinical use

---

## BATCH 1.6 — Patient Identity and EMPI (Section 4.3, 4.4, 2.3)

### 1.6.1 — Patient Domain Model
- Tables: patient, patient_identifier, contact, address, emergency_contact, guardian, dependent, insurance_policy, allergy, condition, medication_history, family_history, social_history, document
- EMPI matching algorithm, merge/unmerge, MRN generation

### 1.6.2 — Patient Service API
- Full CRUD, EMPI merge/unmerge, global search
- Events: PatientRegistered, PatientUpdated, PatientMerged, PatientUnmerged

### 1.6.3 — Patient UI Components
- Registration, search, list, detail, demographics, allergies, conditions, medications, insurance, documents

---

## BATCH 1.7 — Application Shell and Navigation (Section 88, 89, 90, 92)

### 1.7.1 — Application Shell
- Collapsible sidebar, topbar (hospital, search, AI, notifications, tasks, comms, user)
- Responsive (desktop/tablet/mobile), keyboard shortcuts, patient context bar

### 1.7.2 — Navigation Structure
- Sidebar: Dashboard, Patients, Appointments, Reception, Consultation, Orders, Lab, Pharmacy, Billing, Tasks, Communication, Reports, Admin
- Role-based visibility, badges, recently accessed, pinned

### 1.7.3 — Universal Command Bar
- Cmd+K palette: search, navigation, actions, queries

### 1.7.4 — AI Intelligence Rail
- Collapsible right panel with contextual AI insights

### 1.7.5 — Patient Context Bar
- Persistent bar: photo, name, MRN, DOB, age, gender, allergies, status

---

## BATCH 1.8 — Appointment Management (Section 9)

### 1.8.1 — Appointment Domain Model
- Tables: schedule, appointment, schedule_exception
- Slot generation, conflict detection, walk-in support

### 1.8.2 — Appointment API
- CRUD, cancel, reschedule, check-in, start, complete, no-show
- Events: AppointmentCreated, AppointmentCancelled, AppointmentRescheduled, PatientCheckedIn

### 1.8.3 — Appointment UI
- Booking form, calendar view (day/week/month), list view, schedule management

---

## BATCH 1.9 — Reception and Front Office (Section 6)

### 1.9.1 — Reception Domain Model
- Tables: check_in, visitor, payment_initiation

### 1.9.2 — Reception API
- Check-in, queue assign/transfer, visitor management, labels, receipts
- Events: PatientCheckedIn, VisitorRegistered, VisitorCheckedOut

### 1.9.3 — Reception UI
- Dashboard, patient lookup, check-in flows, visitor management

---

## BATCH 1.10 — Queue Engine and Display (Section 6, 8)

### 1.10.1 — Queue Domain Model
- Tables: queue_definition, queue_entry, queue_call
- Priority algorithm, position computation, escalation rules

### 1.10.2 — Queue API
- CRUD, call next, complete, transfer, cancel
- Events: TokenIssued, PatientCalled, PatientQueueCompleted, QueueEscalated

### 1.10.3 — Display System
- Auto-refreshing display page: current token, counter, next 5, department, clock

### 1.10.4 — Voice Announcement Engine
- TTS (Web Speech API + pluggable), multi-language, configurable

### 1.10.5 — Queue Management UI
- Queue dashboard, add/call/transfer/cancel/complete, analytics

---

## BATCH 1.11 — OPD / Ambulatory Platform (Section 9)

### 1.11.1 — OPD Domain Model
- Tables: encounter, vital_sign, physical_examination, note, referral, care_plan
- Encounter lifecycle, provenance tracking

### 1.11.2 — OPD API
- Encounter CRUD, vitals, examination, notes (create, update, sign)
- Events: ConsultationStarted, ConsultationCompleted, VitalsRecorded, NoteCreated, NoteSigned, ReferralCreated

### 1.11.3 — OPD Consultation Workspace UI
- Doctor workspace: patient queue, consultation view (context, documentation, actions)
- Chief complaint, HPI, vitals, examination, diagnosis, notes, orders, prescription, referral

---

## BATCH 1.12 — Orders Management (Section 9, 13)

### 1.12.1 — Orders Domain Model
- Tables: order, order_item
- Order number generation, batch ordering

### 1.12.2 — Orders API
- CRUD, submit, cancel, complete
- Events: OrderCreated, OrderSubmitted, OrderCompleted, OrderCancelled

### 1.12.3 — Orders UI
- Order entry (type, search, priority, multi-item), order list/detail, pending board

---

## BATCH 1.13 — Laboratory Information System (Section 15)

### 1.13.1 — Laboratory Domain Model
- Tables: specimen, test_result, result_attachment, critical_result

### 1.13.2 — Laboratory API
- Specimen lifecycle, result entry/validate/release, critical result reporting
- Events: SampleCollected, SampleReceived, ResultValidated, CriticalResultDetected, ResultReleased

### 1.13.3 — Laboratory UI
- Dashboard, sample tracking, result entry (numeric flags, reference ranges), critical alerts

---

## BATCH 1.14 — Pharmacy (Section 19)

### 1.14.1 — Pharmacy Domain Model
- Tables: prescription, prescription_item, dispensing, formulary, formulary_item, drug_interaction_check
- Drug interaction, allergy cross-reference, dose range checks

### 1.14.2 — Pharmacy API
- Prescription lifecycle, dispensing, formulary, interaction/allergy checks
- Events: PrescriptionCreated, PrescriptionVerified, MedicationDispensed

### 1.14.3 — Pharmacy UI
- Dashboard, dispensing workflow, alerts, formulary browser

---

## BATCH 1.15 — Billing and Revenue Cycle (Section 25, 26)

### 1.15.1 — Billing Domain Model
- Tables: charge, invoice, invoice_item, payment, estimate, deposit, discount

### 1.15.2 — Billing API
- Charges, invoices, payments, estimates, deposits, discounts, revenue reports
- Events: ChargeCreated, InvoiceGenerated, PaymentReceived, RefundProcessed

### 1.15.3 — Billing UI
- Dashboard, charge entry, invoice management, payment collection, reports

---

## BATCH 1.16 — Universal Task Engine (Section 50)

### 1.16.1 — Task Domain Model
- Tables: task, task_comment, task_attachment
- Lifecycle, escalation, polymorphic context attachment

### 1.16.2 — Task API
- CRUD, assign, start, complete, escalate, hold, cancel, comments, attachments
- Events: TaskCreated, TaskAssigned, TaskStarted, TaskCompleted, TaskEscalated, TaskCancelled

### 1.16.3 — Task UI
- Kanban/list, detail, creation, assignment, quick actions, context-linked

---

## BATCH 1.17 — Communication (Section 49)

### 1.17.1 — Communication Domain Model
- Tables: channel, channel_member, message, message_read, message_attachment
- Channel types: direct, group, department, hospital, patient-context, encounter-context

### 1.17.2 — Communication API
- Channel CRUD, messages, unread counts, WebSocket

### 1.17.3 — Communication UI
- Channels, messaging, real-time, typing indicators, file sharing

---

## BATCH 1.18 — Notifications (Section 49, 52)

### 1.18.1 — Notification Domain Model
- Tables: notification_template, notification, notification_preference

### 1.18.2 — Notification Service and API
- Kafka consumer, list/unread/mark-read/preferences, WebSocket push

### 1.18.3 — Notification UI
- Bell icon, dropdown, page, preferences, toasts

---

## BATCH 1.19 — Workflow Engine (Section 51, 52)

### 1.19.1 — Workflow Domain Model
- Tables: definition, instance, step_instance
- Step types: task, approval, notification, escalation, action, condition, wait

### 1.19.2 — Workflow API
- Definition CRUD + activate/deactivate, trigger, instance management, approvals

### 1.19.3 — Workflow UI
- Definition list/editor, instance detail, pending approvals

---

## BATCH 1.20 — Real-Time Event Fabric (Section 52)

### 1.20.1 — Event Infrastructure
- DomainEventPublisher, Kafka producer/consumer framework, complete event catalog

### 1.20.2 — WebSocket/SSE Gateway
- Endpoints: queues, notifications, communication, patient, dashboard
- Auth, heartbeat, reconnection, permission-based filtering

---

## BATCH 1.21 — Audit Architecture (Section 70)

### 1.21.1 — Audit Domain Model
- Table: audit_log (append-only, hash chain, monthly partitioned)

### 1.21.2 — Audit Service
- AOP aspect, async writing, search API, export, dashboard

### 1.21.3 — Audit UI
- Search page, entity audit trail, user activity, dashboard

---

## BATCH 1.22 — Patient 360 (Section 5.1)

### 1.22.1 — Patient 360 Aggregation API
- Single endpoint aggregating all patient data, timeline, caching

### 1.22.2 — Patient 360 UI
- Signature screen: all patient data sections, timeline, quick actions, print/export

---

## BATCH 1.23 — AI Gateway and Command Bar (Section 56, 61, 63, 89)

### 1.23.1 — AI Gateway Domain Model
- Tables: model_config, agent_config, interaction_log, tool_config

### 1.23.2 — AI Gateway API
- Chat, command processing, model/agent CRUD, streaming

### 1.23.3 — AI Command Bar UI
- Cmd+K with AI, chat panel, streaming responses

---

## BATCH 1.24 — AI Scribe (Section 14)

### 1.24.1 — AI Scribe Backend
- Session management, pipeline: record -> STT -> speaker separation -> generate -> draft -> sign

### 1.24.2 — AI Scribe UI
- Recording controls, live transcript, draft editor, sign & save

---

## BATCH 1.25 — Basic Analytics (Section 83)

### 1.25.1 — Analytics Data Pipeline
- Kafka -> ClickHouse, daily aggregation tables, Redis counters

### 1.25.2 — Analytics API
- Overview, patients, encounters, appointments, queue, lab, billing analytics

### 1.25.3 — Analytics UI
- Dashboard, per-domain analytics, report builder, export

---

## BATCH 1.26 — FHIR/HL7 Foundation (Section 65)

### 1.26.1 — FHIR Foundation
- FHIR R4 resources: Patient, Practitioner, Organization, Encounter, Condition, Observation, MedicationRequest, ServiceRequest, AllergyIntolerance, DiagnosticReport

### 1.26.2 — HL7v2 Foundation
- Parser/serializer, ADT/ORM/ORU/SIU message types, MLLP server

### 1.26.3 — Interoperability Configuration UI
- Integration management, connection config, health dashboard

---

## BATCH 1.27 — Testing and Quality Assurance (Section 100)

### 1.27.1 — Backend Tests
- Unit, integration (Testcontainers), contract tests, 80% coverage

### 1.27.2 — Frontend Tests
- Unit, integration, E2E (Playwright) for all critical workflows

### 1.27.3 — Security Testing
- OWASP Top 10, auth/authz bypass, injection, XSS, CSRF

---

## BATCH 1.28 — Deployment and DevOps (Section 72, 98, 99)

### 1.28.1 — Docker Configuration
- Dockerfiles (multi-stage), docker-compose (all services)

### 1.28.2 — Kubernetes Manifests
- Helm/Kustomize, ConfigMaps, Secrets, Ingress, HPA

### 1.28.3 — CI/CD Pipeline (Complete)
- Lint -> Test -> Build -> Deploy staging -> Smoke test -> Deploy production -> Health check

### 1.28.4 — Monitoring and Alerting
- Grafana dashboards, alert rules, PagerDuty/Slack integration

---

# PHASE 1 COMPLETE

**Squamed now has:** Identity, Patient, EMPI, Hospital Config, MDM, RBAC/ABAC, Patient 360, Appointments, Reception, Queue+Display+Voice, OPD, EMR, Orders, Lab, Pharmacy, Billing, Tasks, Communication, Notifications, Workflow, Events, Audit, AI Gateway+Command Bar, AI Scribe, Analytics, FHIR/HL7, Design System, Testing, CI/CD, Monitoring.

---

# PHASE 2: HOSPITAL OPERATIONS

**Goal:** Full inpatient, surgical, emergency, and operational capabilities.
**Master Blueprint Reference:** Section 107

## BATCH 2.1 — Inpatient Platform (IPD) (Section 10)

### 2.1.1 — Admission Domain Model
- Tables: admission, admission_transfer, discharge_summary, attendant
- Admission types: emergency, elective, day_care, transfer, observation
- Bed allocation algorithm, transfer tracking, discharge summary generation

### 2.1.2 — Admission API
- Admission CRUD, bed allocation, transfer, discharge
- Events: PatientAdmitted, PatientTransferred, PatientDischarged

### 2.1.3 — Admission UI
- Admission form, bed selection (visual ward map), ward census, transfer, discharge workflow

---

## BATCH 2.2 — Nursing (Section 20)

### 2.2.1 — Nursing Domain Model
- Tables: nursing_assessment, nursing_careplan, nursing_note, nursing_handover, vitals_entry, medication_administration_record, risk_assessment
- SBAR handover structure

### 2.2.2 — Nursing API
- Assessment, care plans, notes, handover, vitals, eMAR, risk assessments

### 2.2.3 — Nursing UI
- Ward patient list, vitals entry, care plans, handover, eMAR (barcode), bedside documentation (tablet)

---

## BATCH 2.3 — ICU / Critical Care (Section 12)

### 2.3.1 — ICU Domain Model
- Tables: icu_vitals_entry, icu_fluid_balance, icu_ventilation_record, icu_infusion_record, icu_critical_alert

### 2.3.2 — ICU API
- High-frequency vitals, fluid balance, ventilation, infusion, critical alerts

### 2.3.3 — ICU ICU UI
- ICU dashboard (all beds real-time), vitals chart, fluid balance, ventilation, alerts

---

## BATCH 2.4 — Emergency and Trauma (Section 11)

### 2.4.1 — Emergency Domain Model
- Tables: emergency_registration, triage_assessment, trauma_workflow
- Triage scales (ESI), trauma workflow (primary/secondary survey)

### 2.4.2 — Emergency API
- Emergency registration, triage, trauma workflow, STAT orders, dashboard

### 2.4.3 — Emergency UI
- Fast-track registration, triage form, ED dashboard, resuscitation view, escalation

---

## BATCH 2.5 — OT / Surgery (Section 21)

### 2.5.1 — Surgical Scheduling Domain Model
- Tables: surgery_schedule, ot_allocation, surgical_team, instrument_tracking, implant_record, pre_op_checklist, intra_op_record, post_op_record

### 2.5.2 — Surgical Scheduling API
- Surgery CRUD, OT allocation, team assignment, checklists, instruments, implants
- Events: SurgeryScheduled, SurgeryStarted, SurgeryCompleted

### 2.5.3 — Surgical Scheduling UI
- OT schedule board (Gantt), booking form, pre/intra/post-op documentation, instruments

---

## BATCH 2.6 — Anaesthesia (Section 22)

### 2.6.1 — Anaesthesia Domain Model
- Tables: anaesthesia_evaluation, anaesthesia_record, intra_op_monitoring, anaesthesia_medication, recovery_record, pacu_record

### 2.6.2 — Anaesthesia API
- Pre-anaesthesia evaluation, intra-operative record, medication, recovery/PACU

### 2.6.3 — Anaesthesia UI
- Evaluation form, intra-op timeline, medication entry, recovery scoring

---

## BATCH 2.7 — Blood Bank (Section 18)

### 2.7.1 — Blood Bank Domain Model
- Tables: blood_donor, blood_unit, blood_grouping, crossmatch, blood_reservation, blood_issue, transfusion_record, transfusion_reaction

### 2.7.2 — Blood Bank API
- Donor management, inventory, crossmatch, reservation, issue, transfusion, reactions

### 2.7.3 — Blood Bank UI
- Inventory dashboard, donor management, crossmatch interface, issue workflow, reactions

---

## BATCH 2.8 — PACS / RIS (Sections 16, 17)

### 2.8.1 — PACS/RIS Domain Model
- Tables: imaging_study, imaging_order, imaging_series, imaging_report, modality_worklist, critical_finding

### 2.8.2 — PACS/RIS API
- Study lifecycle, DICOM endpoints, report CRUD, critical findings, external import

### 2.8.3 — PACS/RIS UI
- Radiology worklist, report creation, DICOM viewer integration, critical finding alert

---

## BATCH 2.9 — Procurement and Supply Chain (Section 29)

### 2.9.1 — Procurement Domain Model
- Tables: purchase_requisition, purchase_order, rfq, rfq_response, goods_receipt, stock_transfer, consumption_record, reorder_config

### 2.9.2 — Procurement API
- Requisition -> PO -> receiving, RFQ, stock transfers, consumption, reorder alerts

### 2.9.3 — Procurement UI
- Requisition/PO management, goods receiving, inventory dashboard, vendor management

---

## BATCH 2.10 — Inventory (Section 29)

### 2.10.1 — Inventory Domain Model
- Tables: inventory_stock, inventory_batch, stock_movement, stock_adjustment, expiry_tracking, reorder_point

### 2.10.2 — Inventory API
- Stock levels, receipt/dispense/transfer/adjust/waste, expiry alerts, stock count

### 2.10.3 — Inventory UI
- Stock dashboard, batch management, adjustment, count, expiry/reorder alerts

---

## BATCH 2.11 — CSSD (Section 30)

### 2.11.1 — CSSD Domain Model
- Tables: instrument_set, tray, sterilization_cycle, instrument_issue, instrument_return

### 2.11.2 — CSSD API
- Instrument inventory, trays, sterilization cycles, issue/return, expiry tracking

### 2.11.3 — CSSD UI
- Inventory, tray editor, sterilization recording, issue/return, expiry monitoring

---

## BATCH 2.12 — Facilities and Asset Management (Section 31)

### 2.12.1 — Facilities Domain Model
- Tables: asset, maintenance_schedule, work_order, vendor_contract, spare_parts, calibration_record, equipment_downtime

### 2.12.2 — Facilities API
- Asset registry, preventive maintenance, work orders, vendor contracts, calibration

### 2.12.3 — Facilities UI
- Asset registry, work order management, maintenance calendar, downtime dashboard

---

## BATCH 2.13 — Housekeeping and Hospitality (Section 32)

### 2.13.1 — Housekeeping Domain Model
- Tables: housekeeping_task, cleaning_schedule, room_turnover, linen_tracking, quality_check

### 2.13.2 — Housekeeping API
- Task CRUD, schedules, room turnover, quality checks, SLA monitoring

### 2.13.3 — Housekeeping UI
- Task board, room status view, quality check form, SLA dashboard

---

## BATCH 2.14 — Dietary (Section 33)

### 2.14.1 — Dietary Domain Model
- Tables: diet_order, meal_plan, meal_delivery, dietary_inventory, dietary_waste

### 2.14.2 — Dietary API
- Diet orders (allergy-aware), meal planning, kitchen workflow, delivery, nutrition

### 2.14.3 — Dietary UI
- Diet order form, kitchen dashboard, delivery tracking, nutrition reports

---

## BATCH 2.15 — Ambulance (Section 34)

### 2.15.1 — Ambulance Domain Model
- Tables: ambulance, ambulance_dispatch, ambulance_crew, ambulance_trip, gps_tracking_log

### 2.15.2 — Ambulance API
- Fleet CRUD, dispatch, GPS tracking, crew assignment, handover, billing

### 2.15.3 — Ambulance UI
- Fleet dashboard, dispatch management, GPS map view, trip history

---

## BATCH 2.16 — HR / Workforce OS (Section 36)

### 2.16.1 — Workforce Domain Model
- Tables: employee, credential, roster, shift, leave_request, attendance, training_record, competency, skill_matrix, staffing_requirement

### 2.16.2 — Workforce API
- Employee CRUD, credentials, roster, shifts, leave, attendance, training, staffing

### 2.16.3 — Workforce UI
- Employee directory, roster calendar, leave management, attendance, credentials, staffing analytics

---

## BATCH 2.17 — Insurance / TPA / Claims (Section 28)

### 2.17.1 — Insurance Domain Model
- Tables: insurance_eligibility_check, preauthorization, claim, claim_item, claim_denial, claim_query, claim_settlement

### 2.17.2 — Insurance API
- Eligibility, preauthorization, claim lifecycle, denial management, settlement

### 2.17.3 — Insurance UI
- Eligibility check, preauth workflow, claim management, denial management, settlement

---

## BATCH 2.18 — Patient App / Portal (Section 46)

### 2.18.1 — Patient Portal API
- Patient auth, registration, appointments, check-in, records, payment, messaging, notifications

### 2.18.2 — Patient Portal UI
- Registration/login, dashboard, booking, check-in, records, payment, messaging, wayfinding

---

## BATCH 2.19 — Doctor App (Section 47)

### 2.19.1 — Doctor Mobile API
- Patient 360, appointments, EMR, scribe, orders, results, messaging, tasks, rounds, notifications

### 2.19.2 — Doctor Mobile UI (React Native)
- Dashboard, patient list, Patient 360, consultation, scribe, orders, messaging, tasks

---

## BATCH 2.20 — Nurse App (Section 48)

### 2.20.1 — Nurse Mobile API
- Patient list, bed list, tasks, vitals, eMAR, handover, escalation, communication

### 2.20.2 — Nurse Mobile UI (React Native)
- Ward view, bedside view, vitals entry, eMAR (barcode), tasks, handover, escalation

---

## BATCH 2.21 — Kiosk Platform (Section 8)

### 2.21.1 — Kiosk API
- Self-registration, check-in, queue, payment, reports, wayfinding

### 2.21.2 — Kiosk UI
- Large touch targets, minimal steps, multi-language, payment integration

---

## BATCH 2.22 — Wayfinding (Section 8)

### 2.22.1 — Wayfinding Domain Model
- Tables: wayfinding_node, wayfinding_edge, wayfinding_landmark

### 2.22.2 — Wayfinding API
- Route calculation, landmark search, floor plan data, turn-by-turn

### 2.22.3 — Wayfinding UI
- Interactive floor plan, destination search, accessibility-aware routing

---

## BATCH 2.23 — Security and Access (Section 35)

### 2.23.1 — Security Domain Model
- Tables: access_control_rule, security_incident, lockdown_event

### 2.23.2 — Security API
- Access rules, incidents, lockdown, visitor access, event logging

### 2.23.3 — Security UI
- Access dashboard, incident form, lockdown interface, visitor validation

---

## BATCH 2.24 — Consent and Privacy Engine (Section 69)

### 2.24.1 — Consent Domain Model
- Tables: consent_type, consent_record, consent_audit

### 2.24.2 — Consent API
- Consent CRUD, verification, withdrawal, audit trail

### 2.24.3 — Consent UI
- Consent recording (digital signature), status display, verification prompt

---

## BATCH 2.25 — Enhanced Tasks and Workflow

### 2.25.1 — Task Engine Enhancements
- Templates, bulk operations, SLA dashboard, dependencies, time tracking

### 2.25.2 — Workflow Engine Enhancements
- Visual designer, conditional branching, parallel steps, timer triggers, templates

---

## BATCH 2.26 — International Patient Management (Section 44)

### 2.26.1 — International Patient Domain Model
- Tables: international_enquiry, medical_tourism_case, visa_coordination, interpreter_request

### 2.26.2 — International Patient API
- Enquiry management, case review, visa, interpreter, international billing

### 2.26.3 — International Patient UI
- Enquiry pipeline, case review, visa tracker, interpreter booking, multi-currency billing

---

## BATCH 2.27 — Allied Health (Section 24)

### 2.27.1 — Allied Health Domain Model
- Tables: allied_health_session, allied_health_plan, allied_health_progress_note

### 2.27.2 — Allied Health API
- Session CRUD (physio, dietetics, OT, speech, respiratory, rehab, social work, psych)

### 2.27.3 — Allied Health UI
- Type-specific session forms, treatment plans, progress notes, outcomes

---

## BATCH 2.28 — Health Information Management (Section 27)

### 2.28.1 — HIM Domain Model
- Tables: medical_record, chart_deficiency, coding_record, release_of_information, amendment_record

### 2.28.2 — HIM API
- Medical records, coding, CDI flags, chart deficiencies, ROI, amendments

### 2.28.3 — HIM UI
- Record list, coding interface, CDI flags, deficiency dashboard, ROI management

---

## BATCH 2.29 — Enhanced Billing (Full Revenue Cycle)

### 2.29.1 — Billing Enhancements
- Package billing, auto room charges, professional fees, discharge billing, multi-currency

### 2.29.2 — Revenue Cycle API
- Packages, auto charges, discharge billing, reconciliation, leakage detection

### 2.29.3 — Revenue Cycle UI
- Package management, discharge billing review, leakage alerts, reconciliation dashboard

---

# PHASE 2 COMPLETE

**Squamed now has:** IPD, Nursing, ICU, Emergency, OT/Surgery, Anaesthesia, Blood Bank, PACS/RIS, Procurement, Inventory, CSSD, Facilities, Housekeeping, Dietary, Ambulance, HR/Workforce, Insurance/Claims, Patient App, Doctor App, Nurse App, Kiosk, Wayfinding, Security, Consent, Enhanced Tasks/Workflow, International Patients, Allied Health, HIM, Enhanced Billing.

---

# PHASE 3: AI-NATIVE HOSPITAL

**Goal:** Full AI workforce, intelligence, and advanced capabilities.
**Master Blueprint Reference:** Section 108

## BATCH 3.1 — AI OS Expansion (Section 56)

### 3.1.1 — AI Gateway Enhancements
- Expanded tool registry (all module APIs as AI tools)
- Context-aware tool selection, multi-step reasoning, streaming, cost tracking

### 3.1.2 — Agent Framework
- Agent lifecycle, identity, permissions, agent-to-agent communication, event subscriptions, metrics

---

## BATCH 3.2 — Multi-Agent Orchestration (Section 59)

### 3.2.1 — Orchestrator Domain Model
- Tables: orchestration_request, orchestration_task, agent_contribution, orchestration_synthesis

### 3.2.2 — Orchestrator API
- Submit request, monitor progress, review synthesis, approve/reject actions

### 3.2.3 — Orchestrator UI
- Request input, decomposition view, contributions timeline, synthesis display

---

## BATCH 3.3 — Voice AI (Section 57)

### 3.3.1 — Voice AI Backend
- Full pipeline: Mic -> Noise suppression -> VAD -> STT -> Language/Speaker -> Context -> Intent -> Permission -> Reasoning -> Tool -> Verification -> Action -> TTS
- Medical terminology, multilingual, context-aware

### 3.3.2 — Voice AI API
- Session management, streaming audio, command processing, dictation, queries

### 3.3.3 — Voice AI UI
- Activation button, visual feedback, transcript, confirmation before execution, multi-language

---

## BATCH 3.4 — Hospital Digital Twin (Section 53)

### 3.4.1 — Digital Twin Domain Model
- Tables: twin_snapshot, twin_entity_state, twin_event
- Live representation: Hospital -> Building -> Floor -> Department -> Room -> Bed -> Equipment -> Staff -> Patient -> Workflow

### 3.4.2 — Digital Twin API
- Current state query, history, scenario simulation, real-time WebSocket

### 3.4.3 — Digital Twin UI
- Interactive hospital map, color-coded status, real-time updates, click-through detail

---

## BATCH 3.5 — Capacity Intelligence (Section 54)

### 3.5.1 — Capacity Intelligence Backend
- Continuous modeling: beds, ICU, OT, staff, diagnostics, equipment, pharmacy, blood
- Demand prediction, constraint identification, recommendations

### 3.5.2 — Capacity Intelligence API
- Current status, forecast, accommodation assessment, constraints, optimizations

### 3.5.3 — Capacity Intelligence UI
- Real-time utilization gauges, forecast charts, what-if scenarios, constraint alerts

---

## BATCH 3.6 — Revenue Intelligence (Section 26, 55)

### 3.6.1 — Revenue Intelligence Backend
- Reconciliation: clinical -> charge -> coding -> invoice -> claim -> payment
- Missing/duplicate charges, unbilled services, coding gaps, claim leakage, denial risk

### 3.6.2 — Revenue Intelligence API
- Leakage alerts, missing/duplicate charges, unbilled, coding accuracy, denial prediction

### 3.6.3 — Revenue Intelligence UI
- Intelligence dashboard, leakage alerts, coding view, denial prediction, recommendations

---

## BATCH 3.7 — Clinical Intelligence (Section 55 CMO)

### 3.7.1 — Clinical Intelligence Backend
- Quality metrics, outcome tracking, safety patterns, care pathway compliance

### 3.7.2 — Clinical Intelligence API
- Quality dashboard, outcomes, safety analytics, pathway compliance, alert analytics

### 3.7.3 — Clinical Intelligence UI
- CMO dashboard, outcome comparison, safety trends, pathway view, alert optimization

---

## BATCH 3.8 — Patient Journey Intelligence (Section 7)

### 3.8.1 — Journey Intelligence Backend
- End-to-end tracking, bottleneck identification, time analytics, satisfaction correlation

### 3.8.2 — Journey Intelligence API
- Journey timeline, bottleneck report, time analytics, optimization recommendations

### 3.8.3 — Journey Intelligence UI
- Journey visualization (flow diagram), bottleneck heat map, optimization cards

---

## BATCH 3.9 — Workforce Intelligence (Section 36)

### 3.9.1 — Workforce Intelligence Backend
- Demand prediction, gap identification, skill matching, nurse-patient ratios, overtime analysis
- NOT indiscriminate employee surveillance

### 3.9.2 — Workforce Intelligence API
- Demand forecast, gap report, skill matching, ratios, overtime analytics

### 3.9.3 — Workforce Intelligence UI
- Intelligence dashboard, gap visualization, skill matching, ratio alerts

---

## BATCH 3.10 — Executive Command Centre (Section 55)

### 3.10.1 — Command Centre Backend
- Role-specific aggregation (CEO, COO, CFO, CMO, Nursing Dir, Facilities Dir), KPI engine

### 3.10.2 — Command Centre API
- Role-specific dashboard data, real-time feeds, drill-down

### 3.10.3 — Command Centre UI
- Role-specific pages (Command theme), real-time ops, KPI cards, drill-down, alert panel, full-screen mode

---

## BATCH 3.11 — AI Governance (Section 60)

### 3.11.1 — AI Governance Domain Model
- Tables: model_registry, agent_registry, tool_registry, prompt_version, evaluation_result, ai_incident, safety_test

### 3.11.2 — AI Governance API
- Model/agent/tool CRUD, prompts, evaluations, safety tests, incidents, cost tracking, data leakage prevention

### 3.11.3 — AI Governance UI
- Model/agent/tool management, evaluation dashboard, safety testing, incidents, cost analytics

---

## BATCH 3.12 — AI Model Router Enhancement (Section 61)

### 3.12.1 — Model Router Backend
- Multi-provider, task-based routing, cost/latency/privacy optimization, fallback chains, A/B testing

### 3.12.2 — Model Router API
- Provider CRUD, routing rules, fallback chains, performance comparison, cost metrics

### 3.12.3 — Model Router UI
- Provider management, routing editor, comparison dashboard, cost optimization

---

## BATCH 3.13 — Agent SDK (Section 79)

### 3.13.1 — Agent SDK Framework
- SDK (TypeScript/Python), base class, identity, permissions, tools, knowledge, memory, policies, events, audit

### 3.13.2 — Agent SDK Documentation
- Getting started, API reference, tool dev guide, permission model, examples

### 3.13.3 — Agent SDK UI
- Agent registration portal, testing sandbox, monitoring dashboard

---

## BATCH 3.14 — AI Memory Architecture (Section 62)

### 3.14.1 — Memory Backend
- Session, User, Patient Context, Hospital Knowledge, Operational State, Long-term Analytics layers
- Separate retention and authorization per layer

### 3.14.2 — Memory API
- CRUD per layer, cross-layer search, TTL management, authorization checks

### 3.14.3 — Memory UI
- Memory inspection (admin), user memory mgmt, hospital knowledge mgmt, retention policies

---

## BATCH 3.15 — Permission-Aware AI Enhancement (Section 63)

### 3.15.1 — Enhanced Permission-Aware AI
- Dynamic permission evaluation, patient-level access, department isolation, data access audit

### 3.15.2 — AI Permission API
- Permission scope query, evaluation for proposed actions, violation reporting, audit trail

---

## BATCH 3.16 — Revenue Integrity Engine (Section 26)

### 3.16.1 — Revenue Integrity Backend
- Continuous reconciliation, missing/duplicate charges, unbilled, documentation gaps, coding inconsistency, denial risk

### 3.16.2 — Revenue Integrity API
- Reconciliation status, leakage alerts, missing/duplicate charges, unbilled, coding audit, denial risk

### 3.16.3 — Revenue Integrity UI
- Integrity dashboard, reconciliation timeline, leakage cards, coding audit, denial heatmap

---

## BATCH 3.17 — Patient Experience / CRM (Section 42)

### 3.17.1 — Patient Experience Domain Model
- Tables: feedback, survey, survey_response, complaint, service_recovery, loyalty_point, follow_up_task

### 3.17.2 — Patient Experience API
- Feedback, surveys, complaints, service recovery, loyalty, follow-up, analytics

### 3.17.3 — Patient Experience UI
- Feedback portal, complaint management, service recovery, experience analytics (NPS)

---

## BATCH 3.18 — Contact Centre (Section 43)

### 3.18.1 — Contact Centre Domain Model
- Tables: contact_interaction, contact_agent, call_recording, knowledge_article

### 3.18.2 — Contact Centre API
- Interactions, agent status, knowledge base, call/chat/email, escalation, analytics

### 3.18.3 — Contact Centre UI
- Agent workspace, knowledge base, call/chat/email, escalation, dashboard

---

## BATCH 3.19 — Telemedicine (Section 45)

### 3.19.1 — Telemedicine Domain Model
- Tables: teleconsult_session, teleconsult_recording, remote_monitoring_data

### 3.19.2 — Telemedicine API
- Session management, WebRTC, chat, screen sharing, ePrescription, payment, recording

### 3.19.3 — Telemedicine UI
- Waiting room, consultation room, video/audio controls, chat, ePrescription, notes

---

## BATCH 3.20 — Quality and Compliance (Section 38)

### 3.20.1 — Quality Domain Model
- Tables: quality_indicator, quality_target, accreditation_framework, quality_audit, policy, policy_acknowledgement

### 3.20.2 — Quality API
- Indicators, targets, frameworks, audits, policy lifecycle, compliance

### 3.20.3 — Quality UI
- Quality dashboard, indicators, accreditation readiness, policy management, audits

---

## BATCH 3.21 — Patient Safety / Incident Management (Section 39)

### 3.21.1 — Safety Domain Model
- Tables: incident_report, near_miss, incident_category, rca_record, capa_record

### 3.21.2 — Safety API
- Incident/near-miss reporting, investigation, RCA, CAPA, safety analytics

### 3.21.3 — Safety UI
- Incident form, investigation workspace, RCA form, CAPA board, safety dashboard

---

## BATCH 3.22 — Infection Prevention and Control (Section 40)

### 3.22.1 — IPC Domain Model
- Tables: infection_surveillance_record, isolation_order, outbreak_event, antibiotic_surveillance, hand_hygiene_audit, device_associated_infection

### 3.22.2 — IPC API
- Surveillance, isolation, outbreak, antibiotic stewardship, hand hygiene, device infections

### 3.22.3 — IPC UI
- IPC dashboard, surveillance data entry, isolation management, outbreak management, stewardship

---

# PHASE 3 COMPLETE

**Squamed now has:** AI OS, Multi-Agent Orchestration, Voice AI, Digital Twin, Capacity Intelligence, Revenue Intelligence, Clinical Intelligence, Journey Intelligence, Workforce Intelligence, Command Centre, AI Governance, Model Router, Agent SDK, AI Memory, Permission-Aware AI, Revenue Integrity, Patient Experience, Contact Centre, Telemedicine, Quality, Safety, IPC.

---

# PHASE 4: ENTERPRISE / GROUP

**Goal:** Multi-hospital, enterprise governance, sovereign deployment.
**Master Blueprint Reference:** Section 109

## BATCH 4.1 — Multi-Hospital Architecture (Section 73)

### 4.1.1 — Multi-Hospital Domain Model
- Tables: enterprise, hospital_group, hospital_hierarchy, cross_hospital_referral, shared_patient_identity
- Hierarchy: Enterprise -> Group -> Hospital -> Campus -> Building -> Department

### 4.1.2 — Multi-Hospital API
- Enterprise/group CRUD, hierarchy, cross-hospital referrals, shared EMPI

### 4.1.3 — Multi-Hospital UI
- Enterprise/group management, hospital selector, cross-hospital records, group dashboards

---

## BATCH 4.2 — Central Command (Section 55)

### 4.2.1 — Central Command Backend
- Multi-hospital KPI aggregation, comparative analytics, resource sharing intelligence

### 4.2.2 — Central Command API
- Group CEO/COO/CFO/CMO data, cross-hospital comparison, strategic planning

### 4.2.3 — Central Command UI
- Group command centre, hospital comparison, resource sharing, strategic planning

---

## BATCH 4.3 — Group EMPI (Section 4.4)

### 4.3.1 — Group EMPI Backend
- Cross-hospital matching, record linking, de-duplication, consent for cross-hospital access

### 4.3.2 — Group EMPI API
- Cross-hospital search, merge, link/unlink, consent verification

### 4.3.3 — Group EMPI UI
- Cross-hospital search, aggregated records, merge management, consent management

---

## BATCH 4.4 — Group Analytics (Section 83)

### 4.4.1 — Group Analytics Backend
- Multi-hospital aggregation, group KPIs, comparative analytics, benchmarking

### 4.4.2 — Group Analytics API
- Group analytics, hospital comparison, benchmarking, trends

### 4.4.3 — Group Analytics UI
- Group dashboard, comparison views, benchmarking, trends

---

## BATCH 4.5 — Group Procurement (Section 29)

### 4.5.1 — Group Procurement Backend
- Consolidated purchasing, group vendors, volume discounts, shared inventory

### 4.5.2 — Group Procurement API
- Consolidated requisitions/POs, cross-hospital transfers, vendor performance

### 4.5.3 — Group Procurement UI
- Consolidated dashboard, group POs, stock visibility, vendor comparison

---

## BATCH 4.6 — Group Finance (Section 37)

### 4.6.1 — Group Finance Backend
- Consolidated reporting, inter-hospital transfers, group GL/AP/AR, budget management

### 4.6.2 — Group Finance API
- Consolidated P&L/BS, transfers, budget vs actual, per-hospital and group reporting

### 4.6.3 — Group Finance UI
- Group financial dashboard, drill-down, transfers, budget management

---

## BATCH 4.7 — Cross-Hospital Referrals (Section 73)

### 4.7.1 — Referral Backend
- Cross-hospital workflow, record sharing (with consent), tracking, analytics

### 4.7.2 — Referral API
- Create, record sharing (FHIR), status tracking, analytics

### 4.7.3 — Referral UI
- Referral creation, consent form, tracking dashboard, incoming management

---

## BATCH 4.8 — Enterprise AI Governance (Section 60)

### 4.8.1 — Enterprise AI Governance
- Group model registry, cross-hospital policy enforcement, centralized cost tracking

### 4.8.2 — Enterprise AI Governance API
- Group model management, policy enforcement, cost tracking

### 4.8.3 — Enterprise AI Governance UI
- Group AI dashboard, cross-hospital deployment, policy management, cost analytics

---

## BATCH 4.9 — Private Cloud Deployment (Section 72)

### 4.9.1 — Private Cloud Infrastructure
- Terraform modules, K8s management, DB replication, networking, backup/DR

### 4.9.2 — Deployment Automation
- One-click deployment, config management, monitoring stack, SSL management

---

## BATCH 4.10 — Sovereign Deployment (Section 72)

### 4.10.1 — Sovereign Deployment
- Data residency config, sovereign cloud integration, country-specific encryption

### 4.10.2 — Sovereign Configuration
- Residency rules engine, cross-border controls, compliance checks, local regulatory integration

---

## BATCH 4.11 — Advanced Disaster Recovery (Section 71)

### 4.11.1 — Advanced DR Backend
- Active-passive/active-active failover, RPO/RTO management, automated triggers

### 4.11.2 — DR Infrastructure
- Multi-region, DB replication, storage replication, DNS failover, health checks

### 4.11.3 — DR Testing
- Automated drills, failover testing, integrity verification, recovery measurement

---

## BATCH 4.12 — Developer Platform (Section 78)

### 4.12.1 — Developer Portal
- Registration, API keys, OAuth 2.0, webhooks, sandbox, app certification

### 4.12.2 — Developer Portal UI
- Dashboard, interactive docs, API key mgmt, sandbox, app submission, webhooks

---

## BATCH 4.13 — Multi-Tenancy (Section 74)

### 4.13.1 — Multi-Tenancy Backend
- Tenant isolation (data, identity, config, AI, storage, integration, audit), provisioning, billing

### 4.13.2 — Multi-Tenancy API
- Tenant CRUD, provisioning workflow, configuration, billing

### 4.13.3 — Multi-Tenancy UI
- Tenant management, provisioning wizard, configuration, billing dashboard

---

## BATCH 4.14 — Regulatory Framework (Section 75)

### 4.14.1 — Regulatory Domain Model
- Tables: country, regulation, regulatory_rule, compliance_check, regulatory_report
- Country -> Regulation -> Rule -> Workflow -> Evidence -> Audit

### 4.14.2 — Regulatory API
- Country/regulation/rule management, compliance checks, report generation

### 4.14.3 — Regulatory UI
- Framework management, compliance dashboard, report generation

---

## BATCH 4.15 — Legal / Risk (Section 41)

### 4.15.1 — Legal Domain Model
- Tables: legal_case, legal_claim, litigation_hold, risk_register, legal_correspondence

### 4.15.2 — Legal API
- Case CRUD, litigation hold, risk register, evidence, correspondence

### 4.15.3 — Legal UI
- Case management, litigation hold, risk dashboard, evidence management

---

## BATCH 4.16 — Full Finance (Section 37)

### 4.16.1 — Finance Domain Model
- Tables: general_ledger, chart_of_accounts, accounts_payable, accounts_receivable, journal_entry, bank_account, bank_reconciliation, cost_centre, budget, asset_accounting

### 4.16.2 — Finance API
- Chart of accounts, journals, AP/AR, bank reconciliation, budgets, financial statements, tax

### 4.16.3 — Finance UI
- GL, journal entries, AP/AR management, bank reconciliation, budgets, statements, tax reports

---

## BATCH 4.17 — Data Portability (Section 82)

### 4.17.1 — Data Portability Backend
- Export engine (FHIR, HL7, CSV, JSON, PDF), controlled destruction, import, audit

### 4.17.2 — Data Portability API
- Export requests, status/download, import, destruction requests

### 4.17.3 — Data Portability UI
- Export/import wizards, destruction request, export/import history

---

## BATCH 4.18 — Licensing and Entitlement (Section 81)

### 4.18.1 — Licensing Domain Model
- Tables: license, entitlement, usage_tracking, billing_metric
- Types: subscription, bed-based, user, module, AI, voice, API, storage

### 4.18.2 — Licensing API
- License/entitlement CRUD, usage tracking, feature access verification

### 4.18.3 — Licensing UI
- License management, entitlement config, usage dashboard, feature matrix

---

## BATCH 4.19 — Enhanced DR and Business Continuity (Section 71)

### 4.19.1 — BCP Backend
- Degraded mode, offline workflows, emergency access, downtime procedures, mass casualty, evacuation

### 4.19.2 — BCP API
- Degraded mode activation, offline sync, emergency access, mass casualty mode, evacuation

### 4.19.3 — BCP UI
- Degraded mode indicator, offline interface, emergency access panel, mass casualty command

---

## BATCH 4.20 — Implementation and Migration Studio (Section 76)

### 4.20.1 — Migration Backend
- Pipeline: Extract -> Map -> Clean -> Transform -> Validate -> Test -> Import -> Reconcile
- Source adapters: Legacy HIS, EMR, LIS, RIS, PACS, ERP, Excel, CSV

### 4.20.2 — Migration API
- Project CRUD, source config, mapping, execution, validation, reconciliation, rollback

### 4.20.3 — Migration Studio UI
- Project wizard, connection setup, field mapping, data preview, validation, progress, reconciliation

---

## BATCH 4.21 — Sandbox and Training Hospital (Section 77)

### 4.21.1 — Sandbox Backend
- Synthetic data generator, training session management, data reset, scenario config

### 4.21.2 — Sandbox API
- Environment provisioning, data generation, session management, reset, scenario templates

### 4.21.3 — Sandbox UI
- Environment selector, data generator, training management, scenario library, performance tracking

---

# PHASE 4 COMPLETE

**Squamed now has:** Multi-Hospital, Central Command, Group EMPI, Group Analytics, Group Procurement, Group Finance, Cross-Hospital Referrals, Enterprise AI, Private Cloud, Sovereign Deployment, Advanced DR, Developer Platform, Multi-Tenancy, Regulatory Framework, Legal/Risk, Full Finance, Data Portability, Licensing, BCP, Migration Studio, Sandbox.

---

# PHASE 5: PLATFORM ECOSYSTEM

**Goal:** Open marketplace, developer ecosystem, global scale.
**Master Blueprint Reference:** Section 110

## BATCH 5.1 — Squamed Marketplace (Section 80)

### 5.1.1 — Marketplace Backend
- Listing management, submission/review workflow, payment processing, versioning, ratings

### 5.1.2 — Marketplace API
- Listing CRUD, installation, payment, reviews, search/discovery

### 5.1.3 — Marketplace UI
- Storefront, app detail, developer portal, admin review, installed apps

---

## BATCH 5.2 — Agent Marketplace (Section 80)

### 5.2.1 — Agent Marketplace Backend
- AI agent listing, review, sandbox testing, permission scoping, pricing, metrics

### 5.2.2 — Agent Marketplace API
- Listing CRUD, installation, testing, performance reporting

### 5.2.3 — Agent Marketplace UI
- Storefront, agent detail, testing sandbox, installed agents, performance dashboard

---

## BATCH 5.3 — Device Marketplace (Section 80)

### 5.3.1 — Device Marketplace Backend
- Connector listing, certification, integration testing, compatibility matrix

### 5.3.2 — Device Marketplace API
- Connector CRUD, device registration, certification, testing

### 5.3.3 — Device Marketplace UI
- Storefront, device detail, registration wizard, management dashboard

---

## BATCH 5.4 — Integration Marketplace (Section 80)

### 5.4.1 — Integration Marketplace Backend
- Connector listing (payment, insurance, ERP, HR), certification, testing sandbox, pricing

### 5.4.2 — Integration Marketplace API
- Listing CRUD, installation, health monitoring, testing

### 5.4.3 — Integration Marketplace UI
- Storefront, detail, installation wizard, configuration, health monitoring

---

## BATCH 5.5 — Developer SDK Enhancement (Section 78, 79)

### 5.5.1 — Enhanced SDK
- TypeScript SDK, Python SDK, React Native SDK, Voice SDK, IoT SDK, Agent SDK

### 5.5.2 — SDK Documentation
- Interactive docs, code examples, tutorials, migration guides, best practices

### 5.5.3 — SDK Tools
- CLI tool, SDK generator, mock server, version management

---

## BATCH 5.6 — Third-Party Application Support (Section 80)

### 5.6.1 — Third-Party App Framework
- App manifest, sandboxed execution, permissions, data APIs, UI embedding, webhooks

### 5.6.2 — Third-Party App API
- Registration, data access (permission-checked), webhooks, lifecycle management

### 5.6.3 — Third-Party App UI
- App management dashboard, configuration, marketplace integration, usage analytics

---

## BATCH 5.7 — Hospital Benchmarking (Section 84)

### 5.7.1 — Benchmarking Backend
- Anonymized data collection, metrics (wait time, LOS, utilization, TAT, revenue, experience, readmission, staffing), peer groups

### 5.7.2 — Benchmarking API
- Metric submission, peer groups, benchmark queries, rankings, trends

### 5.7.3 — Benchmarking UI
- Benchmarking dashboard, comparison charts, rankings, improvement opportunities

---

## BATCH 5.8 — Advanced Hospital Digital Twin (Section 53)

### 5.8.1 — Advanced Twin Backend
- IoT integration, predictive maintenance, energy modeling, space optimization, flow simulation

### 5.8.2 — Advanced Twin API
- IoT ingestion, predictive alerts, energy metrics, simulation endpoints

### 5.8.3 — Advanced Twin UI
- IoT overlay on floor plan, predictive maintenance timeline, energy dashboard, simulation builder

---

## BATCH 5.9 — Global Regulatory Packs (Section 75)

### 5.9.1 — Regulatory Pack Framework
- Pluggable architecture, pack = rules + workflows + reports + validations, installer/updater

### 5.9.2 — Initial Regulatory Packs
- India (ABDM, NABH), USA (HIPAA, CMS, JC), UAE (DHA, HAAD), Saudi (CBAHI, NPHIES), UK (NHS, CQC)

### 5.9.3 — Regulatory Pack UI
- Pack marketplace, country config wizard, compliance dashboard per country

---

## BATCH 5.10 — Sustainability / ESG (Section 85)

### 5.10.1 — ESG Domain Model
- Tables: energy_record, water_record, waste_record, carbon_record, procurement_sustainability, esg_report

### 5.10.2 — ESG API
- Data recording, metrics calculation, report generation, sustainability scoring

### 5.10.3 — ESG UI
- ESG dashboard, data entry, reports, sustainability scores, recommendations

---

## BATCH 5.11 — Specialty Engine Advanced (Section 23)

### 5.11.1 — Specialty Package Framework
- Package definition (templates, workflows, forms, order sets, pathways), installer, versioning

### 5.11.2 — Initial Specialty Packages
- Cardiology, Oncology, Orthopaedics, Neurosurgery, OBGYN, Paediatrics, Nephrology, Gastroenterology

### 5.11.3 — Specialty Package UI
- Package marketplace, installation wizard, workflow config, form templates

---

## BATCH 5.12 — Advanced Analytics and BI (Section 83)

### 5.12.1 — Advanced Analytics Backend
- Self-service report builder, custom dashboards, predictive analytics, cohort/funnel analysis, scheduling, data warehouse

### 5.12.2 — Advanced Analytics API
- Dynamic query construction, dashboard CRUD, predictions, cohort/funnel, scheduling, exports

### 5.12.3 — Advanced Analytics UI
- Report builder, dashboard builder, predictive views, cohort interface, funnel viz, drill-down, scheduled reports

---

# PHASE 5 COMPLETE

**Squamed is now a complete platform ecosystem:**
Marketplace (Apps, Agents, Devices, Integrations), Developer SDK, Third-Party Apps, Benchmarking, Advanced Digital Twin, Global Regulatory Packs, ESG, Specialty Packages, Advanced Analytics.

---

# COMPLETE BATCH SUMMARY

## Phase 1: Foundation MVP — 28 Batches

| Batch | Name | Sub-Batches |
|-------|------|-------------|
| 1.1 | Repository & Dev Infrastructure | 1.1.1 - 1.1.13 |
| 1.2 | Design System Foundation | 1.2.1 - 1.2.5 |
| 1.3 | Authentication & Authorization | 1.3.1 - 1.3.4 |
| 1.4 | Hospital Configuration Engine | 1.4.1 - 1.4.3 |
| 1.5 | Master Data Management | 1.5.1 - 1.5.2 |
| 1.6 | Patient Identity & EMPI | 1.6.1 - 1.6.3 |
| 1.7 | Application Shell & Navigation | 1.7.1 - 1.7.5 |
| 1.8 | Appointment Management | 1.8.1 - 1.8.3 |
| 1.9 | Reception & Front Office | 1.9.1 - 1.9.3 |
| 1.10 | Queue Engine & Display | 1.10.1 - 1.10.5 |
| 1.11 | OPD / Ambulatory | 1.11.1 - 1.11.3 |
| 1.12 | Orders Management | 1.12.1 - 1.12.3 |
| 1.13 | Laboratory Information System | 1.13.1 - 1.13.3 |
| 1.14 | Pharmacy | 1.14.1 - 1.14.3 |
| 1.15 | Billing & Revenue Cycle | 1.15.1 - 1.15.3 |
| 1.16 | Universal Task Engine | 1.16.1 - 1.16.3 |
| 1.17 | Communication (Connect) | 1.17.1 - 1.17.3 |
| 1.18 | Notifications | 1.18.1 - 1.18.3 |
| 1.19 | Workflow Engine | 1.19.1 - 1.19.3 |
| 1.20 | Real-Time Event Fabric | 1.20.1 - 1.20.2 |
| 1.21 | Audit Architecture | 1.21.1 - 1.21.3 |
| 1.22 | Patient 360 | 1.22.1 - 1.22.2 |
| 1.23 | AI Gateway & Command Bar | 1.23.1 - 1.23.3 |
| 1.24 | AI Scribe | 1.24.1 - 1.24.2 |
| 1.25 | Basic Analytics | 1.25.1 - 1.25.3 |
| 1.26 | FHIR/HL7 Foundation | 1.26.1 - 1.26.3 |
| 1.27 | Testing & QA | 1.27.1 - 1.27.3 |
| 1.28 | Deployment & DevOps | 1.28.1 - 1.28.4 |

## Phase 2: Hospital Operations — 29 Batches

| Batch | Name | Sub-Batches |
|-------|------|-------------|
| 2.1 | Inpatient Platform (IPD) | 2.1.1 - 2.1.3 |
| 2.2 | Nursing | 2.2.1 - 2.2.3 |
| 2.3 | ICU / Critical Care | 2.3.1 - 2.3.3 |
| 2.4 | Emergency & Trauma | 2.4.1 - 2.4.3 |
| 2.5 | OT / Surgery | 2.5.1 - 2.5.3 |
| 2.6 | Anaesthesia | 2.6.1 - 2.6.3 |
| 2.7 | Blood Bank | 2.7.1 - 2.7.3 |
| 2.8 | PACS / RIS | 2.8.1 - 2.8.3 |
| 2.9 | Procurement & Supply Chain | 2.9.1 - 2.9.3 |
| 2.10 | Inventory | 2.10.1 - 2.10.3 |
| 2.11 | CSSD | 2.11.1 - 2.11.3 |
| 2.12 | Facilities & Asset Management | 2.12.1 - 2.12.3 |
| 2.13 | Housekeeping & Hospitality | 2.13.1 - 2.13.3 |
| 2.14 | Dietary | 2.14.1 - 2.14.3 |
| 2.15 | Ambulance | 2.15.1 - 2.15.3 |
| 2.16 | HR / Workforce OS | 2.16.1 - 2.16.3 |
| 2.17 | Insurance / TPA / Claims | 2.17.1 - 2.17.3 |
| 2.18 | Patient App / Portal | 2.18.1 - 2.18.2 |
| 2.19 | Doctor App | 2.19.1 - 2.19.2 |
| 2.20 | Nurse App | 2.20.1 - 2.20.2 |
| 2.21 | Kiosk Platform | 2.21.1 - 2.21.2 |
| 2.22 | Wayfinding | 2.22.1 - 2.22.3 |
| 2.23 | Security & Access | 2.23.1 - 2.23.3 |
| 2.24 | Consent & Privacy Engine | 2.24.1 - 2.24.3 |
| 2.25 | Enhanced Tasks & Workflow | 2.25.1 - 2.25.2 |
| 2.26 | International Patient Management | 2.26.1 - 2.26.3 |
| 2.27 | Allied Health | 2.27.1 - 2.27.3 |
| 2.28 | Health Information Management | 2.28.1 - 2.28.3 |
| 2.29 | Enhanced Billing | 2.29.1 - 2.29.3 |

## Phase 3: AI-Native Hospital — 22 Batches

| Batch | Name | Sub-Batches |
|-------|------|-------------|
| 3.1 | AI OS Expansion | 3.1.1 - 3.1.2 |
| 3.2 | Multi-Agent Orchestration | 3.2.1 - 3.2.3 |
| 3.3 | Voice AI | 3.3.1 - 3.3.3 |
| 3.4 | Hospital Digital Twin | 3.4.1 - 3.4.3 |
| 3.5 | Capacity Intelligence | 3.5.1 - 3.5.3 |
| 3.6 | Revenue Intelligence | 3.6.1 - 3.6.3 |
| 3.7 | Clinical Intelligence | 3.7.1 - 3.7.3 |
| 3.8 | Patient Journey Intelligence | 3.8.1 - 3.8.3 |
| 3.9 | Workforce Intelligence | 3.9.1 - 3.9.3 |
| 3.10 | Executive Command Centre | 3.10.1 - 3.10.3 |
| 3.11 | AI Governance | 3.11.1 - 3.11.3 |
| 3.12 | AI Model Router Enhancement | 3.12.1 - 3.12.3 |
| 3.13 | Agent SDK | 3.13.1 - 3.13.3 |
| 3.14 | AI Memory Architecture | 3.14.1 - 3.14.3 |
| 3.15 | Permission-Aware AI | 3.15.1 - 3.15.2 |
| 3.16 | Revenue Integrity Engine | 3.16.1 - 3.16.3 |
| 3.17 | Patient Experience / CRM | 3.17.1 - 3.17.3 |
| 3.18 | Contact Centre | 3.18.1 - 3.18.3 |
| 3.19 | Telemedicine | 3.19.1 - 3.19.3 |
| 3.20 | Quality & Compliance | 3.20.1 - 3.20.3 |
| 3.21 | Patient Safety / Incident Management | 3.21.1 - 3.21.3 |
| 3.22 | Infection Prevention & Control | 3.22.1 - 3.22.3 |

## Phase 4: Enterprise / Group — 21 Batches

| Batch | Name | Sub-Batches |
|-------|------|-------------|
| 4.1 | Multi-Hospital Architecture | 4.1.1 - 4.1.3 |
| 4.2 | Central Command | 4.2.1 - 4.2.3 |
| 4.3 | Group EMPI | 4.3.1 - 4.3.3 |
| 4.4 | Group Analytics | 4.4.1 - 4.4.3 |
| 4.5 | Group Procurement | 4.5.1 - 4.5.3 |
| 4.6 | Group Finance | 4.6.1 - 4.6.3 |
| 4.7 | Cross-Hospital Referrals | 4.7.1 - 4.7.3 |
| 4.8 | Enterprise AI Governance | 4.8.1 - 4.8.3 |
| 4.9 | Private Cloud Deployment | 4.9.1 - 4.9.2 |
| 4.10 | Sovereign Deployment | 4.10.1 - 4.10.2 |
| 4.11 | Advanced DR | 4.11.1 - 4.11.3 |
| 4.12 | Developer Platform | 4.12.1 - 4.12.2 |
| 4.13 | Multi-Tenancy | 4.13.1 - 4.13.3 |
| 4.14 | Regulatory Framework | 4.14.1 - 4.14.3 |
| 4.15 | Legal / Risk | 4.15.1 - 4.15.3 |
| 4.16 | Full Finance | 4.16.1 - 4.16.3 |
| 4.17 | Data Portability | 4.17.1 - 4.17.3 |
| 4.18 | Licensing & Entitlement | 4.18.1 - 4.18.3 |
| 4.19 | Enhanced DR & BCP | 4.19.1 - 4.19.3 |
| 4.20 | Implementation & Migration Studio | 4.20.1 - 4.20.3 |
| 4.21 | Sandbox & Training Hospital | 4.21.1 - 4.21.3 |

## Phase 5: Platform Ecosystem — 12 Batches

| Batch | Name | Sub-Batches |
|-------|------|-------------|
| 5.1 | Squamed Marketplace | 5.1.1 - 5.1.3 |
| 5.2 | Agent Marketplace | 5.2.1 - 5.2.3 |
| 5.3 | Device Marketplace | 5.3.1 - 5.3.3 |
| 5.4 | Integration Marketplace | 5.4.1 - 5.4.3 |
| 5.5 | Developer SDK Enhancement | 5.5.1 - 5.5.3 |
| 5.6 | Third-Party App Support | 5.6.1 - 5.6.3 |
| 5.7 | Hospital Benchmarking | 5.7.1 - 5.7.3 |
| 5.8 | Advanced Digital Twin | 5.8.1 - 5.8.3 |
| 5.9 | Global Regulatory Packs | 5.9.1 - 5.9.3 |
| 5.10 | Sustainability / ESG | 5.10.1 - 5.10.3 |
| 5.11 | Specialty Engine Advanced | 5.11.1 - 5.11.3 |
| 5.12 | Advanced Analytics & BI | 5.12.1 - 5.12.3 |

---

# GRAND TOTAL

| Phase | Batches |
|-------|---------|
| Phase 1: Foundation MVP | 28 |
| Phase 2: Hospital Operations | 29 |
| Phase 3: AI-Native Hospital | 22 |
| Phase 4: Enterprise / Group | 21 |
| Phase 5: Platform Ecosystem | 12 |
| **TOTAL** | **112** |

---

# MASTER BLUEPRINT SECTION COVERAGE

Every section of the Master Blueprint (Sections 1-117) is mapped to build batches:

| Section | Title | Covered In |
|---------|-------|------------|
| 1 | Vision | Architecture (throughout) |
| 2 | Product Principles | Architecture (throughout) |
| 3 | Platform Model | Module structure (throughout) |
| 4.1 | Hospital Configuration Engine | Batch 1.4 |
| 4.2 | Master Data Management | Batch 1.5 |
| 4.3 | Enterprise Identity | Batch 1.3 |
| 4.4 | EMPI | Batch 1.6 |
| 5.1 | Patient 360 | Batch 1.22 |
| 6 | Patient Access & Front Office | Batch 1.9, 1.10 |
| 7 | Patient Flow OS | Batch 1.7.5, 1.11, 3.8 |
| 8 | Display, Kiosk & Wayfinding | Batch 1.10.3-4, 2.21, 2.22 |
| 9 | Ambulatory / OPD | Batch 1.11 |
| 10 | Inpatient Platform | Batch 2.1 |
| 11 | Emergency & Trauma | Batch 2.4 |
| 12 | ICU / Critical Care | Batch 2.3 |
| 13 | EMR / EHR | Batch 1.11, 1.12 |
| 14 | AI Scribe | Batch 1.24 |
| 15 | Laboratory Information System | Batch 1.13 |
| 16 | Radiology / RIS | Batch 2.8 |
| 17 | PACS / Imaging | Batch 2.8 |
| 18 | Blood Bank | Batch 2.7 |
| 19 | Pharmacy & Medication Intelligence | Batch 1.14 |
| 20 | Nursing | Batch 2.2 |
| 21 | OT / Surgery | Batch 2.5 |
| 22 | Anaesthesia | Batch 2.6 |
| 23 | Specialty Engine | Batch 5.11 |
| 24 | Allied Health | Batch 2.27 |
| 25 | Billing & Revenue Cycle | Batch 1.15, 2.29 |
| 26 | Revenue Integrity Engine | Batch 3.16 |
| 27 | Health Information Management | Batch 2.28 |
| 28 | Insurance / TPA / Claims | Batch 2.17 |
| 29 | Procurement & Supply Chain | Batch 2.9 |
| 30 | CSSD | Batch 2.11 |
| 31 | Facilities & Asset Management | Batch 2.12 |
| 32 | Housekeeping & Hospitality | Batch 2.13 |
| 33 | Dietary | Batch 2.14 |
| 34 | Ambulance | Batch 2.15 |
| 35 | Security & Access | Batch 2.23 |
| 36 | HR / Workforce OS | Batch 2.16 |
| 37 | Finance | Batch 4.16 |
| 38 | Quality & Compliance | Batch 3.20 |
| 39 | Patient Safety / Incident Management | Batch 3.21 |
| 40 | Infection Prevention & Control | Batch 3.22 |
| 41 | Legal / Risk | Batch 4.15 |
| 42 | Patient Experience / CRM | Batch 3.17 |
| 43 | Contact Centre | Batch 3.18 |
| 44 | International Patient Management | Batch 2.26 |
| 45 | Telemedicine | Batch 3.19 |
| 46 | Patient App / Portal | Batch 2.18 |
| 47 | Doctor App | Batch 2.19 |
| 48 | Nurse App | Batch 2.20 |
| 49 | Squamed Connect | Batch 1.17 |
| 50 | Universal Task Engine | Batch 1.16 |
| 51 | Workflow Engine | Batch 1.19 |
| 52 | Real-Time Event Fabric | Batch 1.20 |
| 53 | Hospital Digital Twin | Batch 3.4, 5.8 |
| 54 | Hospital Capacity Intelligence | Batch 3.5 |
| 55 | Executive Command Centre | Batch 3.10, 4.2 |
| 56 | Squamed AI OS | Batch 1.23, 3.1 |
| 57 | Squamed Voice AI | Batch 3.3 |
| 58 | Agentic AI Workforce | Batch 3.1 |
| 59 | Multi-Agent Orchestration | Batch 3.2 |
| 60 | AI Governance | Batch 3.11, 4.8 |
| 61 | AI Model Router | Batch 1.23, 3.12 |
| 62 | AI Memory Architecture | Batch 3.14 |
| 63 | Permission-Aware AI | Batch 3.15 |
| 64 | Data Fabric | Architecture (throughout) |
| 65 | Interoperability Hub | Batch 1.26 |
| 66 | Device Integration Platform | Batch 5.3 |
| 67 | Security Architecture | Batch 1.3 |
| 68 | Security Operations | Batch 2.23 |
| 69 | Consent & Privacy Engine | Batch 2.24 |
| 70 | Audit Architecture | Batch 1.21 |
| 71 | Disaster Recovery & BCP | Batch 4.11, 4.19 |
| 72 | Deployment Models | Batch 1.28, 4.9, 4.10 |
| 73 | Multi-Hospital / Group | Batch 4.1, 4.7 |
| 74 | Multi-Tenancy | Batch 4.13 |
| 75 | Regulatory Framework | Batch 4.14, 5.9 |
| 76 | Implementation & Migration Studio | Batch 4.20 |
| 77 | Sandbox & Training Hospital | Batch 4.21 |
| 78 | Developer Platform | Batch 4.12, 5.5 |
| 79 | Squamed Agent SDK | Batch 3.13 |
| 80 | Squamed Marketplace | Batch 5.1, 5.2, 5.3, 5.4 |
| 81 | Licensing & Entitlement | Batch 4.18 |
| 82 | Data Portability | Batch 4.17 |
| 83 | Analytics & BI | Batch 1.25, 5.12 |
| 84 | Hospital Benchmarking | Batch 5.7 |
| 85 | Sustainability / ESG | Batch 5.10 |
| 86 | UX / Design System | Batch 1.2 |
| 87 | Three Visual Modes | Batch 1.2.2 |
| 88 | Application Shell | Batch 1.7 |
| 89 | Universal Command Bar | Batch 1.7.3, 1.23 |
| 90 | AI Intelligence Rail | Batch 1.7.4 |
| 91 | Accessibility | Batch 1.2.3 |
| 92 | Responsive Experience | Batch 1.7.1 |
| 93 | Technology Architecture | Batch 1.1 |
| 94 | Data Stack | Batch 1.1.4-9 |
| 95 | API Architecture | Architecture (throughout) |
| 96 | Core Architectural Pattern | Batch 1.1.3 |
| 97 | Repository Structure | Batch 1.1.1 |
| 98 | Observability | Batch 1.1.10, 1.28.4 |
| 99 | Release Engineering | Batch 1.1.11, 1.28.3 |
| 100 | Testing Strategy | Batch 1.27 |
| 101 | AI Evaluation Framework | Batch 3.11 |
| 102 | Implementation Governance | Batch 4.20 |
| 103 | Product Governance | Architecture (throughout) |
| 104 | Module SDK | Batch 1.1.3, 3.13 |
| 105 | Operating Model | Architecture (throughout) |
| 106 | MVP Strategy | Phase 1 |
| 107 | Phase 2 | Phase 2 |
| 108 | Phase 3 | Phase 3 |
| 109 | Phase 4 | Phase 4 |
| 110 | Phase 5 | Phase 5 |
| 111 | Scale Strategy | Architecture (throughout) |
| 112 | What Squamed Must Never Become | Principles (throughout) |
| 113 | Final Architectural Model | Architecture (throughout) |
| 114 | Core Differentiator | UX (throughout) |
| 115 | Squamed Promise | Architecture (throughout) |
| 116 | Master Development Rule | Every batch |
| 117 | Final North Star | Architecture (throughout) |

---

**All 117 sections of the Master Blueprint are covered.**
**112 batches. Nothing skipped.**
