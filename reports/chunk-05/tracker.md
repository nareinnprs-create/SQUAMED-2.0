# Chunk 05 PIT tracker

Seed frame: 00.00. Keep one line per PIT.

| ID | Description | Status | Run | Notes |
|----|-------------|--------|-----|-------|
| 05.01 | aiGatewayGovernanceCoverage | PASS | local (CI pending) | redaction [QID]/[MRN], raw digits absent from the log; tokens split + promptVersion ops@N + capability chat; doctor count allowed / nurse 403 AI_SCOPE_DENIED; kill switch off 409 AI_CAPABILITY_DISABLED -> on 200; cap 1 409 AI_DAILY_TOKEN_CAP_REACHED -> restored; usage 4 capabilities tokens>0 cap 200000; priced model costUsd>0 (agent restored); self-hosted model registered/removed |
| 05.03 | aiCommandBarGroundingCoverage | PASS | local (CI pending) | count/analytics citations (count.patients, analytics.overview); patient search cites record id; create-task draft on the PROPOSED queue; doctor discharge-plan approval 403 AI_SCOPE_DENIED; admin approval creates the task + sets expectedDischargeAt; reject recorded + re-approve 409 AI_ACTION_NOT_PENDING; outbox ai.action_proposed/approved/rejected; catalog 46 |
| 05.07 | aiGovernanceRegistryCoverage | PASS | local (CI pending) | scoped agent allows admin, denies doctor 403 AI_SCOPE_DENIED; eval PASS 100.00 / FAIL 80.00 history newest-first, counts 11/10 400; incident OPEN->INVESTIGATING->CLOSED (resolution required), reopen 409, closed list; cost rows per capability/model; ai.incident_reported outbox; catalog 47 |
| 05.04 | aiRailAttentionCoverage | PASS | local (CI pending) | DISCHARGE_BLOCKER + OT_DELAY + PENDING_AI_APPROVAL + TASK_OVERDUE on the rail, high first, <=20; dismiss keeps reason, re-dismiss 409 AI_ATTENTION_NOT_OPEN, dismissed blocker never re-raises; ai.attention_item_raised/cleared outbox; catalog 49 |
| 05.06 | aiProductionAgentsCoverage | PASS | local (CI pending) | ops/revenue/patient_comms seeded; ops proposes task -> approve -> created; revenue REVENUE ok / OTHER 400 AI_AGENT_ACTION_NOT_ALLOWED; patient_comms PATIENT_COMMS ok; disabled revenue 409 AI_AGENT_DISABLED; unknown agent 400 AI_AGENT_NOT_FOUND |
| 05.08 | demo-05.08-chunk5-exit.ps1 | LIVE 24/24 | - | SECTION REVIEW | cited count; ops proposal -> queue -> approve -> task; rail blocker/OT delay, approved proposal cleared; trace tokens/cost/redaction/prompt version; eval PASS ops@0; incident open/close; cap 409; kill switch 0.2s; scribe leg OWED (05.05 exp:E8) |
