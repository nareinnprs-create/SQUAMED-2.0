# SQUAMED 2.0 — MASTER PLAN
**Status:** Approved planning baseline · **Version:** 1.0 · **Date:** 2026-11-12
**Supersedes:** `SQUAMED — Master Blueprint 2026–2030.md` (kept as vision reference)
**Market:** India first · **Team:** 6–8 engineers + 1 clinical lead + 1 designer · **Window:** Dec 2026 → Jun 2029 (**complete product**, chunks 0–8), H2 2029+ commercial scale-out

---

## 1. WHAT CHANGED VS THE ORIGINAL BLUEPRINT

The original document is a strong **vision**. It is not buildable: no data models, no APIs, no NFRs, no dates, no regulatory specificity. This plan converts it into a build baseline with these corrections:

| Original problem | Decision taken |
|---|---|
| §106 "Foundation MVP" framing | Rejected — this is a **complete-product build** in 9 construction stages (chunks 0–8); every blueprint section §1–§117 is scheduled, nothing deferred (coverage in §7) |
| Offline mode vs real-time default unresolved | Closed by ADR-4: event-sourced outbox + offline write-whitelist + store-and-forward (§6) |
| 6 tech stacks + 6 infra systems | Closed by ADR-5/6: Kotlin/Spring + React/TS; PWA-first; infra starts Postgres+Redis only |
| No terminology standards | Closed: ICD-10, SNOMED CT (free in India via NRCeS), LOINC; brand→generic drug master (§3) |
| No regulatory layer | Closed: India regulatory matrix (§4) |
| No clinical safety engineering | Closed: safety standard (§5) |
| AI governance without contracts | Closed: AI contract (§8) |
| No KPI formulas, no unit economics | Closed: §9, §10 |

---

## 2. ARCHITECTURE DECISION RECORD (closed decisions)

| ADR | Decision | Rationale |
|---|---|---|
| ADR-1 | **Modular monolith** (`squamed-core`, Kotlin/Spring), modules = Maven modules with enforced boundaries (ArchUnit). Extract a service only when a chunk proves the need | Distributed complexity is the #1 killer of hospital IT projects |
| ADR-2 | **Multi-tenancy: single database, row-level security** on `tenant_id` (Postgres RLS), Postgres schema-per-tenant deferred to enterprise hospitals (chunk 6) | One codebase, cheapest isolation that passes audit; escape hatch exists |
| ADR-3 | **Identity:** Keycloak (OIDC) for staff; ABHA linked to patient master; every AI agent = first-class OIDC client with its own scopes | Buy, don't build; ABDM requires ABHA linkage |
| ADR-4 | **Events:** transactional outbox in Postgres → dispatcher → Redis pub/sub + WebSocket for realtime. Kafka enters at chunk 6 if throughput demands | Same event contracts either way; no Kafka tax on day 1 |
| ADR-5 | **Frontend:** React + TypeScript PWA (installable = covers desktop + tablet + mobile v1). Tauri shell and React Native deferred to after chunk 4 | One UI codebase until native is proven necessary |
| ADR-6 | **Infra at start:** Postgres + Redis only. OpenSearch (chunk 1, universal search), ClickHouse (chunk 6, BI), Temporal (chunk 6, long workflows) enter when needed | Minimal moving parts; each addition tied to a chunk |
| ADR-7 | **Buy list:** Keycloak (IAM), HAPI FHIR server (interop), Orthanc (DICOM PACS), OHIF (viewer), Mirth Connect (HL7 v2 engine, chunk 2), device gateways vendor-provided | Never rebuild healthcare standards middleware |
| ADR-8 | **EMPI:** deterministic match (ABHA ID, mobile+DOB+name-exact) → auto-link; probabilistic match (fuzzy name+DOB+phone, threshold ≥0.9) → **manual review queue**, never auto-merge; merges are versioned and reversible | Wrong-patient merge is a clinical-safety hazard |
| ADR-9 | **Audit:** append-only table, hash-chained per tenant (`prev_hash` in each row), exported nightly to WORM object storage, retention 10 years | "Tamper-resistant" with an actual mechanism |
| ADR-10 | **Consent:** explicit, per-purpose, per-channel, versioned, digitally signed (IT Act 2000 §3A). Treatment consent is implied-by-context for emergency care with mandatory post-hoc recording | DPDP compliance; emergency reality |

---

## 3. TERMINOLOGY & CODING STANDARD

| Domain | Standard | Source/License |
|---|---|---|
| Diagnoses | **ICD-10** (ABDM profile) | WHO / NRCeS mapping |
| Clinical terms | **SNOMED CT** | Free for India via NRCeS affiliate license |
| Lab observations | **LOINC** | Free (Regenstrief) |
| Drugs | Indian **brand→generic master** (curated dataset ~100k SKUs) + open interaction rules (DrugBank open subset) seeded; upgrade path: licensed DB (Medi-Span/First Databank India feed) when >3 hospitals live | Cost line in §10 |
| Procedures | ICD-10-PCS equivalent: Indian modifier set + internal procedure master with SNOMED mapping | Internal master |
| Adherence | **All clinical entries carry at least one standard code**; free text allowed only alongside a code. Enforced at write time | Platform rule |

Terminology service: lightweight internal service (tables + mapping + search) in chunk 1; HAPI FHIR terminology ops in chunk 6.

---

## 4. INDIA REGULATORY MATRIX (first market)

| Regulation | Scope | Platform impact | Chunk |
|---|---|---|---|
| **DPDP Act 2023** | Personal data, consent, breach notification | Consent engine, purpose limitation, breach workflow, DPO reports, data erasure for non-clinical data (clinical records retained under medical regs) | 0, ongoing |
| **ABDM (Ayushman Bharat Digital Mission)** | ABHA ID, HIP participation, health information exchange | ABHA linkage at registration, ABDM sandbox integration, consent-artifact handling for record sharing | 0–1 |
| **EHR Standards 2016 / EHR-M v2** | Interop coding standards | SNOMED CT / ICD-10 / LOINC adherence (§3) | 1–2 |
| **GST** | Pharmacy sales (5%/12% slabs), healthcare services exempt | Tax engine must distinguish service (exempt) vs goods (taxed); HSN codes; e-invoicing threshold handling | 3 |
| **Insurance (TPA/CGHS/ESIC/PM-JAY)** | Claim formats | Adapter pattern: cashless pre-auth formats (TPA portal-specific), PM-JAY package codes; no X12 (that's US) | 3 |
| **Telemedicine Practice Guidelines 2020** | Remote consults | Registered Medical practitioner verification, consult-type rules (first consult via video), prescription validity | 6 |
| **IT Act 2000 §3A** | E-signature validity | Clinician signing = Aadhaar eKYC/eSign or certified signature; signing events audited | 1 |
| **Record retention** | Medical records | Default 10 years; medico-legal cases legal-hold (indefinite until released); minors: 10y past age 18 | 0 |
| **CEA (Clinical Establishments Act)** | Varies by state | Configurable register/reporting pack per state | 6 |
| **NABH** | Accreditation | Quality module evidence pack (incident, CAPA, audit trails) — alignment, not full QMS | 6 |

**Upgrade path for new markets:** each regulation = config + adapter module (tax adapters, claim adapters, ID schemes). No core rewrite. (Blueprint §75 honored.)

---

## 5. CLINICAL SAFETY STANDARD (applies to every chunk)

1. **Two-identifier rule:** any patient-scoped action (order, dispense, administer, result, bill) requires active confirmation of **name + MRN/ABHA**, displayed together. Enforced in UI components, not conventions.
2. **Wrong-patient hazard register:** maintained per chunk; each new clinical feature adds its hazards with mitigations (e.g., merge → reversible; eMAR → barcode scan of patient + drug wristband/label).
3. **High-alert medications list** (insulin, anticoagulants, opioids, chemo, concentrated electrolytes): independent double-check workflow enforced (second authorized user confirms before administration).
4. **Allergy & interaction alerts:** severity taxonomy — `BLOCK` (documented anaphylaxis match), `WARN` (override with reason, audited), `INFO`. Overrides tracked as an alert-fatigue KPI.
5. **Critical results:** lab critical value → escalate within configured SLA (default 15 min) through task engine with ack trail; unacked → auto-escalate to department head.
6. **Downtime paper fallback:** per-module printed procedure pack + re-entry tool (chunk 1+); downtime drill once per chunk in test hospital.
7. **AI safety:** AI never writes to the legal clinical record directly — drafts only, human signature required (except non-clinical ops artifacts, which still require policy-tagged approval). See §8.
8. **IEC 62304 posture:** Squamed is classed **Class B** (could cause harm, non-life-sustaining) — risk-managed development process, documented verification per safety-critical module. (Not a device filing today; revisited if decision-support becomes prescriptive.)

---

## 6. NFR REGISTER (numbers)

| NFR | Target |
|---|---|
| Core availability (registration, EMR, orders, eMAR, billing) | 99.9% monthly |
| Display/queue screens | Degrade gracefully offline (cached tokens, reconnect) — availability of *hospital process* > API uptime |
| RTO / RPO | 4 h / 15 min (Postgres PITR + nightly WORM audit export) |
| API latency | p95 < 300 ms reads, < 500 ms writes, per-hospital LAN or India-region cloud |
| Concurrency | 1,000 concurrent authenticated users per hospital; 200 WebSocket clients per display zone |
| Audit retention | 10 years, WORM |
| Scribe latency | draft ≤ 60 s after stop-dictation; STT streaming < 1.5 s trailing |
| Command-bar query | p95 < 3 s including retrieval |
| Mobile/PWA first paint | < 3 s on 4G mid-range Android (nurse device reality) |
| Accessibility | WCAG 2.2 AA, verified per chunk |
| Languages | UI: English + Hindi (i18n from chunk 0); India regional packs configurable |

---

## 7. MASTER TIMELINE — COMPLETE PRODUCT BUILD (no MVP; chunks are construction order, not scope cuts)

| Chunk | Window | Duration | Milestone |
|---|---|---|---|
| 0 — Foundation | Dec 2026 → Feb 2027 | 12 wk | Governed platform, hospital configurable, EMPI live |
| 1 — OPD & front office | Mar → May 2027 | 12 wk | OPD runs end-to-end on Squamed |
| 2 — Diagnostics & pharmacy | May → Aug 2027 | 14 wk | Order→result→report; prescribe→dispense safely |
| 3 — Revenue cycle | Aug → Nov 2027 | 12 wk | Billing + tax + TPA claims + integrity engine |
| 4 — Inpatient & critical | Nov 2027 → Mar 2028 | 16 wk | Full hospital: OPD + IPD + OT + ICU live |
| 5 — AI intelligence | Mar → Jun 2028 | 14 wk | Scribe, command bar, 3 agents, governance |
| 6 — Enterprise & scale | Jun → Oct 2028 | 16 wk | Multi-hospital, telemedicine, BI, DR drill passed |
| 7 — **Full depth** | Oct 2028 → Feb 2029 | 16 wk | All 9 agents + orchestration, voice ops, digital twin, full GL finance, full ED, wayfinding, specialty library, QMS/IPC/legal, migration studio, benchmarking/ESG — **no "lite" remains** |
| 8 — **Ecosystem** | Feb → Jun 2029 | 16 wk | Developer platform, agent SDK, marketplace, sovereign/on-prem productization, 2nd country pack, multi-entity finance — **blueprint §78–80, §72, §75 complete** |
| H2 2029 → 2030 | — | — | Commercial scale-out: hospital acquisitions, reseller channel (§14 Tier 2), benchmarking network |

Blueprint coverage: §1–§77 → chunks 0–7 · §78–85, §23-depth, §37-full → chunks 7–8 · §86–117 (design/quality/architecture rules) → enforced across all chunks from chunk 0.

Dependency rule: chunk N cannot start exit-criteria testing until chunk N−1 exit demo passes. Chunks 2 and 3 can overlap one sprint after chunk 1 exit (team of 8 supports one parallel lane).

---

## 8. AI CONTRACT (chunk 5 governs; principles bind from chunk 0)

1. **No unrestricted data access:** every AI call executes as an OIDC client with the invoking user's effective scopes (or its own narrower agent scopes). Row-level security applies to AI like to any user.
2. **PHI to models:** only India-region or zero-retention endpoints under DPA; prompt-level redaction of direct identifiers where the task allows; no PHI in prompt caches.
3. **Grounding:** clinical/operational answers must cite record references; ungrounded claims marked as inference.
4. **Actions:** AI proposes → governed approval UI → human authorizes → platform executes. Timeouts return proposals to queue, never auto-approve. Emergency overrides are human-only.
5. **Evaluation:** each capability ships with an eval set + pass thresholds (scribe: completeness ≥95% of dictated facts, hallucination = 0 critical-field fabrications on eval set; command bar: permission-leak = 0, citation correctness ≥95%). Fail threshold = feature flag off, not a warning.
6. **Kill switch:** per-capability flag, revocable in <1 min, audited; **daily hard cap per tenant per capability — hard cut at 100% (degrade gracefully, never silently exceed), budget alerts to owner at 50%/80%**, per-capability cost ledger visible in governance dashboard.
7. **Registry:** every prompt/model/agent version registered; production changes require eval evidence attached to the release.
8. **Custom providers are mandatory (not optional):** the gateway must accept **any custom API provider** — any OpenAI-compatible endpoint (base URL + key + model id), self-hosted models (vLLM/Ollama/etc.), and hospital-supplied or reseller-supplied provider contracts — as first-class routing targets. Tenants configure their own providers per environment; all §8 rules (tracing, redaction, eval, caps, kill switch) apply identically to custom providers. No Squamed feature may depend on one vendor's API shape.

---

## 9. KPI DICTIONARY (extract — full version lives with chunk 6)

| KPI | Formula |
|---|---|
| Bed occupancy | occupied bed-hours ÷ (beds × 24h) |
| ALOS | mean(discharge − admission) per specialty |
| OPD wait time | consult-start − token-issued, p50/p90 |
| Lab TAT | result-validated − sample-collected, per test class |
| OT utilization | in-use OT-minutes ÷ scheduled OT-minutes |
| Claim denial rate | denied claims ÷ submitted claims (value-weighted) |
| Revenue leakage | unbilled detected services ÷ total detected services |
| eMAR on-time | doses administered in window ÷ scheduled doses |
| Alert override rate | overridden alerts ÷ fired alerts, per severity |
| Critical-result ack time | ack − validation, p90 vs SLA |

---

## 10. COST MODEL (unit economics — drives pricing §81)

| Item | Assumption | Enter at |
|---|---|---|
| LLM (note drafting, command bar) | ₹–$ per 1M tokens, cap ₹X/tenant/day via gateway | 5 |
| Medical STT | per-minute pricing; scribe = ~5 min audio/consult | 5 |
| TTS (queue announcements) | browser/OS TTS free tier; cloud neural voices per 1M chars | 1 |
| SMS/WhatsApp | per-message; WhatsApp requires opt-in consent template approval | 1 |
| Drug DB (licensed, later) | annual license per hospital | 3+ |
| Storage | ~50 MB/DICOM study, 10y retention, WORM audit ~2 GB/tenant/yr | 2, 0 |
| Infra baseline (2 hospitals) | Postgres+Redis+app+OHIF/Orthanc, India region | 0 |

---

## 11. TEAM & OPERATING MODEL
> **Execution model superseded by `13-AI-BUILD-WORKFORCE.md`:** the build workforce is AI agents (Builder/QA/Security/Design/DevOps/Content agents under an orchestrator) — timeline compressed ~45% (→ ~Q1 2028). The roles below remain the *accountability map* (who signs off what), and §103 governance is unchanged.

| Role | Count | Owns |
|---|---|---|
| Tech lead / architect | 1 | ADRs, boundaries, review gate |
| Backend engineers (Kotlin) | 3 | Core modules, events, audit |
| Frontend engineers (React/TS) | 2 | Shell, design system, workspaces |
| Clinical lead (MD/Nursing background) | 1 | Clinical content, safety register, UAT with pilot hospital |
| QA/automation engineer | 1 | Test hospital, regression, safety cases |
| Designer (shared 50%) | 1 | Design system, workflows |
| DevOps (shared 50%) | 1 | CI/CD, environments, DR drills |

Governance (blueprint §103, honored): clinical safety, security, AI changes require clinical-lead + tech-lead sign-off in release checklist. Pilot hospital partner identified before chunk 1 exit.

---

## 12. RISK REGISTER (structural fixes — each risk reduced to residual, not managed by hope)

| # | Risk | Structural fix (where it lives) | Residual |
|---|---|---|---|
| 1 | Offline sync bugs corrupt records | **Sync chaos simulator** in CI (08 §1): replays every queued write under random drop/duplicate/reorder/partition; property test: replay = exactly-once, net effect identical to online path; CS-08 extended | Exotic device clocks |
| 2 | Drug DB open-data gaps → missed interaction | Dual-source checking (open dataset + rule engine) with **provenance + confidence on every rule**; `BLOCK` fires only ≥ confidence threshold; chunk-2 eval gate ≥98% / 0 false-BLOCK; licensed DB upgrade path pre-wired (adapter) | Truly novel interactions until licensed feed |
| 3 | EMPI false merges | Never auto-merge probabilistic matches; adversarial merge eval set (500 cases incl. twins/name-clones) must route 100% of traps to review queue; merge = snapshot + reversible pointer (05 §2 invariant); post-merge state-identity test | Judgement calls inside threshold band → human queue (by design) |
| 4 | Scope creep | Chunks are contracts; scope change = edit chunk file first + product-owner approval; **scope-change log column in ledger** (03 §3) — every addition visible with approver | Your own late additions — priced honestly when they happen |
| 5 | First-hospital readiness | 3-candidate pipeline from chunk 0; sandbox hospital covers all training so go-live ≠ first contact; chunk-1 exit gate stays: no partner signed, no chunk 2 start | Hospital-side delays — external track, escalation dates enforced |
| 6 | AI cost overrun | Daily hard cap per tenant per capability (**hard cut at 100%, not soft warn**), budget alerts at 50/80% to owner dashboard (chunk 5), kill switch < 1 min, per-capability cost ledger | Provider price hikes — router re-prices automatically |
| 7 | DPDP/ABDM spec drift | Sandbox versions pinned; adapters isolate every external contract; **standing sprint task: check ABDM/DPDP change feed**, diffs become chunk-file edits via protocol §5.2 | Late breaking regulation — legal review cadence quarterly |
| 8 | Clinical content bottleneck | Primary clinical lead + **named backup advisor per specialty from day 1** (13 §6); content calendar runs 2 weeks ahead of chunks (§11); batch authoring | Backup unavailable same week — calendar buffers absorb |
| 9 | Architect key-person risk | ADRs 1–10 + enforced boundaries (ArchUnit) + docs-first routing (13 §4); **architecture continuity drill at every chunk exit**: a fresh agent builds a throwaway module using only the docs — if it can't, the docs fail the chunk | None meaningful post-drill |
| 10 | Display/announcement hardware quirks | **Hardware lab bought at chunk-1 kickoff** (one representative of each surface: Android TV box, thermal printer, kiosk tablet, barcode scanner); soak tests per surface in CI-adjacent manual gate; browser-first stack keeps vendor lock minimal | Unusual site-specific hardware — pilot audit covers |
| 11 | Gate suite weakened/bypassed silently by build agents | Gate-integrity §7 (read-only `.gate/`, parity test, baseline hash, evidence cross-check, no self-merge, 20% blind re-verification, weekly audit) | Compromised repo host / colluding human — trust boundary, WORM audit is the recorder |
| 12 | Build-session loss / context rot (agent workforce) | Sections are the unit of continuity: fresh session per section fed by chunk file + prior report (13 §4); ledger + exact resume point at every session end (protocol §5.4); commits only at section boundaries | Multi-day interruption mid-section — bounded by section size (≤1 wk) |
| 13 | Compressed timeline collapses if human gates stall | Every EXTERNAL-BLOCKED row carries owner + escalation date (protocol §2); content deadlines 2 wk ahead (§11); weekly status to you flags any human-gate aging > 5 working days | Your availability — decisions batched at chunk kickoffs/exits to fit your calendar |

---

## 13. REPOSITORY STRUCTURE (authoritative, supersedes blueprint §97)

```text
squamed/
├── apps/
│   └── web/                    # React PWA (all personas; installable = desktop/tablet/mobile v1)
├── backend/
│   └── squamed-core/           # Kotlin modular monolith (module-per-domain, ArchUnit enforced)
├── services/                   # added only when a chunk needs them
│   ├── ai-gateway/             # chunk 5 (TS or Kotlin — decided at chunk 5 kickoff)
│   └── interop/                # HAPI FHIR + Mirth configs (chunk 2+)
├── packages/
│   ├── design-system/          # tokens, components (chunk 0)
│   ├── api-client/             # generated from OpenAPI
│   └── schemas/                # event envelope + OpenAPI + FHIR profiles
├── infrastructure/
│   ├── compose/                # dev: postgres, redis, orthanc, hapi, keycloak
│   ├── k8s/                    # chunk 1 exit: first real deployment
│   └── observability/          # OpenTelemetry + Grafana from chunk 0
├── docs/
│   ├── chunks/                 # THE 7 CHUNK FILES LIVE HERE (source of truth for tracking)
│   ├── adr/                    # ADR-1..10 expanded
│   └── clinical/               # safety register, content templates
└── tests/
    ├── clinical-safety/        # two-identifier, high-alert, critical-result cases
    └── e2e/                    # chunk exit demo scenarios, automated
```

---

## 14. WHITELABEL & MULTI-BRAND MODEL (two tiers, both supported)

**Tier 1 — Tenant theming (from chunk 0, mandatory):**
Every hospital tenant carries its own brand pack: logo, product name, accent color (within the semantic-color rules of §86), report/receipt templates, patient-facing sender names. Clinical tools show hospital branding; "Squamed" never appears in patient-visible surfaces. Design tokens are brand-parameterized from day 1 — no hardcoded product strings in UI, emails, PDFs, or exports.

**Tier 2 — Full whitelabel / reseller channel (requirement, lands incrementally):**
An authorized reseller/SI may operate Squamed under their own brand. Requirements:

| Area | Requirement | Lands in |
|---|---|---|
| Entitlement & licensing | Blueprint §81 entitlement layer moves up: feature/module/AI-usage gating per tenant + reseller plan definitions, enforced from first external tenant | chunk 0 (service + stubs) → chunk 1 (enforcement) |
| Brand isolation | Brand pack per reseller (not just per hospital); reseller admin portal; zero "Squamed" strings in any surface — enforced by lint rule on string literals + snapshot tests | chunk 0–1 |
| SMS (India DLT) | Sender headers/templates registered per operating entity; notification service resolves DLT entity per tenant at send time | chunk 1 (consent + notification layer) |
| WhatsApp BSP | Per-brand WABA display names + template sets; template approval tracked per brand | chunk 1 |
| ABDM / HFR identity | Facility registered under hospital's (or reseller's contracted) entity; ABDM integration keys are per-entity config, not platform-global | chunk 0 (config schema) |
| eSign / certificates | Signing certificates belong to the hospital entity; platform never signs in its own name | chunk 1 |
| Support identity | Reseller-facing support model: reseller answers under their brand; Squamed runs L2 behind the reseller. Operational, not code — contract template in docs | chunk 6 (channel launch) |
| Billing | Reseller plan pricing + per-tenant invoicing data in licensing service | chunk 3 (aligns with billing engine) |

**Cost:** ~2–3 engineering weeks total spread across chunks 0–1, plus contract/legal work. Doing Tier 2 foundations in chunk 0 costs almost nothing extra because tokens and entitlements are being built anyway; retrofitting after chunk 3 would be a rewrite of surfaces.

---

## 15. HOW THIS PLAN IS TRACKED

- Each chunk = one `.md` in `docs/chunks/` with status header (`PLANNED → IN PROGRESS → EXIT REVIEW → DONE`).
- Chunk exit = the demo scenario passes live + safety cases green + sign-off.
- Changes to scope require editing the chunk file first — the file is the contract.
