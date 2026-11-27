## 06.09 — correction: the load latency was host-dominated, not an audit-chain breach

I published the 1,500-user load numbers in PR #161 and attributed the latency to the per-tenant audit hash chain. **That attribution was wrong, and this corrects it.**

### The control that falsified it
Same script, same machine, minutes later, **200 users instead of 1,500**:

| Run | Users | Ops | Errors | Read p95 | Write p95 |
|---|---|---|---|---|---|
| 1,500 | 1,500 | 6,000 | 0 | 1,666 ms | 2,005 ms |
| **200** | 200 | 800 | 0 | **3,691 ms** | **4,474 ms** |

Fewer users measured *worse*. A serialization bottleneck would scale with load; this does not. The dominant factor is the single host running the app, Postgres, Kafka, OpenSearch, Docker and the load client at once — the 200-user run's own setup phase took 64 s versus 19 s, which is the tell.

### What is actually established
- **Correctness at 1,500 concurrent authenticated users: 0 errors across 6,000 operations.** That claim stands.
- **The latency NFR is unverified**, not breached by design. These numbers cannot be used to argue the platform fails the 300/500 ms targets, and they cannot be used to argue it passes them.
- The "audit-chain serialization" explanation is **withdrawn** as unsupported. 00.17's finding was measured under different conditions and is not transferable by inference.

### What would settle it
A staging run with the load client off-box and users fanned out across hospitals. Until then the NFR stays unverified and 06.09 stays SECTION REVIEW (DR drill and 10k replay drill are also still owed).

### Files
- `reports/chunk-06/section-06.09.md` — load row and owed list corrected.
- `reports/chunk-06/FINAL.md` — owed table corrected.
- `reports/LEDGER.md` — 06.09 row and the #161 merge-log row carry the correction.
