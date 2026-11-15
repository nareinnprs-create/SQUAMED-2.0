# On-call runbook (00.16)

Alert routing: Prometheus evaluates the rules in `infrastructure/docker/config/alerts.yml`
(SLO burn + business groups) and ships every alert to Alertmanager
(`infrastructure/docker/config/alertmanager.yml`), which pages the `squamed-ops` webhook
receiver (`host.docker.internal:9095`, overridden per deployment) — critical alerts on a
separate route. Escalation: warning -> next business day, critical -> immediate page.

Severity ladder: `critical` pages (SLA/audit/availability), `warning` informs.

## SLO burn alerts (NFR §6 budgets)

Budgets: availability 99.9% monthly => 0.1% error budget; latency p95 < 300 ms reads (GET) /
< 500 ms writes => 5% of requests slower. Multiwindow burn detection: fast window (5m + 1h)
at 14.4x burn = critical; slow window (30m + 6h) at 6x burn = warning.

### SloAvailabilityBurnFast (critical)
5xx ratio > 1.44% over both 5m and 1h (burning the whole monthly budget in ~2 days).
1. `http://localhost:9090/graph?g0.expr=squamed%3Aslo%3Ahttp_error_ratio%3A5m` — which
   status codes dominate: `sum by (status) (rate(http_server_requests_seconds_count{status=~"5.."}[5m]))`.
2. `kubectl logs deploy/squamed-backend --tail=200` or the compose backend logs; check
   Postgres/Redis/Kafka health (`docker compose ps`).
3. If a deploy preceded it, roll back first, diagnose after.

### SloAvailabilityBurnSlow (warning)
5xx ratio > 0.6% over both 30m and 6h — slow burn, budget gone in ~1 week. Same triage as
above; schedule the fix for the current sprint.

### SloLatencyBurnFast (critical) / SloLatencyBurnSlow (warning)
Bad ratio = share of requests slower than p95 targets (GET 0.3 s, others 0.5 s) over the
burn windows (72% / 30% thresholds).
1. Find the slow routes: `topk(10, histogram_quantile(0.95, sum by (le, uri)
   (rate(http_server_requests_seconds_bucket[5m]))))`.
2. Check DB pool + slow queries: `hikaricp_connections_pending`,
   `hikaricp_connections_usage_seconds`; otel traces for the slow route (JDBC spans in the
   collector debug output).
3. Full GC / heap pressure: `SquamedJvmHeapPressure` firing alongside is the usual cause.

## Audit + DLQ (business alerts)

### AuditChainBreached (critical)
`squamed_audit_chain_breaches > 0` — ADR-9 hash-chain verify found tampered rows.
1. Do NOT delete or rewrite audit rows (10-year WORM retention, compliance evidence).
2. Identify the tenant: `POST /api/v1/audit/verify` (admin) returns the breach list with
   seq + reason (HASH_MISMATCH / LINK_BROKEN / MISSING_HASH); a matching
   `audit.chain_broken` event was published to the event fabric.
3. Freeze writes for the affected tenant if HASH_MISMATCH persists, escalate to the
   compliance owner, pull the last WORM export for the tenant (`POST /api/v1/audit/export`).

### AuditVerifyStale (warning)
`squamed_audit_chain_seconds_since_verify > 90000` (~25 h) — nightly verify (03:00 cron)
has not completed since the last run.
1. Check the backend scheduler: `GET /actuator/loggers` for `com.squamed.core.audit`
   errors; verify DB reachable at 03:00.
2. Run `POST /api/v1/audit/verify` manually; the gauge resets on completion.

### DlqNonEmpty (warning)
`min_over_time(squamed_events_dlq_depth[15m]) > 0` — outbox dead-letters (FAILED rows)
present for over 15 minutes.
1. List them: `GET /api/v1/admin/events/outbox?status=FAILED` — read `lastError`.
2. Fix the consumer/broker problem (Kafka down, bad payload); the DLQ is replayable.
3. Replay: `POST /api/v1/admin/events/outbox/{id}/replay` per row (or the admin batch
   endpoint). Depth gauge returns to 0 once replayed.

Note: inbound integration failures are tracked separately
(`squamed_integration_errors`, OPEN rows awaiting replay via IntegrationErrorService) and
are not part of the DlqNonEmpty alert — page manually from the Grafana stat panel when the
backlog grows.
