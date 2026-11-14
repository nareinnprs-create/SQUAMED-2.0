# Section 00.03 — Identity & access

## 1. Scope
Keycloak realm-as-code, OIDC integration (web + API), staff user entity, role model, RBAC service, MFA for admin/auditor, session + device registry, permission middleware.

## 2. Built (current tree)
- `identity/security/JwtService.kt` — HS256 JWT issue/verify, sid/hp/typ claims, 10-min MFA token.
- `identity/security/TotpService.kt` + `identity/service/MfaService.kt` — TOTP MFA (enable/verify, otpauth URL).
- `identity/service/AuthService.kt` — login/verifyMfa/refresh/logout, BCrypt, 5-fail lockout 15 min, session records.
- `identity/domain/SessionRecord.kt` + `identity/security/TokenBlacklistService.kt` — token-hash sessions, Redis revocation.
- `identity/security/SquamedPermissionEvaluator.kt`, `PermissionAspect.kt`, `RequiresPermission.kt`, `JwtAuthenticationFilter.kt`, `SecurityHeadersFilter.kt`, `SecurityConfig.kt` — RBAC + permission middleware + hardening.
- `identity/domain/{UserAccount,UserProfile,Role,Permission,UserRole}.kt` + 5 repositories + `RoleAdminController.kt` — staff user/role model.
- Migrations `V1__init_identity.sql`, `V3__seed_permissions.sql` + per-module seeds V5–V40.
- Frontend `packages/auth/` — login/MFA flow client.

## 3. Contracts delivered
- REST auth surface: login/verify-mfa/refresh/logout/register, role admin APIs, `/ws` authenticated upgrade.
- 23+ seeded domain:action:resource permissions (V3, V25, V26 + module seeds).
- NOT delivered: Keycloak realm-as-code (grep: Keycloak in docs only — no realm export, no compose service), OIDC token exchange (web+API), external IdP integration, device registry (SessionRecord is close, not a device registry).

## 4. Gates
1. FAIL — named contents missing: Keycloak realm-as-code, OIDC integration, device registry.
2. PASS — CI run 43548016617 (gate 2 green).
3. PASS — CI run 43548016617 (gate 3 green).
4. PASS — CI run 43548016617 (gate 4 green).
5. PASS — CI run 43548016617 (gate 5 green); SecurityTests 7 + auth/realtime coverage below.
6. PASS — CI run 43548016617 (gate 6 green) (login page in axe scope).
7. PASS — CI run 43548016617 (gate 7 green).
8. FAIL — schema-registry harness not yet wired (00.02 scope); auth contract covered by PIT assertions only.
9. FAIL — `V1__init_identity.sql` exists with rollback note absent; user_role/session carry hospital_id → tenant-scoped, but RLS cross-tenant denial suite ABSENT until 00.04 lands (ADR-13).
10. PASS — AuditAspect deliberately excludes AuthController (login/refresh); all other identity writes (RoleAdminController) intercepted; audit rows asserted PIT-side.
11. PASS — this report; ledger D16 keeps the Keycloak/OIDC decision open (baseline-adopted JWT stack).
12. PASS — scripted slice: `tests/e2e/login.spec.ts` + PIT `authRefreshLogoutMfaAndRealtimeCoverage` (login→MFA→refresh→logout journey).

## 5. Tests
PIT (7 SecurityTests + 1 coverage test, integration-gates green run 43548016617): `unauthenticatedRequestsAreRejected`, `tamperedOrForgedTokensRejected`, `hardenedSecurityHeadersArePresent`, `userWithoutRolesIsDenied`, `validationErrorsUseCleanContract`, `injectionPayloadsAreNeutralized`, `csrfNotApplicableWithoutSessionCookie`, `authRefreshLogoutMfaAndRealtimeCoverage`. E2E: `login.spec.ts`, `api-smoke.spec.ts`. Coverage ≥0.80 merged (SC-10) enforced.

## 6. Accessibility
Login/MFA pages inside axe gate scope; run 43548016617 gate 6 green (zero critical/serious).

## 7. Issues found during build
Baseline section — see BASELINE-AUDIT.md D16 pack. Verified current tree: Keycloak still docs-only (no realm-as-code artifacts, no compose service); hand-rolled TOTP unchanged; no device registry.

## 8. External blockers
None (D16 keep-vs-migrate is a decision, not a blocker).

## 9. Deviations from chunk file
Zero SC-logged deviations; contract's Keycloak/OIDC wording is unmet by the adopted baseline (SC-6) — decision D16 open.

## 10. Exit-demo slice
`npx playwright test tests/e2e/login.spec.ts` + PIT `authRefreshLogoutMfaAndRealtimeCoverage` — green in run 43548016617.
