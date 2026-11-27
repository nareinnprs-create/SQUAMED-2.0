## 06.09 — 10k store-and-forward replay drill

The second of chunk-6's two owed drills, run for real: 10,000 writes queued during degraded mode, then replayed.

### Result (2026-11-28, single laptop, dev stack)

| Step | Result |
|---|---|
| Queue 10,000 writes in degraded mode | 10,000/10,000, **0 failures**, 79 s |
| Replay | **10,000 replayed, 0 failed**, 944 s |
| Second replay (idempotency) | **0 replayed** |
| Pending after replay | **0** |

**No loss, no duplication.** `scripts/drill-06.09-10k-replay.mjs` fails loudly if any of those four numbers is wrong.

### The finding
Replay throughput is **~10.6 writes/s**, so a 10k backlog takes ~16 minutes to drain. That is tolerable for a short degraded window and too slow for a multi-day outage; the obvious fix is batching the replay write path rather than replaying one queued row at a time. Recorded as a measured limit, not fixed here.

### Script detail worth knowing
The first run of the drill appeared to fail its idempotency check because the 10k replay outlived the access token (401 on the follow-up calls). The script now re-authenticates before the post-replay checks, so a long replay cannot masquerade as a duplication failure.

### Chunk-6 owed list after this
- **Datacenter-kill DR drill** — still owed (needs a second region).
- Everything else in chunk 6 is either done or externally gated (06.07 ABDM).
