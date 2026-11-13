# GATE REGISTRY — authoritative sync target for `.github/workflows/ci.yml`

Enforcement source: `03-BUILD-PROTOCOL.md` §1. `scripts/gate-parity.mjs` (CI job `gate-parity`) verifies every row below maps to ≥1 existing CI job id and every CI job maps to ≥1 gate. CI-job columns list job ids only.

| Gate # | Name | Pass condition (03 §1) | CI job |
|---|---|---|---|
| 1 | Functional completeness | Every code path works end-to-end against real data flow; the section's user journey works live | integration-gates |
| 2 | Zero-stub scan | grep over backend/, apps/, packages/, services/, scripts/ for `TODO, FIXME, stub, placeholder, mock, "not implemented", "for now"` — zero hits (allowlist: lines tagged `// test-fixture` / `# test-fixture`, .md excluded) | stub-grep |
| 3 | Types | Compiler clean, zero `any`/`!` escapes on new code (Kotlin warnings-as-errors / TS strict) | backend-gates, frontend-gates |
| 4 | Lint + format | Clean lint/format incl. whitelabel string-lint and ArchUnit/boundary rules | backend-gates, frontend-gates |
| 5 | Tests | All pass; every new behavior tested; clinical-safety cases green; coverage ≥ 80% (JaCoCo instruction) | backend-gates, frontend-gates |
| 6 | Accessibility | axe-core: zero critical/serious; keyboard path per new task; focus visible | a11y-perf-gates |
| 7 | Design system | No hardcoded hex/strings; full state coverage; visual-regression screenshots match (Lighthouse budget runs in same job) | a11y-perf-gates |
| 8 | Contracts | OpenAPI/event schemas committed before code; generated clients regenerated; schema-registry tests green | backend-gates, frontend-gates |
| 9 | Data | Migration applied + rollback note; RLS cross-tenant denial test for tenant-scoped tables | backend-gates |
| 10 | Audit | Every new write path emits audit events (interceptor verified by named test) | backend-gates |
| 11 | Docs | Chunk file + API docs + ledger reflect what is; gate docs stay synced to CI (this file vs ci.yml) | gate-parity |
| 12 | Exit item | The section's slice of the chunk exit demo runs green in the report (scripted E2E where possible) | integration-gates |

## Gate-8/9/10 note

Contracts, RLS and audit-interceptor enforcement live as **named tests inside the Gradle suite** (`backend/squamed-core/architecture-test/` + contract/data test packages per `01-ENGINEERING-STANDARDS.md` §3–4), so `backend-gates` runs them with `test`. Post-merge, section 00.02 wires the schema-registry harness and section 00.04 the cross-tenant denial suite; until then the backend-gates job still blocks on whatever the merged suite contains.

## Integrity rules (BUILD-PROTOCOL §7)

- **Agent-read-only:** `.gate/`, CI workflows, stub-ban lists, thresholds and allowlists are protected paths (CODEOWNERS = human only). Any agent edit is rejected; changes happen only via protocol §7 amendment approved by the product owner.
- **Baseline hash:** after section 00.01 scaffold, gate configs are hashed into `reports/LEDGER.md`; every CI run re-verifies the hash; drift fails unless the run cites a human-approved amendment record.
- **Parity test:** `gate-parity` fails the build when this registry and `ci.yml` drift (§7.2). A gate without a CI job, or a disabled/skipped/`continue-on-error` blocking job, is a parity violation.
- **20% blind re-verification:** per chunk exit, 20% of completed sections (random, seeded, logged) are re-verified from scratch by a fresh agent given only the contract; failures reopen the section and trigger a gate-adequacy review (§7.6).
- **Weekly gate audit:** DevOps-Agent diffs live CI config against the baseline snapshot, greps for weakened patterns (widened allowlists, lowered coverage, disabled steps, added skips), posts the diff to the ledger; drift without amendment = S1 incident (§7.7).
