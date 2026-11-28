## The defect

`POST /api/v1/audit/verify` loaded and re-hashed **every chained audit row for every tenant** inside one request, then returned the breaches — including other tenants' chain keys — to anyone holding `audit:read:audit`.

Two consequences, both observed rather than theorised:

1. **It stops completing.** Locally the table reached 209,905 rows across 2,364 chain heads after ~30 full-suite runs; the call blew a 30 s client timeout and one full-suite run died because of it. A hospital admin clicking "chain-integrity check" on a real deployment would hit the same wall.
2. **Cross-tenant disclosure.** A `READ_ONLY_VIEWER` bound to one hospital could read the breach list for every other hospital — chain keys and sequence numbers included.

## The fix

| Caller | Before | After |
|---|---|---|
| Hospital-scoped user (admin, auditor) | full platform sweep | **own chain only** |
| Platform admin, no params | full platform sweep | platform chain only |
| Platform admin, `?all=true` | n/a | explicit full sweep (same cost as the nightly job) |
| Hospital user passing another tenant's `?hospitalId=` | n/a | `403 AUDIT_VERIFY_TENANT_MISMATCH` |
| Hospital user passing `?all=true` | n/a | `403 AUDIT_VERIFY_ALL_PLATFORM_ONLY` |

The nightly sweep is unchanged (`verifyAll`), and the freshness gauges (`squamed.audit.chain.breaches`, seconds-since-verify) are now updated **only** by a platform sweep — a tenant-scoped check can no longer make the platform gauge claim everything was verified.

## Tests

- `auditChainCoverage` now verifies as the **tenant-bound auditor**, asserts the tamper is detected on that chain, and adds the two guards: another hospital's admin cannot verify `?hospitalId=<h1>` (403) and cannot request `?all=true` (403).
- `dlqDepthAndAuditBreachGaugesFlip` uses the explicit `?all=true` sweep, so its gauge assertion still means what it says.
- Both pass; full suite below.

## What this does not fix

The full sweep is still a full re-hash. It is now only reachable by the nightly job or an explicit platform-admin request, which is where unbounded work belongs. Making it **incremental** (verify from each chain head's last verified sequence) is the next step for very large installations; that is a change to the chain protocol, not to this endpoint.

Evidence: full chain run — see the CI result on this PR.
