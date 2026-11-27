## 06.09 — the 1,500-user load test, run and reported honestly

Docs + measurement only; no runtime code changed. The chunk-6 exit owed two items: the 1,500-user load test and the datacenter-kill drill. One of them is now done.

### Result (2026-11-27, single laptop, app on :8080, dev compose stack)

| Metric | Value |
|---|---|
| Authenticated users | 1,500 (114 s to log in all of them) |
| Measured ops | 6,000 (3,000 reads, 3,000 writes) |
| Errors | **0** |
| Throughput | 191.4 ops/s |
| Read p50 / p95 / p99 / max | 829 / **1,666** / 2,039 / 2,545 ms |
| Write p50 / p95 / p99 / max | 1,098 / **2,005** / 2,455 / 3,320 ms |

**Correctness holds at 1,500 users; the latency NFR does not.** The targets are p95 < 300 ms reads and < 500 ms writes, so this is a breach of roughly 5.5× (reads) and 4× (writes).

### Why, and what it is not
This is the same root cause 00.17 recorded at 1,000 users: every simulated user shares **one hospital**, so the per-tenant audit hash chain serializes appends. It is a measurement of a single-tenant worst case on laptop hardware, not evidence that the platform cannot serve 1,500 users spread across hospitals. I am not claiming the NFR passes; I am claiming it was not tested in the configuration that would let it pass.

### What is still owed
- **Datacenter-kill DR drill** — needs a second region; unchanged.
- **10k replay drill** — unchanged.
- A staging run with per-hospital fan-out, so the latency figure is quotable.

### Files
- `reports/chunk-06/section-06.09.md` — load row, gate 12, owed list.
- `reports/chunk-06/FINAL.md` — owed table.
- `reports/LEDGER.md` — 06.09 row (still SECTION REVIEW).
