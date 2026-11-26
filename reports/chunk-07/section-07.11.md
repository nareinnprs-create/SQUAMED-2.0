# Section 07.11 — Chunk-7 exit (digital + intelligence layer)

**Verdict: SECTION REVIEW → exit demo 22/22 live; three items owed.** The chunk-7 exit scenario runs end to end on the shipped sections. The cardiology specialty-pack install, the mass-casualty drill harness and the prescriptive-CDS counsel opinion are not built and are reported, not faked.

## 1. Scope
Per `20-CHUNK7-SECTIONS.md` 07.11 (⛔ external: prescriptive-CDS counsel opinion) and `chunk-07-full-depth.md` EXIT CRITERIA: executive question → orchestrator names the primary blocker with owner and action → capacity engine answers tonight's admission question with evidence → ED mass-casualty drill → month-end GL close → one specialty pack installs via configuration alone in < 1 day → migration studio imports a legacy export with a reconciliation report → benchmarking pack generates an anonymized aggregate.

## 2. Exit demo
`scripts/demo-07.11-chunk7-exit.ps1` — 22 checks, all passing against a live app:

| # | Exit-criteria step | Section | Result |
|---|---|---|---|
| 1 | admin + tenant admin login | 00.04 | PASS |
| 2 | orchestrator answers the surgical-delay question | 07.01 | PASS |
| 3 | orchestrator consulted agents and synthesized an answer with an owner | 07.01 | PASS |
| 4 | capacity answer returned with three evidence factors and a readable verdict | 07.03 | PASS |
| 5 | ED mass-casualty activated, listed as active, stood down, none left active | 07.05 | PASS |
| 6 | operations posted to GL; trial balance balances | 07.04 | PASS |
| 7 | month-end close posted; second close rejected 409 | 07.04 | PASS |
| 8 | migration job staged (2 rows), imported both, rows reconciled | 07.10 | PASS |
| 9 | benchmarking participation + peer aggregate summary | 07.10 | PASS |
| 10 | **cardiology pack install via configuration alone** | 07.10 | **OWED** (22-pack clinical review) |
| 11 | **mass-casualty drill harness** | 07.05 | **OWED** |
| 12 | **prescriptive-CDS counsel opinion** | external | **OWED** (gate documented, not crossed) |

## 3. Evidence from the sections
- **Orchestration eval:** `aiOrchestrationCoverage` runs 50 cross-domain questions against ground truth and asserts primary-blocker accuracy ≥ 45/50 (observed 50/50) — the exit criterion's "50-question orchestration eval ≥ 90%" is met in CI, not in the demo.
- **Month-close sim:** covered by `fullFinancialsCoverage` (post-operations, trial balance, close-once) and re-exercised live by the demo.
- **Capacity answer:** `digitalTwinCapacityCoverage` (snapshot 3/2/1, +1 fits, +5 blocked, negative 400, reconcile no drift).
- **Migration + benchmarking:** `migrationBenchmarkEsgCoverage` (validate → import → reconcile → rollback; consent-gated peer medians; ESG intensity).

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS (partial) | exit demo 22/22; three items owed |
| 2 Zero-stub | PASS | demo + docs only; no new runtime code |
| 3 Types | PASS | no code change (existing `compileKotlin`) |
| 4 Lint + format | PASS | no code change (existing `ktlintCheck`) |
| 5 Tests | PASS | prior full chain green (111 PIT + 56 unit, JaCoCo 84.98%) |
| 6 Accessibility | PASS | no UI change |
| 7 Design system | PASS | no UI change |
| 8 Contracts | PASS | no contract change |
| 9 Data | PASS | demo data is tenant-scoped; no schema change |
| 10 Audit | PASS | every demo write is controller-mediated |
| 11 Docs | PASS | This report; `FINAL.md`; tracker; LEDGER |
| 12 Exit item | PASS (partial) | exit demo 22/22; pack install + drill harness owed |

## 5. Section status entering the exit
| Section | Status | What the exit can honestly claim |
|---|---|---|
| 07.01 agents + orchestrator | COMPLETE | 50/50 orchestration accuracy |
| 07.02 voice | COMPLETE | transcript-only, no audio streaming |
| 07.03 digital twin | SECTION REVIEW | capacity answer real; reads operational tables, not 06.06 marts |
| 07.04 full financials | COMPLETE | GL/AP/AR/close; no multi-entity consolidation |
| 07.05 ED + mass casualty | COMPLETE | activation/stand-down real; drill harness owed |
| 07.06 kiosk + wayfinding | SECTION REVIEW | E12 indoor-map vendor import absent |
| 07.07 patient experience | COMPLETE | backend surfaces only |
| 07.08 QMS/IPC/legal | COMPLETE | evidence packs are register records, no bundle export |
| 07.09 lab depth + cardiology data | COMPLETE | cardiology *pack install* is 07.10 scope |
| 07.10 packs + migration + ESG | SECTION REVIEW | migration/benchmark/ESG real; 22 packs await clinical review |

## 6. Deviations / blockers
- **Cardiology specialty pack** — the installer and the 22 reviewed packs are not shipped; clinical review is external.
- **Mass-casualty drill harness** — activation exists; the timed drill harness does not.
- **Prescriptive-CDS counsel opinion** — documented external gate, not crossed.
- **07.03** still reads operational tables rather than 06.06 ClickHouse marts; switching it is a follow-up, not an exit blocker.
- **Chunk-6 carry-over:** 06.09 still owes the 1,500-user load and datacenter-kill drill; 06.07 ABDM remains external.

## 7. Dual review — adversarial pass
1. **No fabricated steps** — the three unbuilt items are printed as OWED in the demo output and named here.
2. **Demo is live** — every PASS came from an HTTP call against the running app, not from fixtures.
3. **Idempotency** — close-once is asserted by a second close returning 409.
4. **Tenant isolation** — the demo provisions its own hospital and admin; no shared state.
5. **Honest accuracy claim** — the 50-question eval is cited from the PIT that runs it, with the observed 50/50 stated.
6. **Exit ≠ chunk closure** — chunk 8 stays blocked until the three owed items and the two SECTION REVIEW features are closed.
