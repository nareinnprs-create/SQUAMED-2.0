# SECURITY ARCHITECTURE & THREAT MODEL
**Position:** Zero Trust per master plan §67; this file makes it concrete and testable. Security gates are blocking gates in `03-BUILD-PROTOCOL.md`.

## 1. Assets & trust boundaries
| Boundary | Assets | Controls |
|---|---|---|
| Internet → Edge | PWA, APIs | WAF, DDoS, TLS 1.3 only, rate limits, bot protection |
| Edge → Core API | Clinical data | Self-hosted JWT (HMAC, kid rotation) + permission strings, hospital-scope filters (see 01 §3 Auth flow — OIDC/Keycloak and ABAC are **not** built) |
| Core → Data | PHI, money | App-level tenancy (`hospital_id` + TenantGuards; **RLS deferred to stage 2, ADR-13**), field-level encryption **not yet built** (national identifiers stored as validated plain rows — flagged gap), audit chain |
| Core → Integrations | HL7/FHIR/DICOM, payers, ABDM | Per-interface allowlist, message signing where supported, error quarantine |
| AI calls → Providers | Prompts (PHI) | Redaction layer, zero-retention/DPA contracts, egress allowlist |
| Kiosk/TV devices | Queue data, check-in | Device certificates, locked chrome, no local PHI storage, kiosk-specific tokens |

## 2. STRIDE summary (per surface — full register maintained in `docs/security/threat-register.md`)
| Threat | Highest-risk surface | Control |
|---|---|---|
| Spoofing | Staff login, ABHA portal login | JWT with rotating refresh + TOTP MFA (per-user opt-in; mandatory-for-role enforcement not built), device binding for kiosk tokens |
| Tampering | Clinical records, bills, audit | Append-only + versioned amendments, app-level status machines (no DB triggers), hash-chained audit, WORM export |
| Repudiation | Signing (notes, eRx, consents) | eSign evidence (signature data + timestamp + audit), actor on every write |
| Information disclosure | Cross-tenant, cross-dept, AI prompts, logs | Tenant guards + denial PIT suite, hospital-scope filters on WS + search (ABAC not built), prompt redaction, log scrubber **not yet built** (structured JSON logs; hygiene test covers prod secrets only), no PHI in error messages |
| Denial of service | WS fan-out, public portal | Per-tenant rate caps / WS quotas / kill switch **not yet built** (roadmap) |
| Elevation of privilege | Agent/service accounts, API keys | Named permission strings, key rotation, scope-creep tests in PIT |

## 4a. Permission registry (as shipped)
- Grammar: **`<domain>:<action>:<resource>`** — e.g. `patient:read:patient`, `scheduling:write:appointment`; stored in `identity.permission` (unique triple) and granted to roles via `identity.role_permission`.
- New module = new permissions seeded in the module's own migration, then `@RequiresPermission("...")` on **every** endpoint; enforcement = `PermissionAspect` + `SquamedPermissionEvaluator` (wildcard string match, Redis-cached 60 s). Unseeded permission = every PIT call 403s.
- **Known grant drift (stage-2 RBAC-alignment queue):** `AppointmentController` requires `scheduling:*` while seeds grant DOCTOR the obsolete `appointment:*` domain, and a `FRONT_DESK` grant references a role never created — net effect: only `SYSTEM_ADMIN` can use scheduling/queue/analytics/ai endpoints.

## 3. Cryptography & secrets
- Transit: TLS 1.3 everywhere; internal service traffic mTLS once services extract.
- At rest: managed-disk encryption + Postgres; **field-level envelope encryption** for national identifiers — designed, **not built** (ABHA values stored as validated plain `patient_identifier` rows); keys in KMS/Vault, rotation yearly, key access audited when built.
- Secrets: **never in repo or CI logs** — Vault/SSM; rotation on schedule + on staff exit; secret-scan in CI (gitleaks — **CI job not yet wired**). Dev compose carries explicit non-prod defaults.
- Session: short-lived access tokens (15 min) + rotating refresh; device registry with revoke. **Shipped.**

## 4. Access management
- Joiner/mover/leaver: HR-triggered deprovisioning ≤ 24h; credential-expiry tasks (chunk 6) track professional licenses too.
- Quarterly access review (admin exports role bindings → manager attestation → audit).
- Break-glass: emergency elevated-access account, sealed, every use = page + mandatory post-review.
- Vendor/remote access: Zero Trust Access + browser isolation, no standing VPN (per master plan §68).

## 5. Application security program
- CI: dependency scanning (fail on critical), container image scan, secret scan, SAST — **none wired yet** (CI currently: parity, stub-grep, backend/frontend/a11y/integration gates). DAST baseline per release on staging and pen test cadence unchanged.
- DAST: baseline scan per release on staging.
- Pen test: **before first hospital go-live**, then annual + after any major authz change. Findings triaged critical→48h, high→1wk.
- AI-specific: prompt-injection suite (chunk 5 evals), PHI-leak suite, cost-cap tests.

## 6. Incident response runbook (on-call + security role)
1. **Detect** (alerts: audit-breach, DLQ spikes, anomaly rules, staff report) → 2. **Triage** severity S1–S4 → 3. **Contain** (kill switch / revoke tokens / isolate tenant / failover) → 4. **Eradicate & recover** (patch, restore, verify) → 5. **Notify**: DPDP breach → Data Protection Board + affected patients without delay (form in §12) → 6. **Postmortem** blameless, within 5 working days, actions tracked to closure in task engine.
- Hospital downtime/compromise drill once per chunk from chunk 1 (reuses clinical downtime drill).
