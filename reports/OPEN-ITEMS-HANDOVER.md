# Open items — what is blocked, on what, and how to close it

Status as of 2026-11-28. Ledger totals: **66 COMPLETE / 1 IN PROGRESS / 13 SECTION REVIEW / 10 PLANNED** (90 sections).

Every open item below needs something this repository cannot produce by itself. Each entry states the
blocker, what the platform already provides, and the acceptance check to run when the input arrives.

---

## 1. 07.10 — the 22 clinically reviewed specialty pack contents
**Blocked on:** clinical review. Inventing clinical configuration would be fabrication.

**Already built:** `V108` pack schema and installer (`POST /api/v1/packs`, `POST /api/v1/packs/{code}/install`). A pack is `CONFIG` components (existing validated config types) plus `ROLE` grant sets. Installation is idempotent per (hospital, pack, version). A reference cardiology pack installs and is exercised by the chunk-7 exit demo.

**To close:** deliver 22 pack definitions in the existing request shape. Each `CONFIG` payload must validate against that type's JSON schema; each `ROLE` permission code must exist.

**Acceptance check:** `POST /api/v1/packs` for each pack → 200; `POST /api/v1/packs/{code}/install` → `INSTALLED`; re-install → `ALREADY_INSTALLED`; the hospital's config documents carry the pack values. Then update `reports/chunk-07/section-07.10.md` gate 1 and the ledger row to COMPLETE.

---

## 2. 07.11 — prescriptive-CDS counsel opinion
**Blocked on:** external legal opinion. The section map marks 07.11 ⛔ for exactly this.

**Already built:** the chunk-7 exit demo runs 32/32 live (`scripts/demo-07.11-chunk7-exit.ps1`), covering orchestration, capacity with five evidence factors, ED mass-casualty, GL close, migration + reconciliation, benchmarking, config-only pack install and the drill harness.

**To close:** obtain the opinion and record it in the 07.11 report §6; then re-issue the exit as COMPLETE.

**Acceptance check:** `pwsh scripts/demo-07.11-chunk7-exit.ps1` → 32 passed, 0 failed, and the two OWED lines removed.

---

## 3. 07.06 — E12 indoor-mapping vendor / format
**Blocked on:** the vendor's file format. The campus/map graph is admin-defined today.

**Already built:** campus, map-node and map-edge CRUD, kiosk wayfinding, QR/print paths.

**To close:** obtain one real vendor export; write the importer against it; keep the existing manual CRUD as the fallback.

**Acceptance check:** import the sample → nodes/edges created; re-import → idempotent (no duplicates); a kiosk route resolves across an imported edge.

---

## 4. 06.07 — ABDM data flows
**Blocked on:** ABDM sandbox credentials (and India EHR-M artefacts). No ABDM client or consent-artefact table exists.

**Already built:** outbound FHIR to a real HAPI peer with local profile validation, consent-gated documents, per-resource export ledger, and a peer-rejection record that carries the OperationOutcome.

**To close:** register for the ABDM sandbox; implement the gateway client and consent-artefact exchange on top of the existing outbound path.

**Acceptance check:** export a patient to the sandbox; ABDM consent artefact recorded; document export withheld without consent and released with it — mirroring `fhirOutboundCoverage`.

---

## 5. 05.05 — the provider half of the AI scribe
**Blocked on:** `exp:E8` — streaming STT / LLM provider contracts and a human-paced eval set.

**Already built:** 6-stage session machine, STT provider switch, consent gate (treatment/data_sharing, patient match, expiry), recording indicator, draft corrections captured as eval data, audio never persisted (so the 30-day purge has nothing to purge).

**To close:** pick the streaming provider, implement the streaming/diarization path, add en/hi/Hinglish, then the structured radiology/lab report drafting. Record the eval set as human-paced.

**Acceptance check:** a streamed session transcribes incrementally; diarization assigns speakers; a signed note keeps `ai_generated` provenance; corrections still land in `scribe.draft_correction`.

---

## 6. 06.09 — datacenter-kill DR drill
**Blocked on:** a second region. `docs/runbooks/dr-drill.md` describes the procedure.

**Already done:** exit demo 28/28; 1,500-user load run (0 errors, latency host-dominated so the NFR is unverified); **10k store-and-forward replay drill** (10,000 queued + replayed, 0 failures, idempotent, ~10.6 writes/s).

**To close:** stand up the second region, run the drill, measure RTO (target 4 h) and RPO (target 15 min).

**Acceptance check:** failover completes inside RTO; no acknowledged write is lost beyond RPO; the replay dashboard drains to zero.

---

## Also worth doing, not blocking anything
- Re-run the 1,500-user load on staging with the load client **off-box** and users fanned out across hospitals. The local numbers are host-dominated and cannot be quoted.
- Batch the store-and-forward replay: ~10.6 writes/s means a 10k backlog takes ~16 minutes.

---

## Moved since this list was first written (same day)

Three sections that were PLANNED are now **SECTION REVIEW** because their internal half needed nothing from outside:

| Section | Built | Still owed |
|---|---|---|
| 05.02 Eval framework | datasets, pass@k harness, enforced gates, adversarial leak suite, 500-sim agent harness | the 200 recorded consults (human-paced, `exp:E8`) |
| 01.10 Entitlement | plans, subscriptions, module gates, brand payload | per-tenant DLT/WABA sender (`exp:E2`), eSign certs (`exp:E5`) |
| 06.02 Telemedicine | RMP licence gate, first-consult-video, encounter documentation, support escape | video SDK (`exp:E9`), payment link (`exp:E4`) |

What remains genuinely unbuilt: **03.04** (payer claim formats, `exp:E4`/D6), **04.04** (device specs, `exp:E7`), and **chunk 8** (08.01-08.08), which the plan gates behind chunk-7 exit and whose own exit requires a third-party developer to ship an agent into a live marketplace.

Fixed while running the gates: `POST /api/v1/audit/verify` used to re-hash every chained audit row for every tenant in one request — at 209,905 rows across 2,364 chain heads it exceeded a 30 s client timeout, and it returned other tenants' chain keys to any `audit:read:audit` holder. It is now scoped to the caller's chain; the platform sweep is `?all=true` from a platform admin (or the nightly job). Still open for very large installations: make the full sweep incremental from each chain head's last verified sequence.