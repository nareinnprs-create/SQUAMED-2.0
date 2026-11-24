# DR drill — regional failover (chunk-6 resilience)

Targets: **RTO 4 h**, **RPO 15 min**. The drill is a scheduled deployment activity; this runbook
defines the procedure and the evidence pack. The drill itself is owed until staging has a second
region (external infrastructure).

## 1. Preconditions
- Secondary region provisioned with the same images; Postgres streaming replica + object storage
  replication + Kafka mirroring (or the documented equivalent) verified the day before.
- The degraded-mode flag (`POST /api/v1/resilience/degraded/activate`) and the store-and-forward
  queue are known to the ward leads; the drill announces a freeze window.
- Baseline captured: patient/encounter counts, last event id per topic, last audit chain hash.

## 2. Drill steps (timed)
1. **T0** — stop writes in the primary (maintenance flag); record the last committed event id and
   the audit chain head.
2. Promote the replica / flip the regional endpoint; start the app in the secondary.
3. Run the smoke pack: login, patient read, admission read, bed board, eMAR read, lab result read,
   one queued clinical append through the replay endpoint.
4. **T1** — first successful clinical read/write in the secondary; RTO = T1 − T0.
5. Measure RPO: compare the last event id / audit head with the primary's final state; the delta
   must be ≤ 15 min of events.
6. Replay the degraded-mode queue; assert zero loss (every idempotency key present once) and zero
   duplicates (ledger unique).
7. Fail back or hold in the secondary per the incident commander.

## 3. Evidence pack (retained for hospital IT audits)
- T0/T1 timestamps, computed RTO.
- Event-id/audit-head delta, computed RPO.
- Replay result JSON (`queued`, `replayed`, `duplicates`, `rejected`, `ledger`).
- Smoke-pack output and the incident timeline.

## 4. Abort / rollback
- Any data divergence → abort, return to the primary, keep the secondary read-only, and file an
  incident with the evidence pack.
- Degraded mode stays on until the primary is verified; identity/financial writes remain blocked
  throughout.

## 5. Cadence
Quarterly, first drill before the first production go-live. Each drill updates this runbook with
the measured RTO/RPO and any configuration drift.
