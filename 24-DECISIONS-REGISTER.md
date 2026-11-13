# DECISIONS REGISTER — every open decision in one place
**Rule:** a decision exists here or it doesn't exist. Each has an ID, a needed-by date, and closes only with an artifact link (signed doc, account, purchase, ADR). Business decisions B1–B8 and external dependencies E1–E12 live in `23-BUSINESS-OPS-CHECKLIST.md`; this file tracks the *technical* decisions (many ride on those externals). Blueprint-review findings are folded into the plan; any NEW finding gets an F-ID row in the ledger's scope-change log, never chat-only.

## Open technical decisions
| ID | Decision | Source | Needed by | Rides on | Status |
|---|---|---|---|---|---|
| D1 | WhatsApp BSP selection (pricing) | chunk-00 OPEN | Before 00.12 exit demo | E2 | OPEN |
| D2 | Regional language #1 (pilot state) | chunk-01 OPEN | Before 01.07 | B3 pilot hospital | OPEN |
| D3 | Thermal printer model + QR queue card vs printed slip | chunk-01 OPEN | Before 01.08 | E10 purchase | OPEN |
| D4 | Analyzer vendor list → Mirth channel configs | chunk-02 OPEN | Chunk 2 wk 1 | E6, B3 | OPEN |
| D5 | OHIF version pin + Orthanc storage sizing | chunk-02 OPEN | Before 02.05 | `09` §2 | OPEN |
| D6 | Pilot payer/TPA list (adapter effort = chunk-3 schedule risk) | chunk-03 OPEN | Chunk 3 wk 1 | E4 | OPEN |
| D7 | Monitor vendor/gateway models | chunk-04 OPEN | Chunk 4 wk 1 | E7 | OPEN |
| D8 | Blood-bank regulatory reporting format (state authority) | chunk-04 OPEN | Chunk 4 wk 1 | B3 | OPEN |
| D9 | Consult-room recording hardware/mic standard | chunk-05 OPEN | Chunk 5 wk 1 | E8 | OPEN |
| D10 | Temporal adopt/defer (evidence-based) | chunk-06 OPEN | Chunk 6 wk 8 | chunks 3–4 state machines | OPEN |
| D11 | Patient app: React Native vs PWA | chunk-06 OPEN | Chunk 6 wk 2 | portal usage data | OPEN |
| D12 | Video SDK provider (India latency + DPDP terms) | chunk-06 OPEN | Chunk 6 wk 1 | E9 | OPEN |
| D13 | Indoor mapping vendor/format | chunk-07 OPEN | Chunk 7 wk 1 | E12 | OPEN |
| D14 | Market #2 selection | chunk-08 OPEN | Chunk 8 kickoff | B8 | OPEN |
| D15 | Marketplace commercial model (rev-share %, listing fees) | chunk-08 OPEN | Chunk 8 wk 1 | B7 | OPEN |
| D16 | Identity: planned Keycloak vs adopted build's own JWT+TOTP auth module | SC-6 adoption | Chunk 0 wk 1 (00.03) | Master baseline audit | OPEN |
| D17 | FHIR: planned HAPI FHIR buy vs adopted build's hand-rolled `FhirService` (403 lines) | SC-6 adoption | Chunk 0 wk 1 (00.07 ABHA rides on it) | Master baseline audit, E1 | OPEN |
| D18 | Tenancy: planned Postgres RLS vs adopted build's `hospital_id` column scoping | SC-6 adoption | Chunk 0 wk 1 (00.04) | Master baseline audit | OPEN |
| D19 | Interop engine: planned Mirth Connect vs adopted build's built-in HL7 v2 module | SC-6 adoption | Chunk 2 wk 1 (02.01) | Master baseline audit, E6 | OPEN |

## Already-decided (for the record — don't re-open without protocol §5.2)
| ID | Decision | Where |
|---|---|---|
| ADR-1…10 | Stack, tenancy, identity, outbox, buys, EMPI, audit, deployment | `00-MASTER-PLAN.md` §10 |
| S-1 | Full ED module → chunk 6 (not 4) | chunk-04/06 |
| S-2 | Full GL → chunk 7; chunk 3 = GL journal export only | chunk-03 §8 |
| S-3 | PACS/viewer = Orthanc + OHIF (never build DICOM engines) | chunk-02 §3 |
| S-4 | Product agents: 3 at chunk 5 + 6 at chunk 7 = 9 total | chunk-05/07 |
| S-5 | WORM audit export = S3 Object Lock (not R2) | `09` §4 |
| S-6 | Micro/AST, histo slides, NAT, food service, AI report drafting, Web Push — bound (SC-1…5) | ledger scope log |
| S-7 | Payroll never built (external export) | chunk-06 §4 |

## Cadence
- Weekly report lists every OPEN row + age; decisions batched for product-owner review at chunk kickoffs (risk #13).
- A decision closing changes: Status → DECIDED + artifact link; if it alters scope, ledger scope-change row first (§5.2).
