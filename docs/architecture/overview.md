# SQUAMED Architecture Overview

## Core Pattern

Squamed starts as a **modular monolith** (Kotlin, Spring Boot) with strict package-per-domain boundaries (Section 96). Services are extracted only when justified by scale, isolation, security, independent deployment, performance or organizational boundaries.

## Ten Architectural Planes (Section 3)

1. **Experience** — Web, PWA, Desktop, Mobile, Tablet, Kiosk, TV, Voice
2. **Intelligence** — AI OS, agents, scribe, RAG, voice, model routing
3. **Orchestration** — Workflow, tasks, approvals, SLA, escalation
4. **Clinical** — EMR, OPD, IPD, ICU, Emergency, Surgery, Diagnostics
5. **Operations** — Beds, patient flow, staffing, facilities, assets, supply chain
6. **Revenue** — Billing, coding, claims, finance, revenue integrity
7. **People** — Patient, doctor, nurse, employee, family, communication
8. **Data & Interoperability** — FHIR, HL7, DICOM, devices, events, data fabric
9. **Governance** — Security, identity, consent, compliance, audit, AI governance
10. **Infrastructure** — Cloud, edge, on-premise, DR, observability, deployment

## Event Driven (Section 52)

Hospital activity is event-driven. Domain events flow through Kafka:
`Domain Event -> Event Backbone -> Realtime Gateway -> WebSocket/SSE -> Connected Clients`

## Data Stack (Section 94)

- PostgreSQL (primary), Redis (cache), Kafka (events), OpenSearch (search),
  S3-compatible (objects), ClickHouse (analytics), Temporal (workflows)

## Real-time (Section 2.6)

Realtime by default via WebSocket/SSE. Appropriate workflows never depend on refresh buttons.

## Security (Section 67)

Zero trust: RBAC + ABAC, MFA, SSO, least privilege, encryption at rest and transit, audit trails.

## Multi-tenancy (Section 74)

Tenant-aware architecture with isolation at data, identity, configuration, AI, storage, integration and audit levels.
