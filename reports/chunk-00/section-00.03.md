# Section 00.03 — Identity & access

## 1. Scope
Identity & access for the whole platform (chunk-00 item 4, D16 → ADR-11): baseline JWT+TOTP+RBAC stack retained (Keycloak/OIDC deferred until an SSO-federation requirement lands; ABHA linkage per ADR-3 unaffected). 00.03 residual build scope after the deferral: **device registry, TOTP library swap, JWT signing-key rotation**. Staff user entity, role model, RBAC service, MFA for admin/auditor, session + permission middleware were baseline-covered.

## 2. Built (current tree)
Baseline stack (adopted, unchanged this section except where named):
- `identity/security/JwtService.kt` — HS256 issue/verify, sid/hp/typ claims, 10-min MFA token. **00.03: rewritten for kid rotation** (below).
- `identity/security/TotpService.kt` + `identity/service/MfaService.kt` — TOTP MFA (enable/verify, otpauth URL). **00.03: lib swap** (below).
- `identity/service/AuthService.kt` — login/verifyMfa/refresh/logout, BCrypt, 5-fail lockout 15 min, session records. **00.03: device wiring at login**.
- `identity/domain/SessionRecord.kt` + `identity/security/TokenBlacklistService.kt` — token-hash sessions, Redis revocation.
- `identity/security/SquamedPermissionEvaluator.kt`, `PermissionAspect.kt`, `RequiresPermission.kt`, `JwtAuthenticationFilter.kt`, `SecurityHeadersFilter.kt`, `SecurityConfig.kt` — RBAC + permission middleware + hardening.
- `identity/domain/{UserAccount,UserProfile,Role,Permission,UserRole}.kt` + 5 repositories + `RoleAdminController.kt` + migrations `V1`/`V3` + per-module seeds; frontend `packages/auth/`.

00.03 hardening (PR #15, d2d6278):
- **Device registry** — `V42__device_registry.sql` (`identity.device` with unique `(user_id, fingerprint)`, full BaseEntity audit columns per V2 pattern; `session.device_id` FK + index; rollback note in header). `DeviceRegistryService`: fingerprint = SHA-256(login User-Agent), server-side only; `registerOrTouch` (a revoked device logging in again un-revokes — a fresh login is a fresh, user-visible grant), `listDevices`, `revokeDevice` (revokes row, kills bound sessions in Postgres **and** Redis via `TokenBlacklistService.revokeSession`) publishing `device.registered`/`device.revoked` DomainEvents (gate-8 envelope). `DeviceController` (`GET/DELETE /api/v1/auth/devices`) deliberately outside `AuthController` so `AuditAspect` audits every device call. `SessionRecord.deviceId` + `SessionRepository.findByDeviceIdAndRevokedAtIsNull`.
- **TOTP lib swap** — hand-rolled HMAC replaced by `dev.samstevens.totp:totp:1.7.1` (RFC 6238). Stored secret format unchanged (`UserAccount.mfaSecret` stays base64; `TotpService.toBase32` re-encodes at the boundary, unpadded RFC 4648, shared with MfaService's otpauth URL).
- **JWT kid rotation** — `JwtKeyProperties` (`@ConfigurationProperties`, `@ConfigurationPropertiesScan` on app): `secret`, `keys` (kid→secret), `active-kid` (default `default`). Empty `keys` = legacy single-secret mode — current deployments unchanged. `JwtService` signs with `keys[active-kid]` + header kid (jjwt 0.12.6 `keyId`), verifies by **pinning** kid→key (unknown kid fails), kid-less pre-rotation tokens verified via try-all-then-key (jjwt has no signed-token header peek → manual split + Base64url + Jackson). Runbook: `docs/development/key-rotation.md`.
- Contract: OpenAPI snapshot + `@squamed/api-client` codegen refreshed + drift stamp pinned (`docs/contracts/** text eol=lf` in `.gitattributes` after a CRLF worktree copy drifted the stamp hash).

## 3. Contracts delivered
- REST auth surface (baseline): login/verify-mfa/refresh/logout/register, role admin APIs, authenticated `/ws`.
- 00.03: `GET /api/v1/auth/devices` (list of `DeviceView{id,label,firstSeenAt,lastSeenAt,revoked}`), `DELETE /api/v1/auth/devices/{deviceId}` → `{revoked: true}`; snapshot + codegen stamped sha256 d2b0238ec…
- 23+ seeded domain:action:resource permissions (V3, V25, V26 + module seeds).
- NOT delivered (deferred by D16 → ADR-11, logged as chunk-00 item 4 amendment): Keycloak realm-as-code, OIDC token exchange (web+API), external IdP integration — re-opened when an SSO-federation requirement lands.

## 4. Gates
1. PASS — zero real stubs; CI run 43561720259 green (first run 43560960465 tripped codegen stamp-drift on a CRLF worktree snapshot → LF-normalized + .gitattributes pin, retried green; mechanism, no SC amendment).
2. PASS — run 43561720259.
3. PASS — run 43561720259.
4. PASS — run 43561720259.
5. PASS — run 43561720259; new `TotpServiceTest` (10/10: RFC 4226 vectors via fixed `TimeProvider`, ±1 window, base32 boundary) + `JwtServiceRotationTest` (5/5: legacy roundtrip, active-kid signing, unknown-kid reject, signature-mismatch reject, kid-less try-all).
6. PASS — run 43561720259 (login/MFA pages in axe scope).
7. PASS — run 43561720259.
8. PASS — run 43561720259; new `device.registered`/`device.revoked` events published via `DomainEventPublisher` against the envelope-v1 schema harness (00.02).
9. PARTIAL — V42 applied (schema v42) with rollback note (`DROP TABLE identity.device CASCADE; ALTER identity.session DROP COLUMN device_id`); **RLS cross-tenant denial suite intentionally out of 00.03** — lands in 00.04 with hospital_id NOT NULL per ADR-13 (repo-wide gate-9 unblock). Remaining: that suite.
10. PASS — `DeviceController` audited by `AuditAspect` (exclusion list still only `AuthController`); revocation audit trail asserted via PIT + audit rows.
11. PASS — this report; ledger totals 3 COMPLETE / 36 IN PROGRESS / 50 PLANNED; D16/ADR-11 recorded in 24-DECISIONS-REGISTER.md.
12. PASS — scripted live slice: `scripts/demo-00.03-identity-hardening.ps1` — register → login → GET devices (≥1) → DELETE device → refresh of killed session rejected — **5/5 on 8081**, 2026-11-14.

## 5. Tests
- Unit (new): `TotpServiceTest` 5 tests (RFC 4226 ten vectors, adjacent-period window, wrong-code reject, 20-byte secret, base32 vector), `JwtServiceRotationTest` 5 tests. 10/10 green locally + in CI run 43561720259.
- PIT (new): `deviceRegistryListAndRevokeInvalidatesSession` — register → login → GET devices ≥1 → DELETE own device → refresh returns `INVALID_CREDENTIALS` (session killed). Green in run 43561720259.
- PIT (retained): 7 SecurityTests (`unauthenticatedRequestsAreRejected`, `tamperedOrForgedTokensRejected`, `hardenedSecurityHeadersArePresent`, `userWithoutRolesIsDenied`, `validationErrorsUseCleanContract`, `injectionPayloadsAreNeutralized`, `csrfNotApplicableWithoutSessionCookie`) + `authRefreshLogoutMfaAndRealtimeCoverage` — all green against the kid-rotation JwtService (the rotation path is exercised by every authed PIT call). E2E: `login.spec.ts`, `api-smoke.spec.ts`. Coverage ≥0.80 (SC-10) enforced.

## 6. Accessibility
Login/MFA pages inside axe gate scope; gate 6 green in run 43561720259 (zero critical/serious). No UI added by 00.03 (device endpoints are API-surface; a devices-management UI rides a later section).

## 7. Issues found during build
- jjwt 0.12.6 has no signed-token header peek API — kid extraction implemented as split → Base64url decode → Jackson readTree (documented in JwtService).
- `dev.samstevens.totp:totp:1.7.2` does not exist (Maven metadata: latest 1.7.1) — pinned 1.7.1.
- samstevens 1.7.1 exposes no public Base32 util — the unpadded RFC 4648 encoder lives in `TotpService.toBase32` (pinned by test vector).
- jjwt builder method is `keyId(...)`, not `kid(...)` (compile error caught by gate 13 pre-push).
- PIT call-site bug in the new test (bearer token passed positionally into bodyJson) produced a false 401 — fixed to named args; 41/42 pre-existing PIT tests proved the rotation code correct on first run.
- CRLF worktree copy of the OpenAPI snapshot made CI's checkout hash disagree with the codegen stamp — root-caused to line endings, fixed by LF normalization + `docs/contracts/** text eol=lf` (.gitattributes), consistent with the file's existing hash-verified intent.

## 8. External blockers
None. (B2 clinical-lead gate does not block 00.02–00.06 per SC-12; D16 keep-vs-migrate is a decided ADR, not a blocker.)

## 9. Deviations from chunk file
- Keycloak/OIDC deferred per D16 → ADR-11 (SC-6/D16, 2026-11-13, chunk-00 item 4 amended) — the contract's realm-as-code wording is intentionally unmet; SSO federation re-opens it.
- Gate 9 partial: RLS cross-tenant denial suite deferred to 00.04 by ADR-13 (tenant isolation lands as one change with hospital_id NOT NULL).
- TOTP lib and device registry built exactly as scoped in the ADR-11 residual.

## 10. Exit-demo slice
`pwsh scripts/demo-00.03-identity-hardening.ps1` against the running app (8081): register → login (device auto-registered from User-Agent) → GET devices → DELETE device → refresh rejected. **5 passed, 0 failed**, live 2026-11-14. E2E login journey (`login.spec.ts`) + PIT `authRefreshLogoutMfaAndRealtimeCoverage` remain green in CI run 43561720259.
