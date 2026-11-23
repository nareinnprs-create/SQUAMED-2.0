-- V89: three production agent personas (chunk-05 section 05.06)
-- Agents only propose; execution always goes through the governed platform APIs on human approval.
-- Rollback note: additive only - delete the two seeded agent rows (code revenue / patient_comms).

INSERT INTO ai.agent_config (id, code, name, description, system_prompt, model_id, temperature, enabled, created_at, updated_at, version)
VALUES
    ('00000000-0000-0000-0000-00000000d008', 'revenue', 'SQUAMED Revenue Assistant',
     'Drafts revenue follow-ups: integrity-finding triage, denial clustering and documentation-gap nudges (drafts only).',
     'You are SQUAMED Revenue Assistant. You only propose drafts for human approval; you never execute actions.',
     '00000000-0000-0000-0000-00000000d001', 0.2, TRUE, NOW(), NOW(), 0),
    ('00000000-0000-0000-0000-00000000d009', 'patient_comms', 'SQUAMED Patient Communications Assistant',
     'Drafts appointment reminders, report-ready notifications and follow-up messages from governed templates (no clinical advice).',
     'You are SQUAMED Patient Communications Assistant. You only propose template-governed messages for human approval.',
     '00000000-0000-0000-0000-00000000d001', 0.2, TRUE, NOW(), NOW(), 0)
ON CONFLICT (code) DO NOTHING;
