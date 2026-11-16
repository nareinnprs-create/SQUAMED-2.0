# INFRASTRUCTURE & DEPLOYMENT SPEC
**IaC:** Terraform (cloud) + Helm (k8s) + compose (dev). Everything version-controlled; console drift = finding.

## 1. Environment matrix
| Env | Host | Purpose | Data class |
|---|---|---|---|
| dev | compose on engineer machines | Inner loop | synthetic |
| staging | k8s, India region | RC, DAST, perf, demos | synthetic, prod-shaped |
| test-hospital | k8s namespace | Training/UAT/sandbox hospital | synthetic |
| prod | k8s, India region (Mumbai) | Live hospitals | PHI — full controls |
| DR | Warm standby region | RTO 4h / RPO 15min | replicated |

## 2. Production topology (per 1-hospital tenant, NFR §6 sizing)
| Component | Sizing (initial) | HA |
|---|---|---|
| `squamed-core` (monolith) | 3 × (2 vCPU/4 GB), HPA to 6 | rolling deploys, health-gated |
| WebSocket layer | sticky-by-node + Redis pub/sub fan-out | 2+ replicas |
| PostgreSQL 16 primary | 4 vCPU/16 GB, PITR + nightly logical dump | 1 standby (sync), auto-failover |
| Redis | 2 GB, AOF | replica |
| OpenSearch | single node → 3 data nodes at scale | snapshot nightly |
| Keycloak | 2 replicas + its own PG schema | LB |
| Orthanc/OHIF | 2 vCPU/4 GB, storage on object API | 2 replicas |
| Mirth | 1–2 by hospital interface count | channel persistence |
| Object storage | S3-API (R2 cloud / MinIO on-prem) | versioning + lifecycle |
| Edge | Cloudflare Business zone + Zero Trust + Tunnel (on-prem sites) | anycast |

Scales linearly per hospital; group deployments shard by tenant (ADR-2) before vertical limits are approached.

**Dev reality (the truth until k8s):** `infrastructure/docker/docker-compose.yml` — Postgres 16 **:5433**, Redis 7 **:6380**, Kafka 3.7.1 **:9092**, OpenSearch 2.14.0 **:9200**, MinIO fork `pgsty/silo` **:9000/:9001** (minio/minio images were deleted from Docker Hub), Temporal 1.24.2 **:7233** + Temporal UI **:8088**, Prometheus **:9090**, Grafana **:3005**, Alertmanager **:9093**, otel-collector **:4317/:4318**. App boots on **:8081** (host :8080 is occupied by an unrelated container). **Keycloak, Orthanc/OHIF, Mirth, and Loki are not in the dev stack** — auth is the self-hosted JWT service, DICOM/HL7 paths are hand-rolled in-process (10 §2), logs go to stdout (no Loki).

## 3. Observability (stack: OpenTelemetry + Prometheus + Grafana + Alertmanager; Loki not deployed — logs on stdout)
- Traces: HTTP → service → DB → WS end-to-end; 10% sampling normal, 100% on error.
- Metrics: RED per module + business metrics (events/min per type, SLA breach counts, DLQ depth).
- Alerts → on-call: SLO burn (§6 NFRs), audit-chain breach, DLQ non-empty > 15 min, failover events, cert expiry.
- Logs: structured JSON, **PHI scrubber at ship time** (enforced by test), 30d hot / 1y cold.
- Runbooks linked from every alert (what to check, what to touch, what never to touch).

## 4. Backup & restore
- PITR continuous (WAL) + nightly logical dumps + weekly restore drill into scratch namespace (**restore time measured and reported** — untested backups don't exist).
- Audit exports nightly to WORM (S3 Object Lock compliance mode; MinIO WORM on-prem).
- Patient-facing objects versioned; deletion = lifecycle-only after retention schedule (§12).

## 5. DR runbook (drilled quarterly, timed against RTO 4h/RPO 15min)
1. Declare (criteria: region outage > 15 min or data-corruption event) → 2. Promote standby PG / restore latest PITR → 3. Redirect DNS via load balancer health-check failover → 4. Boot app fleet in standby (IaC, no hand-built steps) → 5. Verify: health, synthetic transaction, audit chain verify, WS fan-out → 6. Communicate (hospital status page + downtime pack if clinical impact) → 7. Post-drill report with RTO/RPO actuals into ledger.
- On-prem/sovereign topology (chunk 8) passes the same drill **without cloud dependency**.

## 6. Release engineering
- Trunk-based; tagged immutable builds; expand-contract migrations forward-only.
- Rolling deploys with health gates; feature flags decouple deploy from release; per-tenant flags (entitlements) enable staged rollout.
- Blue/green reserved for schema-risky releases (chunk 3+ money paths).
- Rollback = previous image + migration contract guarantees (expand first) — rehearsed in staging each chunk.

## 7. Cost envelope (monthly, cloud, at 2-hospital scale — indicative)
App fleet ~$150 · managed PG ~$250 · Redis ~$50 · backups/objects ~$60 · observability ~$80 · edge/Zero Trust ~$300 · CI ~$40 → **~$900–1,000/mo** excluding AI/STT usage (unit economics §10 master plan). On-prem: same components on hospital hardware; licensing identical (entitlement-gated).
