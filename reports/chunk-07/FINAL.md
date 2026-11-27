# Chunk 7 — Digital + intelligence layer: FINAL

**Status: exit demo green (22/22); chunk NOT closed.** Three items are owed and three sections remain SECTION REVIEW. Chunk 8 is not started.

## What shipped

| Section | Status | Delivered |
|---|---|---|
| 07.01 | COMPLETE | Six new agents + orchestrator; 50/50 orchestration accuracy; per-agent eval records `ai.eval_run` |
| 07.02 | COMPLETE | Voice operating interface (transcript-only), deterministic en+hi intents, escalation log |
| 07.03 | COMPLETE | Digital twin snapshots, 5-factor capacity answer, 30-day event replay, staffing + advisory forecast |
| 07.04 | COMPLETE | GL/AP/AR, cost centres, budgets, bank recon, depreciation, GST, month close |
| 07.05 | COMPLETE | ED scales/bays/trauma/mass-casualty activation, ED dashboard |
| 07.06 | SECTION REVIEW | Full kiosk wayfinding (campus/map nodes/edges), payments recorded; E12 vendor import absent |
| 07.07 | COMPLETE | Allied health + patient experience (NPS, follow-ups, interpreter desk) |
| 07.08 | COMPLETE | QMS, IPC, legal/risk registers, evidence packs |
| 07.09 | COMPLETE | Lab depth (antibiogram, NAT, diet orders) + cardiology data surfaces |
| 07.10 | SECTION REVIEW | Migration studio (validate/import/reconcile/rollback), benchmarking, ESG; 22 packs not shipped |
| 07.11 | SECTION REVIEW | This exit: demo 22/22, FINAL, ledger |

## Exit demo
`scripts/demo-07.11-chunk7-exit.ps1` → **22 passed, 0 failed**, plus three explicit OWED lines.

## Owed before the chunk can close
1. **Cardiology specialty pack install via configuration alone** — 22 packs await human clinical review (07.10).
2. **Mass-casualty drill harness** — activation exists, timed drill does not (07.05).
3. **Prescriptive-CDS counsel opinion** — external gate, documented but not crossed (07.11).

## Known limits worth carrying forward
- 07.03 reads operational tables by design (capacity must be live, not nightly); its forecast is a flat 7-day run-rate and staffing counts rostered shifts, not attendance.
- 07.06 has no E12 indoor-map vendor import; the graph is admin-defined.
- 07.08 evidence packs are register records + summary, no bundle export; hygiene is aggregate, not per-observation.
- Chunk-6 carry-over: 06.07 ABDM is external (no sandbox credentials); 06.09 owes the 1,500-user load and datacenter-kill drill.

## Verification
- Full backend chain: `integrationTest test jacocoTestReport jacocoTestCoverageVerification` — BUILD SUCCESSFUL, 111 PIT methods + 56 unit tests, 0 failures, JaCoCo instruction 84.98%.
- Exit demo: 22/22 against a live app on :8080 with the dev compose stack (postgres, redis, kafka, opensearch, minio, clickhouse, hapi).
