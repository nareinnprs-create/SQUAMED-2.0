# Section 00.11 — Task engine

## 1. Scope
Task CRUD + assignment + completion + SLA escalation rules (config-driven), subject links (patient/config/dept), inbox UI.

## 2. Built (current tree)
- `task/domain/Task.kt` + `task/repository/TaskRepository.kt` — task entity/persistence.
- `task/api/TaskController.kt` + `TaskDtos.kt` — CRUD, `/assign`, `/status` APIs (hospitalId still client-supplied, default null).
- `task/service/TaskService.kt` — lifecycle logic.
- `workflow/` module (12 files: definition/instance/step/approval domains + service + controller) — generic workflow engine (definitions, instances, decisions, branch coverage).
- Migrations `V19__tasks_notifications.sql`, `V23__workflow.sql` (+ permission seeds).
- Notifications on task events via `notification/` service (unread-count/list/read/read-all APIs).

## 3. Contracts delivered
- Task CRUD/assign/status APIs; workflow definition/instance/decision APIs.
- NOT delivered: SLA escalation rules (zero grep hits for sla/escalat in task module), config-driven rules, inbox UI (no task page under apps/web), subject links thin.

## 4. Gates
1. FAIL — named contents missing: SLA escalation, config-driven rules, subject links, inbox UI.
2. PASS — CI run 43548016617 (gate 2 green).
3. PASS — CI run 43548016617 (gate 3 green).
4. PASS — CI run 43548016617 (gate 4 green).
5. PASS — CI run 43548016617 (gate 5 green); named tests below.
6. PASS — CI run 43548016617 (gate 6 green) (no task inbox page; workflow page in axe scope).
7. PASS — CI run 43548016617 (gate 7 green).
8. FAIL — schema-registry harness not yet wired (00.02 scope).
9. FAIL — `V19__tasks_notifications.sql` + `V23__workflow.sql` exist; task hospital_id client-supplied/nullable and RLS cross-tenant denial suite ABSENT until 00.04 lands (ADR-13).
10. PASS — AuditAspect intercepts TaskController + WorkflowController writes; audit rows asserted PIT-side.
11. PASS — this report.
12. PASS — PIT `collaborationAndWorkflowJourney` covers create→assign→in_progress→completed + notification fan-out (API-level slice).

## 5. Tests
PIT: `collaborationAndWorkflowJourney` (task create/assign/status transitions, notification unread-count/list/read-all), `workflowEngineBranchCoverage` (definition/instance/decision branches) — green, run 43548016617. Coverage ≥0.80 merged enforced.

## 6. Accessibility
workflow page in axe gate scope; task inbox does not exist. Run 43548016617 gate 6 green.

## 7. Issues found during build
Baseline section — see BASELINE-AUDIT.md §2.1 task + workflow rows. Verified current tree: no SLA/escalation code; `TaskDtos.kt` hospitalId default-null unchanged (client-controlled tenancy defect persists pending 00.04).

## 8. External blockers
None.

## 9. Deviations from chunk file
Zero; missing contents are unbuilt, not re-scoped.

## 10. Exit-demo slice
PIT `collaborationAndWorkflowJourney` (task created on patient event → assigned → completed → notification delivered) — green in run 43548016617.
