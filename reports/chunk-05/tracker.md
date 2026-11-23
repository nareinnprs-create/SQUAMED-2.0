# Chunk 05 PIT tracker

Seed frame: 00.00. Keep one line per PIT.

| ID | Description | Status | Run | Notes |
|----|-------------|--------|-----|-------|
| 05.01 | aiGatewayGovernanceCoverage | PASS | local (CI pending) | redaction [QID]/[MRN], raw digits absent from the log; tokens split + promptVersion ops@N + capability chat; doctor count allowed / nurse 403 AI_SCOPE_DENIED; kill switch off 409 AI_CAPABILITY_DISABLED -> on 200; cap 1 409 AI_DAILY_TOKEN_CAP_REACHED -> restored; usage 4 capabilities tokens>0 cap 200000; priced model costUsd>0 (agent restored); self-hosted model registered/removed |
| 05.03 | aiCommandBarGroundingCoverage | PASS | local (CI pending) | count/analytics citations (count.patients, analytics.overview); patient search cites record id; create-task draft on the PROPOSED queue; doctor discharge-plan approval 403 AI_SCOPE_DENIED; admin approval creates the task + sets expectedDischargeAt; reject recorded + re-approve 409 AI_ACTION_NOT_PENDING; outbox ai.action_proposed/approved/rejected; catalog 46 |
