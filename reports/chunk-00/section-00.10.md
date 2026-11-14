# Section 00.10 — Audit

## 1. Scope
Write-path interceptor, hash-chained audit table, auditor viewer, nightly chain-verify job (pages on breach), WORM export job (MinIO dev / S3 Object Lock prod config).

## 2. Built (current tree)
- `audit/service/AuditAspect.kt` — AOP interceptor over every `com.squamed.core..*Controller` write (AuthController excluded).
- `audit/service/AuditRecorder.kt` + `audit/repository/AuditLogRepository.kt` — persistence.
- `audit/domain/AuditLog.kt` — plain table: actor, action, entity, ip/ua, method/path, request/response payloads, occurred_at. NO hash/previous_hash column.
- `audit/api/AuditController.kt` + `AuditDtos.kt` — `/audit/logs` read API (auditor read path).
- Migration `V22__audit.sql` (+ `V2__identity_audit_columns.sql`, `V9`, `V18` per-table audit columns).

## 3. Contracts delivered
- Write-path interception (all controllers, PIT-verified rows land), `/audit/logs` read API.
- NOT delivered: hash chain (no `previous_hash` — grep zero hits), nightly chain-verify job, WORM export job, auditor viewer UI (API only, no page).

## 4. Gates
1. FAIL — named contents missing: hash-chained table, auditor viewer UI, nightly chain-verify, WORM export.
2. PASS — CI run 43548016617 (gate 2 green).
3. PASS — CI run 43548016617 (gate 3 green).
4. PASS — CI run 43548016617 (gate 4 green).
5. PASS — CI run 43548016617 (gate 5 green); audit-write assertions inside `fullPatientRevenueJourney`.
6. PASS — CI run 43548016617 (gate 6 green) (no audit UI page).
7. PASS — CI run 43548016617 (gate 7 green).
8. FAIL — schema-registry harness not yet wired (00.02 scope).
9. FAIL — `V22__audit.sql` exists; `audit_log.hospital_id` is nullable and table is tenant-scoped, RLS cross-tenant denial suite ABSENT until 00.04 lands (ADR-13).
10. PASS — this IS the audit section: interceptor present and PIT-verified (`audit logs for patient` + `audit rows recorded` assertions).
11. PASS — this report; hash-chain absence flagged (ADR-9, chunk-0 item), not hidden.
12. PASS — PIT `fullPatientRevenueJourney` slice: write patient → `/audit/logs?entityType=Patient` returns ≥1 row (API-level scripted slice).

## 5. Tests
PIT: audit assertions in `fullPatientRevenueJourney` (rows recorded, global read, entity-type filter) + audit authz inside `unauthenticatedRequestsAreRejected`/`userWithoutRolesIsDenied` — green, run 43548016617. Coverage ≥0.80 merged enforced.

## 6. Accessibility
N/A UI (no auditor page). Gate 6 green repo-wide (run 43548016617).

## 7. Issues found during build
Baseline section — see BASELINE-AUDIT.md §2.1 audit row. Verified current tree: `AuditLog.kt` still has NO `previous_hash`/hash columns; no verify/export jobs; interceptor + read API intact.

## 8. External blockers
None.

## 9. Deviations from chunk file
Zero; hash chain / verify job / WORM export are unbuilt (ADR-9 chunk-0 items), not re-scoped.

## 10. Exit-demo slice
PIT `fullPatientRevenueJourney` audit assertions (write → audit row → filtered read) — green in run 43548016617. Tamper-test slice (chain breach detection) not possible until hash chain exists.
