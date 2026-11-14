# Section 00.08 — EMPI

## 1. Scope
Deterministic matcher, probabilistic scorer (threshold ≥0.9), review queue UI, versioned reversible merge/unmerge, 200-case synthetic eval; two-person merge rule (admin + auditor).

## 2. Built (current tree)
- `patient/service/EmpiMatcher.kt` — deterministic identifier matching + probabilistic scoring (similarity ≥0.9 branch at line 182).
- `patient/domain/EmpiScore.kt` + `EmpiScoreRepository.kt` — score persistence.
- `PatientService.mergePatient` — merge flow (moves records, rejects self-merge with `CANNOT_MERGE_PATIENT_WITH_ITSELF`).
- Migration `V7__patient_identity.sql` (empi score table included).

## 3. Contracts delivered
- Match API (by passport/QID/phone/id-number identifier types, PIT-asserted), merge API with self-merge rejection.
- NOT delivered: review queue UI, reversible unmerge, versioned merge audit trail, two-person rule, 200-case synthetic eval.

## 4. Gates
1. FAIL — named contents missing: review queue UI, unmerge, two-person rule, 200-case eval.
2. PASS — CI run 43548016617 (gate 2 green).
3. PASS — CI run 43548016617 (gate 3 green).
4. PASS — CI run 43548016617 (gate 4 green).
5. PASS — CI run 43548016617 (gate 5 green); named tests below.
6. PASS — CI run 43548016617 (gate 6 green) (no EMPI UI exists yet).
7. PASS — CI run 43548016617 (gate 7 green).
8. FAIL — schema-registry harness not yet wired (00.02 scope).
9. FAIL — EMPI tables live in `V7__patient_identity.sql`; tenant-scoped, RLS cross-tenant denial suite ABSENT until 00.04 lands (ADR-13).
10. PASS — merge is a PatientController write intercepted by AuditAspect; audit rows asserted PIT-side.
11. PASS — this report.
12. PASS — PIT `empiAndMergeCoverage` covers match→merge journey (API-level slice).

## 5. Tests
PIT: `empiAndMergeCoverage` (match by passport/QID/phone, self-merge 400, merge moves records, target detail after merge), `empiInputIdentifiersAndLifecycleCoverage` — green, run 43548016617. Coverage ≥0.80 merged enforced.

## 6. Accessibility
N/A EMPI UI (none exists); repo-wide gate 6 green (run 43548016617).

## 7. Issues found during build
Baseline section — see BASELINE-AUDIT.md §2.1 patient/EMPI row. Verified current tree: threshold ≥0.9 present (EmpiMatcher.kt:182); no review-queue/unmerge/two-person wiring added by PRs.

## 8. External blockers
None.

## 9. Deviations from chunk file
Zero; missing contents are unbuilt, not re-scoped.

## 10. Exit-demo slice
PIT `empiAndMergeCoverage` (create two near-duplicate patients → match → merge → records moved) — green in run 43548016617.
