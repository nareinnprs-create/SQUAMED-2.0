# CHUNK 07 — FULL DEPTH COMPLETION
**Status:** PLANNED · **Window:** Oct 2028 → Feb 2029 (16 wk) · **Depends on:** chunk 06 exit
**Purpose:** Close every blueprint section at full depth. Nothing remains "lite" after this chunk. The product is complete at exit.

## IN SCOPE (all previously lite/deferred items, now final)
1. **All 9 agents + multi-agent orchestration (blueprint §58–59):** adds Clinical, Pharmacy, Facilities, Procurement, Security, Executive agents to the 3 from chunk 5; orchestrator decomposes cross-domain questions ("why is today's surgical schedule delayed?") into agent queries with synthesized blocker → owner → impact → recommended action. All §8 contract rules apply; eval sets per agent.
2. **Voice operating interface (§57):** voice task execution under governed approval flow (approve-by-voice for low-risk actions, approval-UI for high-risk), multilingual intent layer, screen-aware context, escalation-to-human. Dictation-only limitation from chunk 5 removed.
3. **Hospital digital twin + capacity intelligence (§53–54):** live operational model (beds/OT/staff/diagnostics/equipment/demand), constraint analysis answering "can we take 18 more admissions tonight?" with evidence.
4. **Full financials (§37):** GL, AP, AR, cash/bank recon, cost centres, budget vs actual, forecast, asset accounting, financial reporting, tax filing packs (GST returns), audit integration. Group multi-entity consolidation in chunk 8.
5. **Full ED & trauma (§11):** triage full (ATS/ESI-configurable), resus bay workflows, trauma team activation, mass-casualty mode, ED dashboards. Replaces chunk-1 triage-lite completely.
6. **Full kiosk + wayfinding (§8):** payment kiosk, report collection kiosk, indoor navigation with live routing (map config per campus), multi-language.
7. **Allied health full (§24):** physio/diet/OT/speech/rehab/social-work/psychology documentation + appointment + care-plan integration.
8. **Specialty package library (§23):** the 22 blueprint specialties shipped as configuration packages (order sets, note templates, flowsheet scales, coding favorites) — validates "configure, don't fork" at scale.
9. **Contact centre (§43), international patient desk (§44), patient-experience/CRM depth (§42):** full modules with agent-assist.
10. **Quality & compliance full (§38–41):** complete QMS (indicators, accreditation evidence packs, CAPA, audit program), full IPC (surveillance, outbreak, device-associated, hand hygiene), legal/risk (litigation hold, risk registers, evidence preservation).
11. **Migration studio (§76):** extract→map→clean→transform→validate→import→reconcile pipeline with rollback, for HIS/EMR/LIS/ERP/Excel sources.
12. **Benchmarking (§84) + ESG (§85):** anonymized aggregate benchmarking (consent-gated), energy/waste/carbon/ESG reporting.
13. **Lab & support depth (binds chunk-2/4 deferrals):** microbiology full ID/AST (organism ID, AST panels, antibiogram), cytology/histopathology full slide workflow (gross → slide → pathologist sign-out), blood-bank NAT screening integration, full food-service ops (kitchen production planning, tray delivery tracking, nutrition-linked menus beyond chunk-4 dietary-lite).

## OUT OF SCOPE
Marketplace/SDK/dev portal → chunk 8. Second-country regulatory pack → chunk 8.

## DATA MODEL / APIS / EVENTS
Per-module; modeled identically to chunks 1–6 (same domain spec template, same standards §3–§6). Digital twin = materialized operational views over the event fabric, not a new database.

## SAFETY
- Voice actions: high-risk categories (orders, meds, discharge, financial) hard-excluded from voice-approval — approval UI only, per §8 rule 4.
- CDS depth: decision-support remains informational/recommendation-grade (IEC 62304 Class B posture); prescriptive CDS requires regulatory classification review before enable — gate documented, not silently crossed.
- Mass-casualty mode is a clinical-safety feature: drill in test hospital before exit.
- Agent expansion: each new agent passes the unauthorized-action = 0 eval suite before flag-on.

## TESTS
- Orchestration eval: 50 cross-domain scenario questions vs known-blocker ground truth (≥90% correct primary blocker).
- Digital twin: replay 30 days of events → capacity answers reconcile with actuals.
- GL: full month-close simulation, trial balance ties to operational ledger within tolerance.
- Mass-casualty drill + migration studio dry-run on legacy dataset dump.

## EXIT CRITERIA (demo scenario)
Executive asks command bar "why is today's surgical schedule delayed?" → orchestrator queries 6 agents, returns primary blocker (CSSD tray shortage) with owner + recommended action → approved → resolved → capacity engine then answers tonight's admission question with evidence → a full ED mass-casualty drill runs on platform → month-end GL closes → one complete specialty pack (cardiology) installs via configuration alone in <1 day → migration studio imports a legacy HIS export with reconciliation report → benchmarking pack generates an anonymized aggregate.

## ROLLOUT / ROLLBACK
Feature-flagged module-by-module at first hospitals. Rollback per module = flag off; no data migration back.

## OPEN DECISIONS
- Prescriptive CDS regulatory path (CDSCO classification opinion — external counsel, start week 1).
- Indoor mapping vendor/format for wayfinding.
