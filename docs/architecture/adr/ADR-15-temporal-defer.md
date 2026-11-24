# ADR-15 — Temporal adoption deferred (chunk 6 decision)

**Status:** accepted (deferral) · **Date:** 2026-11-24 · **Context:** chunk-06 §9 requires an
evidence-based adopt/defer decision for Temporal (discharge workflow + claim lifecycle).

## Decision
Do **not** adopt Temporal in chunk 6. Keep the discharge state machine and the claim lifecycle as
explicit, audited state machines in `squamed-core`.

## Evidence
- **Discharge (04.01/04.08):** a single checklist-gated state machine
  (`admitted → discharge planned → checklist legs → signed summary → discharged → bed cleaning`)
  with synchronous guards (`DISCHARGE_CHECKLIST_INCOMPLETE`, `DISCHARGE_BILL_UNSETTLED`,
  `DISCHARGE_MEDREC_REQUIRED`). No retry/timeout orchestration exists to externalise.
- **Claims (03.04):** still externally gated on the payer list (D6) and the gateway (E4); there is
  no claim lifecycle to move yet.
- **Billing settlement / cash-up:** deterministic request/response, no long-running waits.
- The platform already has the durable pieces it needs: transactional outbox + Kafka dispatcher,
  per-tenant event ordering, and replay/idempotency (00.09, 06.08).

## Consequences
- Fewer moving parts in the critical clinical path; every transition stays visible in the audit
  chain and the event catalog.
- If chunk 7 introduces multi-day, human-in-the-loop workflows with retries (e.g. insurance
  appeals), revisit this ADR with a concrete workflow and a measured retry/timeout cost.

## Revisit triggers
- A workflow needing durable timers longer than a process lifetime.
- Cross-service compensation/retry loops that cannot be expressed with the outbox.
- More than one team editing the same state machine concurrently.
