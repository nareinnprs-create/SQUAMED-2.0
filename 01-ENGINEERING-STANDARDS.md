# ENGINEERING STANDARDS — UI/UX · FRONTEND · MIDDLEWARE · BACKEND
**Status:** Baseline for all chunks · Supersedes ad-hoc choices · Referenced by every chunk file

---

## 1. UI / UX STANDARD

### Process (every chunk)
1. Chunk kickoff → **screen inventory** (persona × task → screens) → wireframes → clinical-lead review → build. No screen is built without an inventory entry.
2. Chunk exit requires: designer + clinical-lead sign-off per screen + usability run of the exit scenario with actual staff role-players.

### Navigation model
- Shell + workspace pattern (chunk-00). **Any screen ≤ 2 clicks from Patient 360.**
- High-volume roles get keyboard-first flows (reception, lab, pharmacy, billing): every common action has a shortcut; forms are tab-order-complete.
- Command bar (chunk 5) is an accelerator, never the only path.

### Interaction rules
| Rule | Standard |
|---|---|
| Forms | Inline validation, autosave drafts for long documents (notes, summaries), dirty-state guard on navigation |
| Tables | Server-side paging/filter/sort, saved views per user, bulk actions only where audited |
| Confirmations | Only for destructive/high-risk actions (§5) — never habit-forming noise; confirm text names the patient (two-identifier rule) |
| Feedback | Skeletons for loads, toasts for success/failure, no blocking spinners > 2 s; errors say what to do next |
| Status | Colour **+ icon + text** (colour-independent, §91) |
| Empty states | Always actionable ("no pending tasks — create/view completed") |

### Responsive matrix
| Surface | Layout |
|---|---|
| Desktop ≥ 1280 | Multi-pane workspaces (list + detail + rail) |
| Tablet 768–1279 | Split view, touch targets ≥ 44 px, landscape bedside flows |
| Mobile < 768 | Single column, task-oriented (approvals, eMAR, vitals, queue) |
| TV | Read-only, 10-foot typography, auto-refreshing, offline-cached |
| Kiosk | Guided wizard, locked chrome, accessibility mode (large type, high contrast) |

### AI UX (chunk 5+)
`ai_generated` provenance rendered visually distinct; approval cards (propose/approve/reject/expire); intelligence rail capped per shift; every AI surface has a "why am I seeing this" affordance.

### Visual modes (blueprint §86–87)
Clinical (default) · Enterprise (dense) · Command (command centre only). Theme tokens are tenant-brand-parameterized (master plan §14).

---

## 2. FRONTEND STANDARD

| Decision | Choice |
|---|---|
| Framework | React 18 + TypeScript + Vite (PWA) |
| Routing | React Router, route-per-workspace, lazy-loaded per route |
| Server state | **TanStack Query** (caching, invalidation from WS events) |
| Client/UI state | **Zustand** (session, patient context, UI prefs) |
| API client | Generated from OpenAPI → `packages/api-client` (typed end-to-end, no hand-written fetch) |
| Forms | react-hook-form + zod; zod schemas shared via `packages/schemas` (frontend + backend validate from one contract) |
| Real-time | One WS manager per tab: auto-reconnect + backoff, resubscribe, event → query invalidation map; stale-on-disconnect banner |
| Offline | Workbox service worker; IndexedDB store-and-forward for whitelisted writes only (ADR-4), idempotency keys per queued mutation |
| i18n | ICU message format, en + hi baseline, RTL-ready; **lint rule: zero hardcoded strings** (whitelabel §14) |
| Entitlements | Feature-flag client reads entitlement service; hidden ≠ secured — backend always enforces |
| Testing | Vitest (unit) + Testing Library + MSW (API mocks) + Playwright (E2E = chunk exit scenarios automated) |
| Performance budget | ≤ 300 KB gz initial per route, LCP < 3 s mid-range Android/4G, interaction < 200 ms; budget enforced in CI (Lighthouse CI) |
| Observability | OpenTelemetry browser SDK: route change spans, WS drop metrics, JS error reporting |

---

## 3. MIDDLEWARE STANDARD (within squamed-core + edge services)

| Concern | Standard |
|---|---|
| API posture | OpenAPI-first (contract committed before code), REST `/v1`, envelope: `{data, error{code,message,details}, meta{page}}` — one error catalogue, no ad-hoc shapes |
| Idempotency | All POSTs accept `Idempotency-Key`; keys stored with response for 24 h; required for offline-replayable writes |
| Pagination | Cursor-based, standard params everywhere |
| Events | Envelope v1 (chunk-00), outbox dispatcher, **consumers idempotent by event id**, DLQ + integration error queue with alert, replay CLI tool |
| Auth flow | Keycloak OIDC → JWT → scope mapping → ABAC policy service (org-tree node scopes) → Postgres RLS (`tenant_id`); cross-tenant denial test runs in CI |
| Jobs | Quartz in-process for schedules (expiry scans, roster generation, recon runs, audit-chain verify nightly). Temporal only per chunk-6 evidence-based decision |
| Files | S3-API abstraction (R2/MinIO/S3), presigned upload/download, AV-scan hook, DICOM only via Orthanc routes — app never parses DICOM |
| Print | Server-side PDF (reports, receipts, prescriptions — legal documents render server-side for auditability); labels via browser print |
| Search | Postgres FTS first; OpenSearch sync pipeline (chunk-00) only for universal search + patient lookup |
| Notifications | Channel adapters behind one interface (SMS/WhatsApp/email/in-app/push), consent + DND checked pre-send, per-tenant DLT/WABA brand resolution (§14) |
| Observability | OTel end-to-end (HTTP → service → DB → WS), RED dashboards per module, alert rules wired to on-call from chunk 1, SLO burn alerts on NFR §6 numbers |

---

## 4. BACKEND STANDARD

| Concern | Standard |
|---|---|
| Architecture | Modular monolith, Kotlin/Spring; module-per-domain; ArchUnit enforced |
| Dependency matrix | Domain modules **never import each other's internals** — only published `api` packages or domain events. Shared kernel: identity, patient context, money, audit. Matrix lives in `backend/squamed-core/architecture-test/` and is a named test |
| Transactions | One transaction per use-case boundary; event writes share the transaction (outbox); no cross-module entity joins — read models via events if needed |
| Money | Integer paise everywhere, currency-aware type, rounding rules centralized (chunk 3 owns the rules) |
| Tenancy | `tenant_id` on every table, RLS on, tenant resolved from token — never from request body |
| Migrations | Flyway, forward-only, expand-contract; every chunk ships rollback notes (mostly flag-off + read-only freeze, per chunk files) |
| Validation | Trust-boundary validation at API edge (zod-shared schemas compiled to Kotlin contracts / Bean Validation), re-validation at persistence for safety-critical fields |
| Testing | JUnit5 + Testcontainers (Postgres/Redis/Keycloak), ArchUnit suite, JSON-schema contract tests for events in CI, load test at every chunk exit (NFR §6) |
| Audit | One interceptor: all writes emit audit events with before/after; hash chain verified nightly; chain-breach = page, not log line |
| Clinical safety hooks | Two-identifier verification service + allergy/interaction check service are platform services every clinical write path must call — enforced by ArchUnit (domain rules), tested in `tests/clinical-safety/` |

---

## 5. WHAT THIS STANDARD DOES NOT DECIDE
- Video provider, AI providers, BSP/SMS vendors — per-chunk open decisions (procurement).
- Kubernetes distribution/cloud provider — deployment ADR at chunk-1 exit; compose is the dev truth until then.
- Tauri/RN adoption — evidence-based decision points per ADR-5.
