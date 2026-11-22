# Section 03.05 — HIM coding + revenue integrity engine (chunk 3)

**Gate-run assessment of the CURRENT tree** @ PR #87 (chunks 0–2 + 03.01-03.03 merged + this PR). Verdict: **COMPLETE → GATE-12 PASS (partial: chunk-3 exit demo slice owed at 03.07)**. CI evidence: **run 44976991927 — all 7 gates green**. Named PIT `himCodingAndIntegrityCoverage` green in the same run.

## 1. Scope
Per `chunk-03-revenue-cycle.md` §5: ICD-10 coding worklist on completed encounters, coder queries to clinicians (task engine), coding audit sampling. §6: revenue integrity rules on the event ledger vs bills (missing charges, duplicates, docs-gaps), daily digest + task creation.

## 2. Key artifacts
**Migration `V75__him_coding_integrity.sql`** (additive; rollback note inside):
- `him.coding_assignment` — one per encounter (unique), ICD/SNOMED CSV, coder + timestamp.
- `him.coder_query` — encounter, question, status `OPEN|ANSWERED|CLOSED`, task link, answer.
- `integrity.finding` — rule, severity, subject, detail, status `OPEN|RESOLVED|IGNORED`, **unique `dedupe_key`**, resolver/resolution.
- `MEDICAL_CODER` role (him read/code/query/answer/audit); DOCTOR `him:answer:query`; BILLING_SUPERVISOR + SYSTEM_ADMIN `integrity:scan/triage`.

**Behaviour:**
- Coding queue (completed encounters, `assigned` + diagnosis count), assignment (completed-only, non-empty ICD, one per encounter, re-code 400), audit sample by percent.
- Coder queries open a task (`relatedType=ENCOUNTER`) and an OPEN query; the clinician answers (`him:answer:query`), double answer rejected.
- Integrity scan (`unbilledHours` operator-tunable, default 24): `UNBILLED_CHARGE` (high), `DUPLICATE_CHARGE` (identical patient+code+qty+price on a day, high), `UNCODED_ENCOUNTER` (warning). Each new finding opens a task; dedupe keys make daily scans idempotent.
- Triage `RESOLVED|IGNORED` with a mandatory note + resolver/timestamp; double triage rejected.
- OpenAPI snapshot regenerated (`d3df19b6…`), codegen stamped.

## 3. Gap closure
- ✔ Coding worklist + assignments + audit sampling
- ✔ Coder queries via the task engine
- ✔ Integrity rules (unbilled, duplicate, uncoded) with dedupe + task creation
- ✔ Triage with audit
- ✔ PIT 03.05: `himCodingAndIntegrityCoverage` PASS

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | PIT covers queue/assign/re-code/audit, query lifecycle, all three rules, dedupe, triage |
| 2 Zero-stub | PASS | CI branch run 44976991927 |
| 3 Types | PASS | CI branch run 44976991927 |
| 4 Lint + format | PASS | CI branch run 44976991927 |
| 5 Tests | PASS | CI branch run 44976991927; named PIT below |
| 6 Accessibility | PASS | CI branch run 44976991927 (web untouched) |
| 7 Design system | PASS | CI branch run 44976991927 |
| 8 Contracts | PASS | Snapshot + codegen committed; parity green |
| 9 Data | PASS | V75 additive + rollback note; unique encounter assignment + unique finding dedupe |
| 10 Audit | PASS | Coder/resolver recorded; coder queries keep the answerer; tasks link to subjects |
| 11 Docs | PASS | This report; LEDGER row 03.05 COMPLETE; tracker updated |
| 12 Exit item | PASS (partial) | Integrity/coding slice of the 03.07 exit demo |

## 5. Tests
Named PIT **`himCodingAndIntegrityCoverage`** (new): two completed encounters queued with diagnosis counts; ICD `R05`+`J18.9` + SNOMED assigned (coder recorded), re-code 400, queue flips assigned, 100% audit sample covers it; coder query → task linked to the encounter, doctor answer → ANSWERED, double answer 400; scan `unbilledHours=0` → ≥3 unbilled, ≥1 duplicate pair, ≥1 uncoded, re-scan 0 new; triage blank note 400, resolve with note → RESOLVED + resolver, double triage 400; finding task exists.

Regressions green: `taskEngineCoverage`, `billingEngineCoverage`, `financialAndOperationsCoverage`, `fullPatientRevenueJourney`.

## 6. Accessibility
Web surface untouched; repo-wide axe gate in CI.

## 7. Dual review — adversarial pass
1. **Dedupe** is the core safety property: the unique `dedupe_key` (rule + subject [+ day]) makes daily scans idempotent; a resolved finding is never re-raised (status kept).
2. **Unbilled rule** is operator-tunable (`unbilledHours`); default 24h avoids flagging same-day counters.
3. **Duplicate rule** keys on patient+code+qty+price+day — two clinically valid same-code charges on one day would be flagged; triage with a note is the escape hatch.
4. **Uncoded rule** fires on completed encounters only; draft/active encounters are out of scope.
5. **Coding assignment** is immutable once set (re-code is a future audited workflow) — a wrong code needs a migration path, noted.
6. **Task creation** per finding can be noisy on a first scan of historical data — the dedupe means once per finding; bulk-close is triage.
7. **Cross-test interference:** the PIT scans the shared seed hospital, so historical rows from earlier tests may add findings; the PIT asserts ≥ thresholds and dedupe stability rather than exact counts.

## 9. Deviations from contract
- Coding inconsistency (code vs note findings mismatch) needs NLP/clinical rules — not built.
- One assignment per encounter (no coding amendments/audit chain yet).
- The daily digest is the findings list + tasks; scheduling reuses the on-demand scan endpoint.

## 8. Session trail
- First PIT run failed only on the last assert: `/tasks/subject` resolves through task *links*, while findings tasks carry `relatedType/relatedId` — switched the assert to the task list.
- Docker Desktop died twice; compose restarted, postgres healthy.
