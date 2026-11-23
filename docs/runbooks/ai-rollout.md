# AI rollout + pilot (chunk-5 go-live prep)

Every AI capability is behind a per-tenant flag (`ai.gateway_policy`) and starts **off** for
general users. Enable per doctor (opt-in), run a 2-week pilot cohort, then general enable.

## 1. Before the pilot

- Confirm the provider contract in writing: India-region/zero-retention terms for the default
  cloud provider, or a self-hosted/OpenAI-compatible endpoint (mandatory routing target).
- Seed the model with its per-1k-token price (the cost trace depends on it) and run the
  governance eval for the release subject (`agent@version`) - do not enable a persona without a
  PASS on its gold set.
- Set the tenant daily token cap and the per-capability flags; record the starting values.
- Confirm the kill-switch drill has been run in staging with a timed result < 60 s.

## 2. Enable order (one capability at a time)

1. **Command bar** (ask only) - citations must appear on every record-backed answer.
2. **Rail** - verify dismissal reasons are being captured and items do not re-raise.
3. **Action drafting + approvals** - every proposal must be approved by a human with the
   underlying permission; reject/expiry paths exercised.
4. **Agent personas** - ops first, then revenue, then patient-comms (template-scoped last).
5. **Scribe** - only after 05.05 closes its eval gate (`exp:E8`) and the consent flag is
   verified per encounter.

## 3. Pilot cohort (2 weeks)

- Opt-in list of doctors; one ward + one OT list.
- Daily: review the AI trace (model, prompt version, tokens, cost, redaction) for the cohort;
  review the approval queue for stale/expired drafts; review rail dismissals for noise.
- Weekly: re-run the capability eval on the current release subject; compare correction rate
  per doctor (scribe) and citation correctness (command bar).
- Any safety event goes to the incident register the same day; severity high/critical pauses the
  capability until closed.

## 4. Kill-switch drill (repeat before general enable)

1. Announce the drill; start the timer.
2. `PUT /api/v1/ai/governance/policies/{capability}` with `enabled=false` on a dummy capability.
3. Confirm other capabilities still answer; confirm the disabled one is refused
   (`409 AI_CAPABILITY_DISABLED`).
4. Re-enable; stop the timer. Target < 60 s end to end.

## 5. Rollback

- Flip the capability flag off (takes effect on the next request; no cache).
- Signed scribe notes and drafts persist as normal records; no AI data is deleted.
- Drafts that were never approved expire on their own; approvals already executed are ordinary
  platform records and are not rolled back.
- Provider outage: route to the self-hosted endpoint or disable the capability; the platform
  keeps working without AI.

## 6. Sign-off

| Capability | Flag owner | Eval subject | Pilot window | Kill-switch drill | Verdict |
|---|---|---|---|---|---|
| Command bar | - | - | - | - | Pending |
| Rail | - | - | - | - | Pending |
| Action approvals | - | - | - | - | Pending |
| Agent personas | - | - | - | - | Pending |
| Scribe | - | - | - | - | Pending (05.05 exp:E8) |
