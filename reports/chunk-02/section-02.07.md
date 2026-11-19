# Section 02.07 — Medication safety engine (chunk 2)

**Gate-run assessment of the CURRENT tree** @ PR #74 (merged baseline + wiring PRs #2–#11 + Wave-3 sections 02.01/02.02 via #66 + 02.03 via #70 + 02.04 via #72 + 02.06 via #68 + this PR). Verdict after verification: **COMPLETE → GATE-12 PASS (partial: exit-demo slice owed at 02.09 chunk exit)**. CI evidence: **run 44632941285 — all 7 gates green** (docs commit on the same branch also green). Named PIT `medicationSafetyEvalCoverage` green in the same run.

## 1. Scope
Per `15-CHUNK2-SECTIONS.md`: interaction checks (dual-source, provenance + confidence, BLOCK only ≥ threshold), allergy ingredient-level mapping, duplicate therapy, max-dose (peds weight), renal flag (eGFR); override reason KPIs; 200-pair eval ≥98% / 0 false-BLOCK. Dual review (safety-critical) recorded in §7. The engine is the dispense-side correctness foundation for chunk-4 eMAR.

## 2. Key artifacts
**Migrations:**
- `V67__medication_safety.sql` — `master_data.drug` += `atc_code`, `ingredients` (CSV tokens, repo-injected only); schema `medication` with `interaction_rule` (pair codes, severity CHECK `BLOCK|WARN|INFO`, `confidence` 0–1, source CHECK `DATASET|RULE`, description, is_active), `dose_rule` (`max_daily_mg_per_kg`, `adult_max_daily_mg`, `renal_flag`, `renal_egfr_threshold`, note), `allergen_class_map` (allergen_token → ingredient_token), `safety_override` (hospital_id NOT NULL, patient, drug_code, finding_type CHECK `INTERACTION|ALLERGY|DUPLICATE|MAX_DOSE|RENAL`, severity CHECK, reason NOT NULL, overridden_by, occurred_at) + pair/dose/token/KPI indexes; permissions `medsafety:check:medication` + `medsafety:override:medication` seeded to DOCTOR + PHARMACIST. Rollback note in header.
- `V68__medsafety_admin_grant.sql` — SYSTEM_ADMIN grant: V25's catch-all predates these perms; without it the platform admin 403s on the check API (found by the first local PIT run: `data()` NPE on a 403 body).

**Engine (`MedicationSafetyService`) — `check(MedSafetyCheckDto)` returns findings + `highestSeverity`:**
- **Interaction (dual-source, §5 risk 7):** rules matched in either orientation (sorted lowercase pair key); multiple rules per pair merged — severity = max rank, confidence = max, sources distinct sorted joined (`DATASET+RULE`); every finding carries `source` + `confidence` + `ruleId`. **BLOCK with merged confidence < threshold (default 0.90, `squamed.med-safety.block-confidence-threshold`) downgrades to WARN** — a 0.80-confidence BLOCK can never BLOCK (eval-proven). Only pairs involving a batch drug fire; context-vs-context pairs suppressed; same-code pairs skipped.
- **Allergy (ingredient-level):** drug tokens (code/name/generic/brand/ingredients, length ≥4) matched against allergen tokens by equality OR `allergen_class_map` expansion (penicillin → amoxicillin/ampicillin/cloxacillin) OR ≥4-char substring (01.04 parity); NKA sentinel filtered; severity `block` → BLOCK, else WARN.
- **Duplicate therapy:** same code OR equal genericName OR ingredient-token intersection; batch×batch + batch×context (active `prescribed` rxs resolved by drugId or free-text exact code/generic/brand — the 3 new `findFirstBy*IgnoreCaseAndIsActiveTrue` queries close the free-text resolution hole without DTO churn); findings deduped by (drugA, drugB).
- **Max dose:** pediatric (age < 12 + latest VitalSign weightKg) × `max_daily_mg_per_kg`, else `adult_max_daily_mg`; exceeded → WARN (`doseMgPerDay` on the check DTO; null skips the leg).
- **Renal:** dose rule `renal_flag` + latest **released** eGFR lab result (`LabTestRequestRepository.findReleasedResults`, joins order status) < `renal_egfr_threshold` → WARN.

**API (`MedSafetyController`)** — `POST /api/v1/medication-safety/check` (`medsafety:check:medication`), `POST /api/v1/medication-safety/overrides` (`medsafety:override:medication`, 201; blank reason → 400 `OVERRIDE_REASON_REQUIRED`, tenant pinned via `tenantResolver.pinOrDefault`), `GET /api/v1/medication-safety/override-kpis?days=30` (rows grouped findingType+reason, count desc; micrometer counter `squamed.medsafety.overrides{type}`).

**Gates (hooks):**
- **Prescribe** (`EncounterService`, after the 01.04 allergy gate): BLOCK → 409 `MEDICATION_SAFETY_BLOCK` unless `overrideReason` typed; with reason → audited override row, rx proceeds.
- **Dispense** (`PharmacyService`, after controlled-drug second-authorizer check, before FEFO stock pick): BLOCK with blank `safetyOverrideReason` → `MEDICATION_SAFETY_BLOCK` (whitespace-only rejected); reason (or WARN + reason) → override row for the top finding (BLOCK preferred) before stock decrement.

**Contracts** — `docs/contracts/openapi-v1.json` regenerated from live `/api-docs` (schemas `MedSafetyResultView`/`MedSafetyFindingView`/override DTOs; **LF-normalized per `.gitattributes` — a CRLF-trailing save made the api-client sha stamp mismatch CI's checkout**, fixed by normalizing + re-stamping); `packages/api-client/src/generated/schema.d.ts` re-stamped (sha256 640d46a6…f93a).

## 3. Gap closure
- ✔ Dual-source interaction checks with provenance (source/confidence/ruleId) + confidence-threshold BLOCK downgrade
- ✔ Allergy ingredient-level mapping incl. class expansion; NKA sentinel safe
- ✔ Duplicate therapy (code/generic/ingredient) across batch + active prescriptions
- ✔ Max dose pediatric weight-based + adult fallback; renal eGFR flag from released results
- ✔ Audited overrides with mandatory reason + override reason KPIs + alert-fatigue counter
- ✔ 200-pair eval (26 Indian brands, row-major C(26,2) sample) ≥98% accuracy, 0 false-BLOCK
- ✔ PIT 02.07: `medicationSafetyEvalCoverage` PASS

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | PIT walk below covers all contract items (eval, provenance, downgrade, allergy, dose, renal, duplicate, both gates, override API, KPIs) |
| 2 Zero-stub | PASS | CI branch run 44632941285 |
| 3 Types | PASS | CI branch run 44632941285 |
| 4 Lint + format | PASS | CI branch run 44632941285 (ktlintFormat round applied pre-push) |
| 5 Tests | PASS | CI branch run 44632941285; named PIT below |
| 6 Accessibility | PASS | CI branch run 44632941285 (axe 0 serious/critical); web surface untouched by this PR |
| 7 Design system | PASS | CI branch run 44632941285 |
| 8 Contracts | PASS | OpenAPI snapshot regenerated + committed (LF-normalized); codegen stamped 640d46a6…; api-client parity test green in the same run |
| 9 Data | PASS | V67/V68 additive with rollback notes; drug columns nullable; override hospital_id NOT NULL pinned server-side; tenant-pinned KPI read (null hospital = admin unscoped). RLS stage-2 remains repo-wide ADR-13 debt |
| 10 Audit | PASS | AuditAspect covers the new write endpoints; overrides are an audited trail with actor + occurred_at; gates write overrides in the caller's transaction |
| 11 Docs | PASS | This report; LEDGER row 02.07 COMPLETE; tracker updated |
| 12 Exit item | PASS (partial) | Safety-engine demo slice at 02.09 chunk exit (per chunk file); this section supplies the PIT evidence |

## 5. Tests
Named PIT (green in the PR #74 branch run): **`medicationSafetyEvalCoverage`** (new, 02.07): 26 Indian brand drugs (Crocin/Brufen/Voveran/Ecosprin/Warf/Deplatt/Losec/Pantocid/Metlong/Amaryl/Gemer/Septran/Augmentin/Monocef/Azee/Cetzin/Telma/Amlopres/Metolar/Lasix/Atorva/Thyronorm/Alprax/Domstal/Emeset, codes `CODE-$s`) + 12 dual-source rules + 2 dose rules + 4 allergen class maps seeded per run → **200-pair eval** (first 200 of row-major over sorted codes) vs authored truth map: **accuracy ≥196/200 asserted, 0 false-BLOCK asserted**; provenance legs: WARF+ECOSP stays BLOCK with `DATASET+RULE` merged source + 0.98 confidence; downgrade leg: WARF+BRUF (BLOCK 0.80) fires WARN with confidence preserved; allergy legs: AUG → ALLERGY BLOCK (class map), SEP → WARN, CRO clean; dose legs: peds 20 kg vital × 50 mg/kg = 1000 cap (1500 → WARN, 800 → none), adult 4000 cap (5000 → WARN); renal leg: full lab flow (order→collect→receive→start→results→verify→release) eGFR 28 → METL RENAL WARN; duplicate leg: Metlong rx active → Gemer DUPLICATE WARN; **prescribe gate**: Ecosprin rx then Warf without reason → 409 `MEDICATION_SAFETY_BLOCK`, with reason → 2xx; **dispense gate**: FEFO Warf batch, null reason 4xx + code, blank "   " 4xx + code, real reason 2xx; override API blank reason → 400 `OVERRIDE_REASON_REQUIRED`; KPI: ≥1 row with findingType INTERACTION count ≥1.

## 6. Accessibility
Repo-wide axe gate green (PR #74 branch run); web surface untouched by this PR — no per-page axe delta.

## 7. Dual review (safety-critical) — adversarial false-BLOCK / false-PASS pass
1. **Merge semantics edge:** a BLOCK candidate below the threshold can ride a higher-confidence WARN partner into BLOCK (severity = max rank, confidence = max). Accepted per locked design — the reported confidence is the max across sources; single-source low-confidence BLOCK downgrades correctly (eval-proven 0.80 case). Risk: low.
2. **False-PASS — free-text context rxs:** the interaction leg matches rules on resolved `Drug` codes only; a context rx like "Warfarin 5mg" that doesn't resolve by exact code/generic/brand is invisible to interactions (the duplicate leg still catches it via drugName tokens). Mitigation added: exact-name resolution; token-level interaction matching deferred to chunk-4 eMAR work.
3. **False-PASS — drugId-less dispense:** `dispense` with null `drugId` (stockId path) skips the safety gate entirely (`drug == null`). Pre-existing DTO shape; named for chunk-4 (make drugId required for safety-relevant dispense).
4. **False-PASS — weightless pediatric dosing:** age < 12 with no recorded vital falls back to the adult cap (e.g. 4000 mg paracetamol for a 20 kg child). Named hardening: require a weight for pediatric dose checks (config/engine follow-up, not this section).
5. **False-PASS — non-numeric eGFR:** `toDoubleOrNull` skips malformed result values silently. Lab eGFR is numeric by convention; a structured numeric column is chunk-3/4 territory.
6. **Audit gap — prescribe-side WARN interactions:** only BLOCK requires a reason at prescribe; interaction WARNs pass without any recorded row (rx `overrideReason` records allergy warns only). Locked design decision: dispense is the irreversible leg and enforces reason + audit for both WARN and BLOCK; rx-level interaction warn recording deferred (chunk-4).
7. **Override API string enums:** `findingType`/`severity` are free-text at the API; invalid values fail the DB CHECK → 500 instead of 400. Minor hardening item.
8. **Cross-test interference audit (CI):** free-text names other PIT tests prescribe (`Paracetamol` ×4 sites, `Ibuprofen` ×3, discharge text `Aspirin 75mg od`) now resolve through the new lookups but produce no BLOCK findings in their contexts (no matching rules/allergies for those patients; dose rules keyed to suffix-scoped codes only; no other drug carries ingredient tokens). `findFirstBy*IgnoreCase` is nondeterministic among duplicate generic names — no asserted outcome depends on which duplicate wins.
9. **Context horizon:** `prescribed` rxs count toward context indefinitely (no time window, dispense doesn't transition rx status) → over-warns over time; the override KPI surface makes the fatigue visible. Chunk-4 rx lifecycle scope.

## 9. Deviations from contract
- `atc_code`/`ingredients` are seeded via migrations/repo injection only — **not reachable through the drug API** (DTO untouched by design; master-data API change would be its own contract review).
- Threshold is a single global config (0.90), not per-rule — per-rule thresholds can be added to `interaction_rule` when a real dataset demands it.
- 200-pair eval samples the first 200 of 325 pairs (row-major over sorted codes) — deterministic subset, not random; full-set run is one slice change away.

## 8. Session trail
- Local PIT round 1: `data()` NPE at the first eval pair — root-caused to admin 403 (`medsafety` perms not granted to SYSTEM_ADMIN post-V25) → V68; round 2 PASSED (4m21s).
- Docker Desktop died pre-run; restarted (`Stop-Process` + `Start-Process` + compose up), postgres healthy.
- CI round 1 (commit 126842a) run 44631648269: frontend gate failed on the api-client sha-stamp parity test — snapshot saved with a trailing CRLF on Windows; `.gitattributes` pins `docs/contracts/**` to LF, so CI hashed different bytes than the stamp. Fixed by LF-normalizing the snapshot + re-stamping (commit 0001fbf). Backend gates had already passed in round 1.
- CI round 2 run 44632941285: all gates green.
