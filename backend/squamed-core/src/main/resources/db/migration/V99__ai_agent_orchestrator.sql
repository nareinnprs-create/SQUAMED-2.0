-- V99: six additional agent personas + orchestrator (chunk-07 section 07.01)
-- Agents only propose; every action still runs through the governed platform API on human approval.
-- Rollback note: additive only - delete the six seeded agent rows (clinical, pharmacy, facilities,
-- procurement, security, executive).

INSERT INTO ai.agent_config (id, code, name, description, system_prompt, model_id, temperature, enabled, scopes, created_at, updated_at, version)
VALUES
    ('00000000-0000-0000-0000-00000000d00a', 'clinical', 'SQUAMED Clinical Assistant',
     'Triages clinical blockers: unacknowledged critical results, overdue discharges and ward follow-ups (drafts only).',
     'You are SQUAMED Clinical Assistant. You only propose drafts for human approval; you never execute actions.',
     '00000000-0000-0000-0000-00000000d001', 0.2, TRUE, 'ipd:read:admission,laboratory:read:lab', NOW(), NOW(), 0),
    ('00000000-0000-0000-0000-00000000d00b', 'pharmacy', 'SQUAMED Pharmacy Assistant',
     'Watches stock levels, expiry and dispensing constraints and drafts replenishment follow-ups (drafts only).',
     'You are SQUAMED Pharmacy Assistant. You only propose drafts for human approval; you never execute actions.',
     '00000000-0000-0000-0000-00000000d001', 0.2, TRUE, 'pharmacy:read:stock', NOW(), NOW(), 0),
    ('00000000-0000-0000-0000-00000000d00c', 'facilities', 'SQUAMED Facilities Assistant',
     'Tracks CSSD tray availability, equipment downtime and open work orders and drafts resolution tasks (drafts only).',
     'You are SQUAMED Facilities Assistant. You only propose drafts for human approval; you never execute actions.',
     '00000000-0000-0000-0000-00000000d001', 0.2, TRUE, 'ops:read:ops', NOW(), NOW(), 0),
    ('00000000-0000-0000-0000-00000000d00d', 'procurement', 'SQUAMED Procurement Assistant',
     'Tracks pending requisitions, supply risk and vendor follow-ups and drafts escalation tasks (drafts only).',
     'You are SQUAMED Procurement Assistant. You only propose drafts for human approval; you never execute actions.',
     '00000000-0000-0000-0000-00000000d001', 0.2, TRUE, 'ops:read:ops', NOW(), NOW(), 0),
    ('00000000-0000-0000-0000-00000000d00e', 'security', 'SQUAMED Security Assistant',
     'Reviews the incident register and access anomalies and drafts investigation tasks (drafts only).',
     'You are SQUAMED Security Assistant. You only propose drafts for human approval; you never execute actions.',
     '00000000-0000-0000-0000-00000000d001', 0.2, TRUE, 'audit:read:audit', NOW(), NOW(), 0),
    ('00000000-0000-0000-0000-00000000d00f', 'executive', 'SQUAMED Executive Assistant',
     'Synthesizes cross-domain blockers into owner/impact/recommended-action answers for leadership (drafts only).',
     'You are SQUAMED Executive Assistant. You only propose drafts for human approval; you never execute actions.',
     '00000000-0000-0000-0000-00000000d001', 0.2, TRUE, 'ai:read:agent', NOW(), NOW(), 0)
ON CONFLICT (code) DO NOTHING;
