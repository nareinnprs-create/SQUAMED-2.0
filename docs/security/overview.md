# SQUAMED Security Model

## Authentication
- JWT access tokens (15 min) + refresh tokens (7 days)
- MFA: TOTP, SMS, Email (configurable per role)
- Token blacklisting in Redis

## Authorization
- RBAC: Role -> Permission -> Resource
- ABAC: policy constraints (department, hospital, time, context)
- Zero Trust: every request authenticated + authorized

## Data Protection
- TLS 1.2+ in transit
- AES-256 at rest
- Sensitive fields encrypted at application level

## Audit (Section 70)
Every data access and modification is recorded with actor, context, before/after, device, workflow, AI agent, model, tool and integration.

## AI Governance (Section 60)
AI inherits user identity + role + data permissions + tool permissions. Every AI interaction is logged.
