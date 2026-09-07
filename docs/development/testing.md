# Testing Strategy

## Levels

1. Unit tests — Vitest (TS), JUnit 5 (Kotlin), 80% minimum coverage
2. Integration tests — Testcontainers (PostgreSQL, Redis, Kafka)
3. Contract tests — API contracts between frontend/backend
4. E2E tests — Playwright across critical clinical workflows
5. Performance — API p95 < 200ms, p99 < 1s
6. Security — OWASP Top 10 checks

## Running tests

- Frontend: `npm test -w @squamed/web`
- Backend: `cd backend/squamed-core && ./gradlew test`
- E2E: `npm run test:e2e`
