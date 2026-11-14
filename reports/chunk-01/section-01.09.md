# Section 01.09 — Emergency fast-path + downtime pack v1 (chunk 1)

**Gate-run assessment of the CURRENT tree** @ 886c51c (merged baseline + wiring PRs #2–#11). Static read-only; no builds/tests run. Verdict after verification: **PARTIAL** → **IN PROGRESS** (named contents missing). CI evidence: run 43548016617 — all 6 gates green.

## 1. Scope
Per `14-CHUNK1-SECTIONS.md`: triage-lite tags boost queue priority; printed reception/queue fallback + token/check-in re-entry tool. Depends on 01.03.

## 2. Built (current tree)
- `backend/squamed-core/src/main/kotlin/com/squamed/core/emergency/domain/EmergencyEntities.kt` — EmergencyRegistration (hospital_id, patient, arrival mode walk_in/ambulance/transfer, arrival time, presenting complaint, triage ESI, priority, status registered/triaged/in_treatment/admitted/discharged, admittedAdmissionId), TriageAssessment (ESI 1–5, category, pain scale, vital triggers, acuity notes), TraumaWorkflow (primary/secondary/tertiary/completed ABCDE survey fields).
- `backend/.../emergency/service/EmergencyService.kt` — register (+ `emergency.registered` event), triage (ESI 1–5 validation, once-only; **ESI ≤ 2 sets priority "stat" on the registration**; + `emergency.triaged` event), trauma activate/update (phase-guarded; + `emergency.trauma_activated`/`emergency.trauma_completed` events), status/disposition update (admitted/discharged; + `emergency.disposition_changed` event).
- `backend/.../emergency/api/EmergencyController.kt` + `EmergencyDtos.kt` — REST surface.
- `backend/.../emergency/repository/EmergencyRepositories.kt` — repositories.

## 3. Data/API/event contracts delivered
- Schema: `V33__emergency.sql` → `emergency.emergency_registration`, `emergency.triage_assessment`, `emergency.trauma_workflow`.
- API: `/api/v1/emergency/registrations` (POST, GET list w/ status/patientId), `/{id}` (GET), `/{id}/triage`, `/{id}/trauma/activate`, `/{id}/trauma` (PUT survey), `/{id}/status`.
- Events: 4 domain events (registered, triaged, trauma_activated/completed, disposition_changed), direct Kafka.
- Permissions seeded: `V34__seed_emergency_perms.sql`.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | **FAIL** | Emergency registration → ESI triage → trauma workflow → disposition works end-to-end (PIT `emergencyWorkflowCoverage`, incl. admission linkage to IPD). Missing named contents: **queue priority boost** — ESI sets `registration.priority = "stat"` inside the emergency module only; **no call into receptionqueue** (EmergencyService has no QueueService dependency; queue priority is never raised), **printed downtime pack** (none), **token/check-in re-entry tool** (none; CS-09 unimplemented). Gap list: queue boost wiring · downtime pack · re-entry tool |
| 2 Zero-stub | PASS | CI run 43548016617 (gate 2 green); no stub markers in emergency module |
| 3 Types | PASS | CI run 43548016617 |
| 4 Lint + format | PASS | CI run 43548016617 |
| 5 Tests | PASS | CI run 43548016617; named tests below; merged instruction coverage 80.13% ≥ 0.80 |
| 6 Accessibility | PASS | CI run 43548016617 (axe 0 serious/critical); emergency has no dedicated web page in SCAN_ROUTES or elsewhere (no emergency UI surface found) |
| 7 Design system | PASS | CI run 43548016617 (visual regression green) |
| 8 Contracts | **FAIL** | schema-registry harness not yet wired (00.02 scope); OpenAPI surfaces all module groups (emergency included per PIT `openApiExposesAllModuleSurfaces` listing of 24 route groups); 4 events lack committed schemas |
| 9 Data | **FAIL** | Migration `V33__emergency.sql` applied; **no rollback note**; **no RLS/cross-tenant denial suite** (00.04/ADR-13 pending); `hospital_id` nullable + required-by-DTO (client-supplied) |
| 10 Audit | PASS | `AuditAspect` intercepts EmergencyController writes → audit rows for register/triage/trauma/status; audit read asserted in PIT `collaborationAndWorkflowJourney`. Caveat: plain audit table (ADR-9 pending) |
| 11 Docs | PASS | This report; chunk file lists contents accurately; ledger row stays PLANNED |
| 12 Exit item | **FAIL** | no scripted slice yet (downtime drill is 01.12 exit scope; phase1-e2e.ps1 does not cover emergency — Phase-1 predates the module) |

## 5. Tests
Named PIT tests (green in run 43548016617): `emergencyWorkflowCoverage` (registration list/get, triage incl. ESI validation path, trauma activate + survey phases, status transitions, admission linkage via `/api/v1/admissions`), `openApiExposesAllModuleSurfaces` (surface parity). Pass counts: PIT 37/37. Coverage: merged instruction 80.13% (branch 52%).

## 6. Accessibility
No emergency-specific web page exists; nothing to axe-scan beyond the repo-wide green run (43548016617).

## 7. Issues found during build + fixes
Baseline section — see BASELINE-AUDIT.md §2.1 (emergency row: PARTIAL — "no printed downtime pack / re-entry tool evidence"). Verified in current tree: emergency module is now substantially richer than the audit's one-line description (ESI triage validation, trauma ABCDE workflow, 4 domain events, admission linkage) — audit understates it; the two named gaps (queue boost, downtime pack/re-entry) are confirmed absent.

## 8. External blockers
None for code; downtime drill requires the 01.12 exit week.

## 9. Deviations from chunk file
Zero (SC-6 baseline adoption).

## 10. Exit-demo slice
PIT `emergencyWorkflowCoverage` (integration-gates, run 43548016617). Reproduce locally: `./gradlew integrationTest --tests "*emergencyWorkflowCoverage*"` with the compose stack up. No CLI scripted slice (phase1-e2e.ps1 lacks emergency).
