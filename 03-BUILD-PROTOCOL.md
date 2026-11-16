# BUILD PROTOCOL — AUTONOMOUS EXECUTION CONTRACT
**Applies to:** every section of every chunk, from first line of code to final report.
**Prime rule:** a section is either COMPLETE (by the checklist below) or IN PROGRESS. There is no third state in code. No stubs, no placeholders, no "will do later", no silent skips.

---

## 1. DEFINITION OF DONE — every section passes ALL gates before the next section starts

| # | Gate | Pass condition | Enforcement |
|---|---|---|---|
| 1 | Functional completeness | Every code path works end-to-end against real data flow (DB/API/events/UI); the section's user journey works live | Demo-script run captured in section report |
| 2 | Zero-stub scan | `grep` gate for `TODO, FIXME, stub, placeholder, mock, "not implemented", "for now"` across changed source — zero hits (allowlist: test fixtures with `// test-fixture` tag, this protocol file) | CI grep step, blocking |
| 3 | Types | Compiler clean, zero `any`/`!` escapes on new code | CI, blocking |
| 4 | Lint + format | Clean, incl. whitelabel string-lint (ktlint for Kotlin, named tooling per language); boundary rules = package-layout convention (**no ArchUnit module exists**) | CI, blocking |
| 5 | Tests | All pass; every new behavior has a test; clinical-safety cases for clinical sections green; coverage = JaCoCo instruction ≥ 0.80 module-wide (broad excludes per `build.gradle.kts`), verified on merged unit+PIT exec | CI, blocking |
| 6 | Accessibility | axe-core: zero critical/serious; keyboard path for every new task; focus visible | CI + manual pass in report |
| 7 | Design system | No hardcoded hex/strings; states complete (hover/focus/active/disabled/loading/empty/error); visual regression screenshots match | CI, blocking |
| 8 | Contracts | OpenAPI/event schemas committed **before** code; generated clients regenerated; schema-registry test green | CI, blocking |
| 9 | Data | Migration applied + rollback note; RLS test (cross-tenant denial) for tenant-scoped tables | CI, blocking |
| 10 | Audit | Every new write path emits audit events (interceptor verified by test) | CI, blocking |
| 11 | Docs | Chunk file + API docs + ledger updated to reflect **what is**, not what was planned | Report check |
| 12 | Exit item | The section's slice of the chunk exit demo runs green in the report | Scripted E2E where possible |

A section that fails any gate is **returned to IN PROGRESS** — never carried forward. "90% done" sections do not advance the build.

## 2. EXTERNAL DEPENDENCIES (the honest exception)

Some items physically cannot be completed autonomously: ABDM sandbox credentials, WhatsApp/DLT approvals, analyzer/monitor interface specs, hardware soak tests, hospital sign-offs. These are **tracked, not skipped**:
- Status `EXTERNAL-BLOCKED` in the ledger with: what, why, workaround shipped (if any), owner, escalation date.
- A section containing an external-blocked item cannot reach COMPLETE — it stops at `SECTION REVIEW` until unblocked. The build may continue in *parallel-free* lanes only if the blocked section is on the critical path's side branch (per chunk dependency rule).
- Workarounds are real implementations behind config (e.g., notification channel ships with email live + SMS code-complete awaiting DLT header) — **never** a stub. The moment the external dependency clears, the workaround is retired and the gate re-run.

## 3. TRACKING — the ledger

`reports/LEDGER.md` is the single running ledger, updated at every section completion:

```text
| Date | Chunk | Section | Status | Gates 1-12 | Tests (pass/total) | Coverage | Blocked | Scope changes | Report link |
```

Statuses: `PLANNED → IN PROGRESS → SECTION REVIEW → COMPLETE` (+ `EXTERNAL-BLOCKED` flag column). Anything not COMPLETE blocks its chunk's exit demo. **Scope changes column** logs any addition/modification with the chunk-file edit reference and approver (risk #4) — an unlogged scope change is a protocol violation.

## 4. REPORTS

**Per section** — `reports/chunk-XX/section-YY.md`, fixed format:
1. Scope (what this section was)
2. Built (files created/changed, with one-line purpose each)
3. Data/API/event contracts delivered
4. Gates 1–12 results (each gate: pass/fail + evidence: command + output summary)
5. Tests: list of new tests incl. clinical-safety cases; pass counts; coverage %
6. Accessibility results (axe summary + keyboard paths)
7. Issues found during build and how each was fixed (root cause, not symptom)
8. External blockers (or "none")
9. Deviations from chunk file (should be zero; if scope changed, chunk file was edited **first**)
10. Exit-demo slice: command to reproduce + last run result

**Per chunk** — `reports/chunk-XX/FINAL.md`: all sections + chunk exit demo evidence (full scenario run), safety-register updates, sign-off block (clinical lead + tech lead per master plan §11).

**Grand final** — `reports/GRAND-FINAL.md`: cumulative across all chunks — every exit demo, cumulative test/coverage/a11y stats, ledger export, risk-register closure status, blueprint §1–117 coverage matrix (section → chunk → status), and the platform's production-readiness statement.

## 5. SEQUENCE DISCIPLINE

1. Chunk N+1 code starts only after chunk N's exit demo is green (parallel review/fixes allowed).
2. Within a chunk: contracts → data → services → API → UI → tests → docs → report, in that order per section.
3. If a gate cannot pass, the build **stops and reports** — it does not weaken the gate, delete the test, or narrow the scope. Gate changes require editing this file first with a written reason.
4. Every autonomous work session ends with either a section report or a precise IN-PROGRESS state note in the ledger (what's done, what's next, exact resume point).

## 6. QUALITY BAR — no AI slop

Per `02-DESIGN-SYSTEM.md`: banned-pattern list is a design-review checklist; visual regression fails on template drift; string-lint bans hardcoded product strings; every screen has an inventory entry before build. Code side: no dead code, no speculative options, no copy-paste blocks > 10 lines without extraction, naming from the clinical domain, comments only for non-obvious *why*.

## 7. GATE INTEGRITY (anti-bypass — the gates themselves are protected)

The gates are the product's quality spine; they are hardened against silent weakening by the build workforce, including the orchestrator:

| # | Mechanism | Effect |
|---|---|---|
| 7.1 | **Gate files are agent-read-only.** CI workflows, stub-ban lists, thresholds, and allowlists live under `.gate/` protected by CODEOWNERS = human only. Any agent edit to `.gate/` (including the orchestrator) is rejected; gate changes happen only via amendment to this file, approved by the product owner | Silent threshold edits impossible |
| 7.2 | **Parity test.** A meta-CI job parses this protocol's gate table (§1) and asserts each gate maps to exactly one enabled CI job with the stated blocking level. A doc gate without a CI job — or a disabled/skipped/`continue-on-error` job — fails the build | Gates can't exist on paper only |
| 7.3 | **Baseline hash.** After scaffold (chunk 0 §00.01), gate configs are hashed into the ledger baseline. Every CI run re-verifies the hash; drift fails unless the run cites a human-approved amendment record | Config drift is loud, not silent |
| 7.4 | **Evidence citation.** Every section report must cite CI run IDs for each gate; the Report-Agent cross-checks citations against actual run results. A report citing non-existent or red runs is itself a protocol violation | No invented evidence |
| 7.5 | **No self-merge.** Branch protection requires the gate workflow from the protected ref; merge authority = orchestrator only after Security/Design verification agents pass; safety-critical sections additionally require the dual independent reviews (§13 §2) | Builders can't wave themselves through |
| 7.6 | **Blind re-verification.** Per chunk exit, 20% of completed sections (random, seeded, logged) are re-verified from scratch by a fresh agent given only the contract — no prior build context. Failures reopen the section and trigger a gate-adequacy review | Catches gates that pass weak code |
| 7.7 | **Weekly gate audit.** DevOps-Agent diffs live CI config against the baseline snapshot, greps for weakened patterns (widened allowlists, lowered coverage, disabled steps, added skips), and posts the diff to the ledger. Any drift without an amendment record = S1 incident per §07.6 | Continuous, not point-in-time |

Amending §7 itself requires product-owner approval **and** a passing full external re-verification of the affected chunk.
