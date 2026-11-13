# CHUNK 00 — FOUNDATION & GOVERNED PLATFORM
**Status:** PLANNED · **Window:** Dec 2026 → Feb 2027 (12 wk) · **Depends on:** —
**Stack entering:** Kotlin/Spring monolith, React PWA shell, Postgres, Redis, Keycloak, OpenTelemetry/Grafana, OpenSearch. **Note (SC-7):** workspace manager is npm (not pnpm) — adopted with the baseline build
**Baseline (SC-6):** `master` prior build covers much of this chunk (identity/JWT+TOTP, hospital config, master data, patient+EMPI, audit, tasks, notifications, search, workflow) — sections may enter gates on existing code after the chunk-0 baseline audit; conflicts resolved 2026-11-13: D16 → ADR-11 (item 4), D18 → ADR-13 (item 15).

## GOAL
A governed, configurable, audited platform on which every later domain is "add, not rebuild" (blueprint §104 honored in code, not slides).

## IN SCOPE
1. **Monorepo + CI/CD:** pnpm workspace + Gradle, GitHub Actions (lint, test, ArchUnit boundary check, migration check, image build), environments: dev (compose) → staging (k8s) → prod. Branching: trunk-based, release = tagged immutable build.
2. **Design system v1:** tokens per blueprint §86 (canvas `#F7F8F6`, identity `#087F7A`, semantic colors reserved for status), core components (data table, form, patient header, task card), light clinical mode only; i18n (en, hi) + RTL-ready from day 1; WCAG 2.2 AA baseline.
3. **App shell:** nav, hospital selector, universal search (OpenSearch), notifications center, task inbox, patient-context bar, user/security state. Command bar = **UI placeholder only** (AI is chunk 5).
4. **Identity & auth (D16 → ADR-11): baseline JWT+TOTP+RBAC stack retained — Keycloak/OIDC deferred until an SSO-federation requirement lands;** staff roles (super-admin, hospital-admin, doctor, nurse, reception, lab, pharmacy, billing, auditor, agent-service); RBAC + ABAC hooks (location/department scoping); MFA for admin/auditor; device sessions.
5. **Hospital configuration engine:** tenant → hospital → campus → building → floor → department → room → bed; working hours, holidays, shifts; service catalog; workflow/queue/approval config stubs with typed config schema (this is where "configure, don't fork" becomes real — versioned config documents, validated).
6. **Master data:** patient, staff/employee, department, location, bed, service, price list, vendor (stub), insurance/TPA registry, equipment (registry only).
7. **Patient master + EMPI:** registration (full demographics + ABHA linkage via ABDM sandbox), identifiers (MRN per hospital, ABHA, mobile, national ID), guardian/dependent relations, unknown-patient flow, merge/unmerge (versioned, reversible), duplicate detection per ADR-8 with manual review queue.
8. **Event fabric:** event envelope v1 (`id, tenant, type, version, occurredAt, actor{type:user|agent|system,id}, subject{type,id}, payload, correlationId, causationId`), transactional outbox → dispatcher → Redis pub/sub → WebSocket gateway. Event catalog starts with ~12 types (registration, patient updated, config changed, task created/completed, merge/unmerge, audit alerts).
9. **Universal task engine:** create → assign → execute → monitor → escalate → complete; subject attachments (patient, encounter placeholder, invoice placeholder, department); SLA + escalation rules from config.
10. **Audit:** ADR-9 hash chain, before/after capture, actor identity incl. device; viewer UI for auditor role.
11. **Consent engine v1:** purpose-scoped consent records (treatment, data sharing/ABDM, communications), digital signature capture (eSign-ready), withdrawal, expiry.
12. **Notification service:** channel adapters (SMS via provider, WhatsApp Business API with template approval, email, in-app, push stub); per-channel consent check; DND hours config.
13. **Offline groundwork:** offline client cache (PWA service worker), write-whitelist framework (empty v1), store-and-forward queue with idempotency keys.
14. **Observability:** OpenTelemetry traces/metrics/logs, Grafana dashboards, error budget for core NFRs.
15. **Tenancy enforcement (D18 → ADR-13):** stage 1 — `hospital_id` NOT NULL, server-side tenant resolution from the principal (client-supplied hospitalId ignored), cross-tenant denial test suite over every tenant-scoped API; stage 2 — Postgres RLS per ADR-2 before chunk 6.

## OUT OF SCOPE (explicit)
Kafka, ClickHouse, Temporal, HAPI FHIR, AI anything (beyond audit of agents identity), billing, clinical documentation, display/TV.

## DATA MODEL (key tables)
`tenant, hospital, location_tree(closure table), bed, staff_user, role_binding, patient, patient_identifier, patient_merge_log(versioned), empi_review_queue, service, price_list, config_document(type,version,jsonb), event_outbox, event_cursor, task(+task_link), audit_event(hash chain), consent, notification_request, consent_channel_optin, offline_queue_inbox`

## APIS (OpenAPI 3.1, all versioned `/v1`)
- `POST /patients`, `GET /patients/{id}`, `POST /patients/merge`, `POST /patients/{id}/unmerge`
- `GET /patients/search?q=` (OpenSearch), `POST /empi/review/{id}:decide`
- `/config/hospitals|locations|beds|services|price-lists` (CRUD, versioned docs)
- `/tasks`, `/tasks/{id}:assign|complete|escalate`
- `/consents`, `/notifications:send`, `/audit/query`
- WS: `/realtime` (event stream, per-user filtered by scopes)

## EVENTS (v1)
`PatientRegistered, PatientUpdated, PatientMerged, PatientUnmerged, TaskCreated, TaskAssigned, TaskCompleted, TaskEscalated, ConsentCaptured, ConsentWithdrawn, ConfigChanged, NotificationDispatched`

## PERMISSIONS (matrix seed)
| Action | Reception | Doctor | Admin | Auditor |
|---|---|---|---|---|
| Register patient | ✔ | – | ✔ | – |
| View patient | ✔ own-queue scope | ✔ | ✔ | ✔ |
| Merge patients | request only | – | ✔ (2-person rule with auditor) | ✔ |
| View audit | own actions | own actions | ✔ | ✔ all |
| Config write | – | – | ✔ | – |

## TESTS
- Safety: merge reversal restores identical record state; audit chain breaks detected on tamper attempt; ABAC denial at dept boundary.
- EMPI: 200-case synthetic set (deterministic auto-link, probabilistic → queue, false-merge probes must route to queue).
- Offline: task completion queued offline replays exactly once (idempotency).
- Load: 1,000 concurrent users, p95 within NFR.

## EXIT CRITERIA (demo scenario)
Admin configures a 2-department hospital with beds and a price list → registers 3 patients (one duplicate) → EMPI flags the duplicate → admin merges with auditor co-sign → every step visible in audit viewer → task "verify merged record" auto-assigned and completed → notifications (SMS + in-app) delivered → chain-integrity check passes.

## ROLLOUT / ROLLBACK
Internal only. DB migrations: forward-only, expand-contract pattern from day 1.

## ADDITIONS (whitelabel, per master plan §14)
- **Brand-parameterized design system:** all product strings externalized; lint rule forbids literal "Squamed"/brand strings in UI code; theme pack per tenant (logo, name, accent, report templates).
- **Entitlement service (stub):** plan/feature registry + enforcement hooks; feature checks call it even where everything is enabled — so chunk 1 can enforce reseller licensing without refactoring.
- ABDM keys, DLT entity, eSign certs are **per-tenant config**, never platform globals.

## OPEN DECISIONS
- WhatsApp BSP provider selection (pricing) — needed before exit demo.
- ABDM sandbox credentials acquisition (lead time ~2–4 wk — start week 1).
