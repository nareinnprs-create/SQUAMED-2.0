# Second hospital in under a day (no code)

Checklist for adding a hospital to an existing group. Target: one working day, zero code changes.

## 1. Group + hospital (30 min)
1. Create the group (or reuse it) and attach the new hospital (`POST /api/v1/group/groups`, then
   `POST /api/v1/group/groups/{id}/hospitals/{hospitalId}`).
2. Create the hospital record with its code, timezone and currency.

## 2. Configuration inheritance (1 h)
1. Publish the group defaults for the config types the hospital needs (`working-hours`, `holidays`,
   `shifts`, `queue`, `approval`, `task-sla`, `dnd-hours`, `scheduling`) via
   `PUT /api/v1/group/groups/{id}/config`.
2. Confirm the new hospital resolves the group defaults
   (`GET /api/v1/group/config/{type}?hospitalId=` → `source=group`).
3. Publish only the deliberate local overrides; they win (`source=hospital`).
4. **Group rate cards/packages are still owed** (06.01 deviation): load hospital tariffs until the
   group pricing lands.

## 3. Structure (2 h)
1. Wards, rooms, beds with type/class/isolation/gender policy.
2. Departments, service points, queue zones.
3. Employees (master data) and their portal/HR links.

## 4. Staff + access (1 h)
1. Create the hospital admin + clinical roles scoped to the new hospital.
2. Add `GROUP_ADMIN` / `GROUP_AUDITOR` users where cross-hospital read is needed.
3. Verify tenant pinning: a user bound to hospital A must get 403 on hospital B's config paths.

## 5. Cross-hospital workflows (2 h)
1. Run the group EMPI candidates for a known duplicate identity (same QID) and confirm the match.
2. Capture a `data_sharing` consent and create the share; verify the access gate allows the
   receiving hospital and denies an outside hospital; revoke and re-verify the denial.
3. Create a referral and accept it in the receiving inbox.

## 6. Go-live checks (1 h)
1. Sandbox: enable the sandbox flag and seed synthetic patients for training.
2. Degraded mode drill: activate, confirm identity/financial writes are blocked and a clinical
   append queues + replays once; release.
3. Event trail: `record.shared_consented`, `group.referral_accepted` visible in the outbox.
4. Backup/DR: confirm the new hospital is in the backup scope and the DR runbook lists it.

## 7. Sign-off
| Step | Owner | Done |
|---|---|---|
| Group + hospital | Platform admin | - |
| Config inheritance + overrides | Platform admin | - |
| Structure | Hospital admin | - |
| Staff + access | Hospital admin | - |
| Cross-hospital workflows | Group admin | - |
| Go-live checks | Platform + clinical lead | - |
