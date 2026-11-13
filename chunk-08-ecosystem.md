# CHUNK 08 — ECOSYSTEM & PLATFORM PRODUCTIZATION
**Status:** PLANNED · **Window:** Feb → Jun 2029 (16 wk) · **Depends on:** chunk 07 exit
**Purpose:** Blueprint phases 109–110 (§78–80, §72, §75) — the platform opens to third parties and new markets. Product complete before this chunk starts; this chunk makes it a platform business.

## IN SCOPE
1. **Developer platform (§78):** developer portal, API docs (OpenAPI + FHIR), API keys/OAuth clients, webhooks, SDKs, sandbox (chunk-6 synthetic hospital), app registration, rate limiting, API analytics, versioning policy, certification process.
2. **Agent SDK (§79):** third parties build governed agents — identity, scoped permissions, tool registry, knowledge, memory, policies, event subscriptions, audit, eval requirements. All §8 contract rules enforced by platform, not by trust.
3. **Marketplace (§80):** listings for AI agents, specialty packages, device connectors, payment/insurance integrations, analytics packs, country modules; review + certification pipeline; revenue-share/billing through licensing service; security review gate per listing.
4. **Deployment modes productized (§72):** Cloud / Private Cloud / Hybrid / On-Prem / Sovereign / Edge as supported, documented, DR-tested topologies (packaging of the compose/k8s assets hardened since chunk 6); license enforcement in on-prem/sovereign modes; **sovereign/on-prem = fully local AI via custom providers** (§8 rule 8) — zero external model calls, verified by network egress audit.
5. **Second country pack (§75):** regulation model proven with market #2 (Country → Regulation → Rule → Workflow → Evidence): tax adapter, claim adapters, ID scheme, drug master, localization. Market selection = business decision at chunk-8 kickoff.
6. **Group consolidation finance:** multi-entity GL consolidation, inter-hospital transactions, group procurement/billing at scale.
7. **Multi-currency + FX** (required by international patient desk + market #2).
8. **Imaging AI integration framework:** third-party imaging AI hookup via marketplace/DICOM routing (Squamed does not build imaging models).

## OUT OF SCOPE
Nothing further from the blueprint — §1–§117 all delivered by this chunk's exit.

## SAFETY / COMPLIANCE
- Marketplace certification: every listing passes security review, permission-scope audit, eval evidence, and clinical-safety review (clinical-adjacent listings require clinical-lead sign-off). Malicious/failed listings are revocable platform-wide in <1 h.
- Agent SDK: sandboxed tool access, cost caps, kill switch per third-party agent — same mechanics as first-party (§8), no exceptions.
- Sovereign/on-prem: audit WORM and DR capability must survive without cloud dependency — verified by drill on the on-prem topology.
- Country pack: regulatory sign-off checklist per §4 matrix structure before go-live.

## TESTS
- Third-party pilot: one external developer ships a working agent + one connector through SDK → marketplace → installed by test hospital, full audit trail visible.
- On-prem sovereign install on clean hardware: day-1 install runbook, DR drill passes, license enforcement verified.
- Country pack: full billing + claim round-trip under country #2 rules in sandbox.

## EXIT CRITERIA (demo scenario)
External developer registers → builds an agent on the SDK → passes certification → listed on marketplace → installed by a test hospital with governed permissions and audited actions → an on-prem sovereign deployment installs from the runbook and passes its DR drill → country-pack #2 runs a complete OPD→billing→claim flow in sandbox → group consolidation report spans 3 entities and 2 currencies.

## ROLLOUT / ROLLBACK
Marketplace in private beta (invite-only certification) → public. Country pack launches with design-partner hospital, not general availability.

## OPEN DECISIONS
- Market #2 selection (drives the entire regulatory pack).
- Marketplace commercial model (rev-share %, listing fees) — business work, start week 1.
