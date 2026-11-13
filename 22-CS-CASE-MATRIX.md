# CS-CASE → SECTION BINDING MATRIX
**What this fixes:** every clinical-safety case (catalog: `08-TEST-STRATEGY.md` §3) is bound to the section(s) that must implement and test it — the QA-Agent's dispatch packet for a section always includes its bound CS cases, so "which tests apply" is never derived at dispatch time.

## Binding rules
1. **Mandatory scope:** a section cannot pass gate 8 (clinical safety) without green runs of every CS case listing it.
2. **Regression rule:** a bound case runs at its first evidence exit **and every subsequent chunk exit** — the exits column marks first evidence, not last run.
3. **New cases:** appended to `08` §3 catalog + this matrix **before** implementation (protocol §5).
4. **Scope boundary:** money/authz test sets (tax table, claim golden files, consent-gate adversarial suite) are NOT CS cases — they stay owned by their chunk files' test sections.

| CS | Case | Build sections (own the test) | First exit evidence |
|---|---|---|---|
| CS-01 | Wrong-patient write → two-identifier block + audit | 01.04 · 02.06 · 04.07 · 07.02 | 01.12 |
| CS-02 | Allergy BLOCK — no override path | 01.04 (eRx) · 02.07 (dispense) | 01.12 |
| CS-03 | Allergy WARN — typed reason + KPI | 01.04 · 02.07 | 01.12 |
| CS-04 | High-alert med — second authorized user | 02.06 (pharmacist) · 04.02 (nurse) | 02.09 |
| CS-05 | Critical result SLA escalation chain | 02.03 · 02.05 (rad findings) · 04.05 (ICU routing) · consumer 05.04 (rail) | 02.09 |
| CS-06 | eMAR barcode mismatch → hard block | 04.02 | 04.09 |
| CS-07 | Merge error → byte-identical reversal | 00.08 · 06.01 (group EMPI) | 00.17 |
| CS-08 | Offline replay exactly-once (chaos sim) | 00.13 · 06.08 | 00.17 (sim live from 00.13) |
| CS-09 | Downtime paper mode + re-entry | 01.09 · 06.08 (degraded mode) | 01.12 |
| CS-10 | Blood bedside wristband mismatch = BLOCK | 04.07 | 04.09 |
| CS-11 | Discharge blocked without med-rec | 04.08 | 04.09 |
| CS-12 | Unsigned note → bill line / claim rejected | 01.04 (signature) · 03.04 (claim reject) | 01.12 |
| CS-13 | Mass-casualty mode + drill | 07.05 | 07.11 |
| CS-14 | No AI write path to signed records (arch test) | 05.01 (arch test + gateway) · 05.05 (draft→sign) · 07.01 (propose-only) · 08.02 (SDK enforcement) | 05.08 |
| CS-15 | Audit chain tamper detected + pages | 00.10 · 08.04 (on-prem WORM survives) | 00.17 |

**Coverage check:** all 15 cases bound; no CS case without an owning section; each binding verified against `14–21` section IDs.
**Machine-readable export:** `22-CS-CASE-MATRIX.json` — a chunk-0 CI job validates JSON ↔ table parity (rule added to its `ci_parity` field); drift fails the build.

## Dispatch wiring
- Orchestrator dispatch packet per section = chunk file + section row + standards files + prior report **+ this matrix filtered to the section** (`13` §2).
- Report-Agent cross-checks: a section report claiming gate 8 green without citations for its bound CS cases = protocol violation (§7.4).
- Exit sections re-run the chunk's full bound set + all previously bound cases (regression rule 2).
