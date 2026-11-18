# Section 01.11 — Analytics mini + shadow-run prep (chunk 1)

**Full build pass** on branch `feat/01.11-analytics-shadow-run` (off merged main `93fe68a`). The section's named gaps from the gate-run report — **no-show rate** (no computed rate, no dashboard metric) and **pilot shadow-run checklist** (no artifact) — are closed with the smallest honest increment: `AppointmentStats.noShowRate` derived inside `AnalyticsService.appointments()` from the byStatus buckets it already computes (**no new query**): `no-show / (completed + no-show)`, rounded to 1 decimal, **null when the denominator is empty** (keeps the badge and the PIT assertion stable at exactly 50.0 for 1/1). The dashboard appointments panel surfaces it as a `no-show {rate}%` warning badge (null → dash). `docs/runbooks/shadow-run-checklist.md` is the pilot shadow-run ops artifact (prep / daily parity checks / exit criteria / rollback / automated coverage pointers). OPD counts and wait times were already delivered (gate-run report §2; `avgWaitMinutes` populated since 01.02's estimate path). Zero new permission codes, zero new events (catalog stays 23 EMITTED), zero migrations. CI green on branch first run (run **44236221707**, 7/7 jobs); PR **#60** squash-merged as **`6f0e0a5`** (no `--admin`); ledger row 70.

## 1. Scope
Per `14-CHUNK1-SECTIONS.md`: OPD counts, wait times, no-show rate; pilot shadow-run checklist. Depends on 01.03, 01.04. The chunk line names one metric — "no-show rate" — without a definition; the denominator choice (terminal outcomes only, excluding booked-but-unresolved) is recorded in §9.

## 2. Built
- `backend/.../analytics/api/AnalyticsDtos.kt` — `AppointmentStats.noShowRate: Double?` added.
- `backend/.../analytics/service/AnalyticsService.kt` — `appointments()` computes the rate from the existing `byStatus` groupBy via a local `statusCount(name)` helper (no new query, no schema change).
- `apps/web/src/app/(app)/dashboard/page.tsx` — `noShowRate: number | null` on `AppointmentsStats`; appointments panel gains `<Badge variant="warning">no-show {rate != null ? `${rate}%` : '-'}</Badge>`.
- `docs/runbooks/shadow-run-checklist.md` — new runbook: prep (accounts, hospital, consent scope), daily parity checks (counts vs source tables, rate vs manual tally), exit criteria, rollback, automated coverage (PIT journey + demo script).
- Contract: `docs/contracts/openapi-v1.json` re-stamped from the running app (`:8080/api-docs`, 326,657 bytes) — **structural diff vs the previous snapshot is exactly one property**: `/components/schemas/AppointmentStats/properties/noShowRate` (number, double); api-client codegen re-run, `schema.d.ts` sha256 `231bc59f…7070`.
- PIT journey `analyticsShadowRunCoverage` (in `SchedulingEngineTests`, reuses class-local helpers — zero duplication).

## 3. Data/API/event contracts delivered
- API: `/api/v1/analytics/appointments` response gains `noShowRate` (nullable double). No other endpoint touched.
- Schema: none (read model; rate computed in service code).
- Events: none. Permissions: none new — analytics reads ride `report:read:report` (only SYSTEM_ADMIN holds it, verified via psql on `identity.role_permission`; no grant API exists, `RoleAdminController` is read-only).

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | Full loop green in PIT `analyticsShadowRunCoverage` + demo 14/14: 2 bookings → completed + no-show → rate reads exactly 50.0 → queue entries populate avg wait → foreign hospital reads zeros/null |
| 2 Zero-stub | PASS | CI run 44236221707 gate green; local grep over changed files clean (`mock` substring ban; `toDouble()` hits are the SC-9-allowlisted "todo"-substring false positives) |
| 3 Types | PASS | Backend compile green; web typecheck green (CI run 44236221707 + local turbo) |
| 4 Lint + format | PASS | `ktlintFormat` before push; web lint green (CI run 44236221707 + local) |
| 5 Tests | PASS | PIT **69/69** incl. `analyticsShadowRunCoverage` (green run 44236221707); web **120/120** (local); merged coverage ≥0.80 enforced (coverage gate green) |
| 6 Accessibility | PASS | axe zero serious/critical; 4 pre-existing moderates on `/` and `/login` unchanged; `/dashboard` remains in SCAN_ROUTES |
| 7 Design system | PASS | VRT **15/15** twice (see §7 — no re-baseline needed: the badge delta is sub-threshold on the full-page dashboard) |
| 8 Contracts | PASS | OpenAPI snapshot re-stamped from the running app; structural diff = exactly 1 property (`AppointmentStats.noShowRate`); `schema.d.ts` stamped sha256 `231bc59f…` |
| 9 Data | PASS (read-only) | No migration; nothing to roll back; reads hospital-scoped via `resolveHospital` (UserRole.hospitalId → principal) since 00.04 |
| 10 Audit | PASS | Read-only endpoints; `AuditAspect` logs reads; no write paths |
| 11 Docs | PASS | This report; LEDGER row 70 COMPLETE; demo script committed (rides this docs PR per 01.09 pattern) |
| 12 Exit item | PASS | Scripted slice **14/14** (`scripts/demo-01.11-analytics-shadow-run.ps1`, §10) |

## 5. Tests
PIT **69/69** (green run 44236221707) incl. `analyticsShadowRunCoverage` (fresh hospital per run): admin mints a RECEPTIONIST (operational writes) and a **hospital-bound SYSTEM_ADMIN reader** (`report:read:report` — verified the only role holding it); template → 2 slots → 2 patients → 2 bookings → completed + no-show; reader asserts total==2, `noShowRate==50.0` exact, byStatus completed=1/no-show=1; two queue entries → queue + overview `avgWaitMinutes` non-null; second fresh hospital + its own reader: total=0, rate null, avgWait null (tenant scoping). Web: 120/120 (no dashboard unit test exists — type+badge change only; covered by the PIT + VRT). Turbo typecheck/lint green.

## 6. Accessibility
`/dashboard` stays axe-scanned: zero serious/critical final scan; the 4 pre-existing moderates on `/` and `/login` are unchanged. The new badge is text, not color alone (`no-show` label + value).

## 7. Issues found during build + fixes
- **First local PIT run FAILED: `data(...) must not be null` NPE at the analytics GET.** The journey originally used RECEPTIONIST for the analytics reads — RECEPTIONIST does not hold `report:read:report`, so the GET 403'd and `data()` NPE'd. Fix: mint a **hospital-bound SYSTEM_ADMIN reader** for reads (binding it to the hospital scopes reads to the tenant); RECEPTIONIST keeps the operational writes. psql check on `identity.role_permission` confirmed SYSTEM_ADMIN is the only holder. Green after the swap (2m54s local).
- **bootRun failed twice during the cycle (attempts 3–4): PostgreSQL :5433 refused mid-boot** — Docker engine flapping again (9th infra incident this build cycle). Recovered per runbook (Docker Desktop restart → `docker info` stable → compose start → `pg_isready` → :9200 → bootRun → health UP; backfill line confirmed readiness). A final bootRun daemon death occurred **after** the demo run completed — app no longer needed, not chased.
- **VRT expectation vs reality: no re-baseline happened — and none was needed.** The plan expected dashboard.png to fail and get re-baselined (01.05 pattern). Run 1 passed 15/15 including /dashboard: the added badge changes ~0.06% of the full-page dashboard pixels, far below the 2% `maxDiffPixelRatio` threshold. A targeted `--update-snapshots` run on /dashboard confirmed it — the comparison matched within tolerance and **rewrote nothing** (git tree unchanged). Committed baseline stays as-is.
- **Demo script: first live run 14/14 PASS** — no script bugs surfaced (written from the PIT journey; slot-filter-by-templateId pattern carried over from 01.09).

## 8. External blockers
None. 01.10 stays externally blocked (`exp:E2,E5` — BSP brand contract + eSign contract); its ledger row is untouched. The shadow-run checklist is prep tooling; the actual pilot run is a later-chunk operational activity.

## 9. Deviations from chunk file
- **No-show rate denominator = terminal outcomes only** (`no-show / (completed + no-show)`, excluding booked-but-unresolved appointments). The chunk file names the metric without a definition; this is the operational definition a shadow run can verify against a manual tally, and it goes null (badge shows a dash) until at least one appointment resolves. Recorded per the no-silent-deviation rule.
- **Shadow-run checklist rides the FEATURE PR, not the docs PR.** The 01.09 docs-PR pattern covers `reports/` + `scripts/`; runbook files historically land in feature commits (docs/runbooks/oncall.md came in feature PRs #28/#30). The checklist is a section deliverable ("pilot shadow-run checklist"), so it traveled with the code.
- **Demo script + this report ride the docs PR** — untracked when the feature PR merged (same pattern as 01.05–01.09).
- **Local full coverage-chain re-run skipped** — the chain is cited from CI's Integration gates job (identical command, green on the branch head), per 01.06–01.09 precedent.

## 10. Exit-demo slice
`pwsh scripts/demo-01.11-analytics-shadow-run.ps1` against the app on :8080 (compose dev stack up): admin login → fresh hospital → receptionist + hospital-bound SYSTEM_ADMIN reader → doctor template for the demo day → exactly two 30-minute slots → 2 patients → 2 bookings (201) → completed + no-show → appointments analytics: total=2, `noShowRate` exactly 50.0, byStatus completed=1/no-show=1 → two queue entries → queue + overview avg wait non-null → second fresh hospital + own reader: total=0, rate null, avgWait null. **14 passed, 0 failed**, live 2026-11-18 (first run).

**Merge record**: PR **#60** squash-merged as `6f0e0a5` after green CI (run 44236221707, 7/7 jobs, no `--admin`), 2026-11-18; prior main was `93fe68a`. First CI run green — no CI failures this section; the PIT reader-role failure was local-only, fixed pre-push.
