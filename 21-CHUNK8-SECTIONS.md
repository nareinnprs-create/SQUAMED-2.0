# CHUNK 8 — SECTION GRAPH (ecosystem & platform productization)
Ledger source for wave 9 — final. Est. in **agent-plan weeks** (~8 wk total). Contract: `chunk-08-ecosystem.md`. Product is complete before this wave starts; this wave makes it a platform business and closes blueprint §1–117.

| # | Section | Contents | Depends on | Est. | Notes |
|---|---|---|---|---|---|
| 08.01 ⛔ | Developer platform | Developer portal, API docs (OpenAPI + FHIR), API keys/OAuth clients, webhooks, SDKs, sandbox (= chunk-6 synthetic hospital), app registration, rate limiting, API analytics, versioning policy, certification pipeline | chunk 7 exit | 1.5 | |
| 08.02 ⛔ | Agent SDK | Third-party governed agents: identity, scoped permissions, tool registry, knowledge, memory, policies, event subscriptions, audit, eval requirements — **enforced by platform, not by trust**; sandboxed tool access, cost caps, kill switch per third-party agent | 08.01 | 1.5 | Dual review (security) |
| 08.03 | Marketplace | Listings (AI agents, specialty packages, device connectors, payment/insurance integrations, analytics packs, country modules), review + certification pipeline, security gate per listing, rev-share/billing via licensing service, **platform-wide revocation <1 h** | 08.01 | 1.0 | ⛔ external: commercial model (business, week 1) |
| 08.04 ⛔ | Deployment modes productized | Cloud / Private Cloud / Hybrid / On-Prem / Sovereign / Edge as documented, DR-tested topologies (packaging compose/k8s assets hardened since chunk 6); license enforcement on-prem/sovereign; **sovereign = fully local AI via custom providers only, zero external model calls, verified by network egress audit**; WORM audit + DR survive without cloud (drill-verified) | chunk 7 exit | 1.5 | Clean-hardware install drill |
| 08.05 | Country pack #2 | Regulation model proven (Country→Regulation→Rule→Workflow→Evidence): tax adapter, claim adapters, ID scheme, drug master, localization; full billing+claim round-trip in sandbox; regulatory sign-off checklist per §4 matrix | chunk 7 exit | 1.0 | ⛔ external: market #2 selection (your call, kickoff) |
| 08.06 | Group finance consolidation + FX | Multi-entity GL consolidation, inter-hospital transactions, group procurement/billing at scale, multi-currency + FX (international desk + market #2) | 07.04 | 1.0 | |
| 08.07 | Imaging AI integration framework | Third-party imaging AI via marketplace listings + DICOM routing (Squamed builds no imaging models) | 08.03 | 0.5 | |
| 08.08 ⛔ | **Chunk exit** | Third-party pilot (external dev ships agent + connector → certification → marketplace → installed by test hospital, full audit trail), on-prem sovereign install from runbook + DR drill, country-pack round-trip, 3-entity/2-currency consolidation report, **platform-complete audit vs blueprint §1–117**, dual review, `reports/FINAL.md` (grand report) | all | 0.5 | |

**Lanes (≈8 wk):** (08.01→08.02→08.03→08.07) · (08.04) · (08.05, 08.06) · exit last. Raw sum 8.5 wk over 3 lanes.
