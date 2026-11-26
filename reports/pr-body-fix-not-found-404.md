## fix: two API-boundary defects found during the chunk-7 exit work

### 1. Unmatched `/api/v1/**` path returned 500, not 404
Found running the exit demo: a GET to a path that does not exist produced

```
NoResourceFoundException: No static resource api/v1/migration/jobs.
GlobalExceptionHandler "Unexpected error" -> 500 INTERNAL_ERROR
```

The `Exception` catch-all swallowed `NoResourceFoundException`, so every typo'd API URL was reported to clients as a server fault.

- `GlobalExceptionHandler`: explicit `NoResourceFoundException` handler → `404` with `{"error":{"code":"NOT_FOUND","message":"No resource at <path>"},"timestamp":...}`.
- `ContractTests.errorResponsesFollowContract`: asserts an unknown path returns `404` with `code = NOT_FOUND`.

### 2. SLA scan raced the scheduled pass and failed the caller with 500
`taskEngineCoverage` failed once in a full run (passed standalone, passed in earlier runs): `POST /api/v1/tasks/sla/scan` returned no body. The on-demand scan and the `@Scheduled` pass (`0 */15 * * * *`) both escalate the same open tasks, so the loser hit an optimistic-lock conflict and the whole scan 500'd.

- `TaskService.scanAndEscalate`: a task lost to a concurrent pass (`ObjectOptimisticLockingFailureException` / `DataIntegrityViolationException`) is skipped, not fatal. The breach counter only increments on a successful escalation.
- `taskEngineCoverage`: the scan call now asserts 2xx with the response body, so a future failure names the actual error instead of an NPE.

### Evidence
- Targeted PITs: `errorResponsesFollowContract` PASS, `taskEngineCoverage` PASS (standalone).
- Full chain: `ktlintCheck integrationTest test jacocoTestReport jacocoTestCoverageVerification` — see the run recorded on this PR.

### Found but not fixed (recorded, not hidden)
A full-chain run on the long-lived dev database (20,044 users / 26,813 patients) timed out on `GET /api/v1/fhir/Practitioner`: the FHIR list endpoints are unbounded and unscoped, so they serialize every row. CI is unaffected (fresh database per run), but a group with ~20k staff would hit the same wall. Fixing it needs FHIR `_count` paging plus tenant scoping — a contract change that belongs in its own pass. The dev database was reset so the local chain reflects CI conditions.
