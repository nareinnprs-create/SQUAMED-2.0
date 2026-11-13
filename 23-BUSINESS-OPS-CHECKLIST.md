# BUSINESS & EXTERNAL-TRACK CHECKLIST (non-engineering work)
**What this fixes:** "business plan — yours to commission" is no longer an open skip. Everything non-engineering the build needs is scheduled here with owner + needed-by date. I (the agent workforce) cannot execute these; the checklist exists so none of them is forgotten and the weekly status report tracks aging (risk #13). **No invented numbers:** pricing/market items are decision memos with their required inputs listed, not fabricated figures.

## 1. Decisions & legal work (owner: you + counsel/finance)

| # | Item | Needed by | Inputs ready | Status |
|---|---|---|---|---|
| B1 | Legal entity + registrations (Pvt Ltd, GSTIN per entity, bank, tax) | Before any contract signing (chunk 0) | `12` compliance map | OPEN |
| B2 | Clinical lead hired + named backup advisors per specialty | Before chunk 0 kickoff | `13` §6, `11` §1 | OPEN |
| B3 | Pilot hospital signed (from 3-candidate pipeline) | Before chunk 1 exit (risk #5 gate) | `00` §12 risk 5 | OPEN |
| B4 | **Pricing decision** (per-bed/per-module/implementation fees; AI pass-through model) | Draft by chunk 1 exit · final before chunk 2 exit (sales starts at pilot-ready) | `00` §10 unit economics · `09` §7 infra cost · `13` §8 build cost | OPEN |
| B5 | Contract library: pilot MSA, DPA (DPDP), SLA, data-processing addendum for AI providers, reseller/whitelabel agreement (Tier 2) | Counsel by chunk 1; reseller terms by chunk 6 (Tier 2 goes live there) | `12`, master plan §14 | OPEN |
| B6 | Insurance: professional indemnity + cyber liability | Before first go-live (chunk 1 exit) | `07` §6 IR runbook | OPEN |
| B7 | Reseller program economics (rev-share %, listing fees for marketplace) | Chunk 8 kickoff (already flagged) | `chunk-08` OPEN DECISIONS | OPEN |
| B8 | Market #2 selection | Chunk 8 kickoff | `chunk-08` §5 | OPEN |

## 2. Consolidated external-dependency register (owner: you; agents log EXTERNAL-BLOCKED against these)

| # | Dependency | Requested by | Blocks | Escalation rule |
|---|---|---|---|---|
| E1 | ABDM sandbox credentials (2–4 wk lead) | Chunk 0 week 1 | 00.07 ABHA flows, `10` §1 | Aging >10 working days → escalate to ABDM support + me |
| E2 | DLT header + WhatsApp BSP/WABA approval | Chunk 0 week 1 | 00.12 channels; 01.10 per-tenant sender identity | Aging >3 wk → swap BSP candidate |
| E3 | SMS/email provider account | Chunk 0 week 1 | 00.12 | trivial, low risk |
| E4 | Payment gateway / UPI PSP | Chunk 3 week 1 | 03.06 | Aging >2 wk → alternate PSP |
| E5 | eSign service contract (per hospital entity) | Chunk 1 | 01.04 note signature, `12` evidence | Aging >2 wk → PIN-fallback audit path only (documented) |
| E6 | Analyzer interface specs (pilot lab) | Chunk 2 week 1 | 02.01 Mirth channels | Biggest chunk-2 risk; request at pilot signing |
| E7 | Monitor/gateway interface specs | Chunk 4 week 1 | 04.04 | Biggest chunk-4 lead-time risk; request at pilot signing |
| E8 | STT + LLM provider contracts (India-region / zero-retention, + custom/self-hosted per §8 rule 8) | Chunk 5 week 1 | 05.05 scribe; sovereign mode | Procurement = chunk-5 critical path |
| E9 | Video SDK (India latency + DPDP terms) | Chunk 6 week 1 | 06.02 | Aging >2 wk → WebRTC self-host fallback eval |
| E10 | Hardware lab: Android TV box, thermal printer, kiosk tablet, barcode scanner | Chunk 1 kickoff (risk #10) | 01.06/01.08 soak tests | Purchase, don't pilot-borrow |
| E11 | Prescriptive-CDS regulatory opinion (counsel) | Chunk 7 week 1 | 07.10 CDS gate | Gate stays documented-closed without opinion |
| E12 | Indoor mapping vendor/format | Chunk 7 week 1 | 07.06 | Fallback: text + static-floor routing |

## 3. Operating cadence
- Weekly status report to you lists every OPEN row with age in working days (risk #13: any human-gate aging > 5 days is flagged, decisions batched at chunk kickoffs/exits).
- A decision is CLOSED only with the artifact linked (signed doc, account URL, purchase receipt) — words don't close rows.
- I update Status and never invent dates: needed-by dates here are the plan's commitments; slippage re-opens the affected wave's estimate via protocol §5.2.
