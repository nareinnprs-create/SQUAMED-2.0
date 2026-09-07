# SQUAMED — AI-Native Hospital Operating System

Squamed is an AI-native hospital operating system that connects patients, clinicians, nurses, administration, finance, operations, facilities, devices, data and AI agents into one governed, real-time operating environment.

## Monorepo Structure

- `apps/` — web (Next.js), pwa, desktop (Tauri), mobile (React Native)
- `backend/` — squamed-core (Kotlin, Spring Boot, modular monolith)
- `services/` — ai, interoperability, notifications, search, analytics
- `packages/` — ui, design-system, auth, api-client, schemas, domain-types
- `infrastructure/` — docker, terraform, kubernetes, observability
- `docs/` — architecture, api, security, clinical, interoperability
- `tests/` — e2e, integration, fixtures

## Prerequisites

- Node.js >= 20
- Java 21 (Temurin/OpenJDK)
- Docker + Docker Compose
- Git

## Getting Started

1. Install dependencies:
   ```bash
   npm install
   ```

2. Start infrastructure:
   ```bash
   bash scripts/dev-up.sh
   ```

3. Start the frontend:
   ```bash
   npm run dev -w @squamed/web
   ```

4. Start the backend:
   ```bash
   cd backend/squamed-core
   ./gradlew bootRun
   ```

5. Access:
   - Frontend: http://localhost:3000
   - API docs: http://localhost:8080/swagger-ui.html
   - Grafana: http://localhost:3005 (squamed/squamed_dev_password)
   - Temporal UI: http://localhost:8088

## Build

```bash
npm run build
```

## Test

```bash
npm test
npm run test:e2e
```

## Infrastructure Services

| Service | Port | Credentials |
|---------|------|-------------|
| PostgreSQL | 5433 | squamed / squamed_dev_password |
| Redis | 6380 | - |
| Kafka | 9092 | - |
| OpenSearch | 9200 | - |
| MinIO | 9000 / 9001 | squamed / squamed_dev_password |
| Temporal | 7233 / 8088 | - |
| Prometheus | 9090 | - |
| Grafana | 3005 | squamed / squamed_dev_password |

## Architecture

See `docs/architecture/overview.md` for the platform architecture.
See `BUILD-BLUEPRINT.md` for the complete batch-by-batch execution plan.
