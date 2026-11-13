# CHUNK 0 — SECTION GRAPH (build order)
**Owner note:** this is the authoritative section sequence the ledger (`reports/LEDGER.md`) tracks. Each section passes the 12 gates of `03-BUILD-PROTOCOL.md` before the next starts. Section graphs for all later chunks exist too: `14-CHUNK1-SECTIONS.md` … `21-CHUNK8-SECTIONS.md` — the ledger tracks all 9 waves from day one; graphs are revised at kickoff only via protocol §5.2 (chunk-file edit + approval).

Cadence target: 12 weeks, 7 engineers. Sections marked ⛔ are critical-path; external dependencies flagged.

| # | Section | Contents | Depends on | Est. | Notes |
|---|---|---|---|---|---|
| 00.01 ⛔ | Infrastructure scaffold | Monorepo (pnpm + Gradle), app skeletons (web, core), CI pipeline with **all blocking gates** (type, lint, stub-ban grep, ArchUnit, test, axe, visual-regression, Lighthouse), **gate-integrity scaffolding: `.gate/` protected path, parity test (protocol §7.2), baseline hash into ledger (§7.3)**, docker-compose dev stack (Postgres 16, Redis 7, Keycloak, MinIO, OTel collector, Grafana, Loki), `reports/LEDGER.md` **verified** (pre-initialized at planning — all 89 sections PLANNED, `reports/LEDGER.md`) | — | 1.0 wk | Gates exist **before** first feature code; gate baseline hashed at exit of this section |
| 00.02 ⛔ | Contract foundation | OpenAPI style guide, error envelope, pagination, idempotency middleware, **event envelope v1 JSON schema**, API-client codegen pipeline, schema-registry test harness | 00.01 | 0.5 wk | Contracts-before-code starts here |
| 00.03 ⛔ | Identity & access | Keycloak realm-as-code, OIDC integration (web + API), staff user entity, role model, RBAC service, MFA for admin/auditor, session + device registry, permission middleware | 00.02 | 1.0 wk | |
| 00.04 ⛔ | Tenancy & RLS | Tenant entity, `tenant_id` on all tables policy, RLS policies, token→tenant resolution, **cross-tenant denial test suite** | 00.03 | 0.5 wk | |
| 00.05 | Org tree & config engine | Location closure table (campus→…→bed), config documents (versioned, validated JSONB), config APIs, audit on config change | 00.04 | 1.0 wk | |
| 00.06 | Master data | Service catalog, price lists, vendor registry, equipment registry, employee master (demographic + credential fields) | 00.05 | 1.0 wk | |
| 00.07 ⛔ | Patient master | Registration, identifiers (MRN/ABHA/mobile/national), demographics, guardian/dependent links, patient photos (MinIO in dev / S3-API prod), Postgres FTS patient search, unknown-patient flow | 00.06 | 1.0 wk | ABDM sandbox creds requested week 1 ⛔ external |
| 00.08 ⛔ | EMPI | Deterministic matcher, probabilistic scorer (threshold ≥0.9), review queue UI, versioned reversible merge/unmerge, 200-case synthetic eval | 00.07 | 1.0 wk | Two-person merge rule (admin + auditor) |
| 00.09 ⛔ | Event fabric | Transactional outbox, dispatcher, Redis pub/sub, WebSocket gateway (per-user scope filter), replay CLI, DLQ + integration error queue | 00.02 | 1.0 wk | Parallel lane after 00.02 |
| 00.10 ⛔ | Audit | Write-path interceptor, hash-chained audit table, auditor viewer, nightly chain-verify job (**pages on breach**), WORM export job (MinIO dev / S3 Object Lock prod config) | 00.03 | 1.0 wk | Parallel lane |
| 00.11 | Task engine | Task CRUD + assignment + completion + SLA escalation rules (config-driven), subject links (patient/config/dept), inbox UI | 00.05, 00.09 | 1.0 wk | |
| 00.12 | Consent + notifications | Consent records (purpose/scope/expiry, eSign-ready signature capture), withdrawal, channel interface: **email adapter live (SMTP)**, SMS/WhatsApp adapters code-complete against provider sandboxes (EXTERNAL-BLOCKED on DLT header/WABA approval), DND rules, consent-check-before-send | 00.03 | 1.0 wk | ⛔ external: DLT + WABA |
| 00.13 | Offline groundwork | PWA service worker (app-shell cache), IndexedDB store-and-forward queue, idempotent replay, reconnect UX | 00.09 | 0.5 wk | Whitelisted offline writes: task complete |
| 00.14 | Design system | Tokens (`02-DESIGN-SYSTEM.md` §2–4), primitives (Button, Field, Chip, StatusDot), DataTable (density modes), Storybook + visual regression | 00.01 | 1.0 wk (overlaps) | Designer parallel |
| 00.15 ⛔ | App shell | Nav rail, hospital selector, universal search (OpenSearch index + pipeline), notification center, task inbox, user/security menu, i18n (en/hi, ICU, no-hardcoded-string lint live) | 00.13, 00.14 | 1.0 wk | |
| 00.16 | Observability completion | OTel spans HTTP→service→DB→WS, RED dashboards per module, SLO burn alerts (NFR §6), audit-breach + DLQ alert routing | 00.09, 00.10 | 0.5 wk | |
| 00.17 ⛔ | **Chunk exit** | Exit demo (chunk-00 file), load test 1,000 users, backup+restore drill, tamper test (audit chain breach detected), tamper-proof report, `reports/chunk-00/FINAL.md`, sign-off | all | 0.5 wk | |

**Parallelization:** lanes (00.03→00.04→00.05→00.06→00.07→00.08) and (00.09→00.10→00.16) and (00.14→00.15) run in parallel; 00.11–00.13 fill remaining capacity.
