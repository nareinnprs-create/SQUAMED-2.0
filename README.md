# SQUAMED 2.0 — BUILD TRACKER

Vision reference: `SQUAMED — Master Blueprint 2026–2030.md` (Downloads)

## Document index
| Doc | Role |
|---|---|
| `00-MASTER-PLAN.md` | **Read first** — ADRs, regulatory, safety, NFRs, AI contract, timeline, team, risks |
| `01-ENGINEERING-STANDARDS.md` | UI/UX process · frontend · middleware · backend standards |
| `02-DESIGN-SYSTEM.md` | "The Calm Instrument" — tokens, components, accessibility, anti-slop rules |
| `03-BUILD-PROTOCOL.md` | Execution contract — 12-gate DoD, zero-stub policy, ledger, reports |
| `04-CHUNK0-SECTIONS.md` | Chunk 0 build-order section graph (ledger source) |
| `14…21-CHUNK{1…8}-SECTIONS.md` | Section graphs for waves 2–9 — build order, lanes, external flags, all 89 sections pre-authored |
| `22-CS-CASE-MATRIX.md` | Clinical-safety case → section bindings (QA dispatch scope + regression rule) · `.json` sibling = machine-readable export, CI-validated |
| `23-BUSINESS-OPS-CHECKLIST.md` | Non-engineering work: decisions/legal (B1–B8), external-dependency register (E1–E12), weekly aging cadence |
| `24-DECISIONS-REGISTER.md` | Technical open decisions D1–D15 + decided-for-the-record list |
| `reports/LEDGER.md` | **The running ledger** — all 89 sections with status, gates, tests, blocked-flags, scope changes (protocol §3) |
| `05-DATA-MODEL.md` | Unified schema map, conventions, module ownership, invariants |
| `06-API-EVENT-CATALOG.md` | All endpoints + all events, versioning rules |
| `07-SECURITY-ARCHITECTURE.md` | Threat model, crypto/secrets, access, IR runbook |
| `08-TEST-STRATEGY.md` | Test pyramid, environments, clinical-safety case catalog, synthetic data |
| `09-INFRASTRUCTURE.md` | Environments, topology, observability, backup/DR runbooks, costs |
| `10-INTEROP-SPEC.md` | ABDM flows, HL7 channels, FHIR profiles, DICOM, terminology |
| `11-CLINICAL-CONTENT-PLAN.md` | Templates, order sets, packs, authoring workflow, delivery schedule |
| `12-COMPLIANCE-PACK.md` | DPDP map, retention schedule, audit evidence, consent schema |
| `13-AI-BUILD-WORKFORCE.md` | Agent workforce roster, execution loop, human gates, compressed timeline |
| `chunk-00…08-*.md` | The 9 chunks — contracts for build scope |
| `reports/` | LEDGER + per-section, per-chunk, grand-final reports (protocol §4) |

Baseline: `00-MASTER-PLAN.md`. Chunks = contracts; scope changes edit the chunk file first.

## Tracking map (what tracks what — one register per concern, no duplicates)
| Concern | Register | Update rule |
|---|---|---|
| Build progress (all 89 sections) | `reports/LEDGER.md` | every section state change + session-end resume note (protocol §3, §5.4) |
| Scope changes | ledger scope-change log | chunk file edited **first**, then logged with approver (§5.2) |
| Clinical-safety test scope | `22` (+ `.json`) | new case → catalog + matrix before implementation |
| Business + external dependencies | `23` (B1–B8, E1–E12) | weekly aging report; close only with artifact |
| Technical decisions | `24` (D1–D15) | close only with artifact; decided list is append-only |
| Quality gates themselves | `03` §7 (baseline hash, parity test, weekly audit) | drift without amendment = S1 incident |
| Test/system inventory | `05` schema · `06` API/events · `08` CS catalog · `09` infra · `11` content calendar | catalog test + chunk-exit drills |

| # | Chunk | Window | Status | Exit demo |
|---|---|---|---|---|
| 0 | [Foundation](chunk-00-foundation.md) | Dec 2026 → Feb 2027 | PLANNED | Governed platform: config + EMPI merge + audit chain |
| 1 | [OPD & front office](chunk-01-opd-front-office.md) | Mar → May 2027 | PLANNED | Real OPD day end-to-end (queue/TV/consult/eRx) |
| 2 | [Diagnostics & pharmacy](chunk-02-diagnostics-pharmacy.md) | May → Aug 2027 | PLANNED | Order→critical result→report; eRx→safe dispense |
| 3 | [Revenue cycle](chunk-03-revenue-cycle.md) | Aug → Nov 2027 | PLANNED | Bill+GST→TPA claim→settlement; leakage caught |
| 4 | [Inpatient & surgery](chunk-04-inpatient-surgery-critical.md) | Nov 2027 → Mar 2028 | PLANNED | Full hospital: admission→eMAR→OT→discharge→final bill |
| 5 | [AI intelligence](chunk-05-ai-intelligence.md) | Mar → Jun 2028 | PLANNED | Scribe+command bar+3 agents, all eval-gated |
| 6 | [Enterprise & scale](chunk-06-enterprise-scale.md) | Jun → Oct 2028 | PLANNED | 2nd hospital in <1 day; DR kill-test passes |
| 7 | [Full depth](chunk-07-full-depth.md) | Oct 2028 → Feb 2029 | PLANNED | 9 agents, voice ops, digital twin, full GL, full ED, specialty library — no "lite" remains |
| 8 | [Ecosystem](chunk-08-ecosystem.md) | Feb → Jun 2029 | PLANNED | SDK + marketplace + sovereign packaging + 2nd country — blueprint 100% covered |

**This is a COMPLETE final-product build.** Chunks are construction order (like floors of one building), not MVPs — every blueprint section is scheduled. Chunk N exit demo must pass before N+1 starts exit testing · clinical-safety cases green = exit precondition · statuses updated here.
