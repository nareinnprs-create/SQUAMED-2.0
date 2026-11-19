# Section 02.06 — Pharmacy stock + dispensing (chunk 2)

**Gate-run assessment of the CURRENT tree** @ PR #68 (merged baseline + wiring PRs #2–#11 + 02.01/02.02 via #66 + V64 pharmacy stock governance). Verdict after verification: **COMPLETE → GATE-12 PASS**. CI evidence: **run 44538984201 — all 7 gates green** (zero-stub, parity, source-tree, backend ktlint+test+ArchUnit, integration compose+PIT+coverage, frontend, a11y/perf). Named PIT `pharmacyFefoAndLedgerCoverage` green in the same run.

## 1. Scope
Per `15-CHUNK2-SECTIONS.md`: drug master activation, formulary, indents, dispensing (partial, substitution audited), batch/expiry, FEFO (BLOCK near-expiry), stock ledger (trigger-enforced balance), min-level alerts, controlled register, returns/wastage. Depends on chunk 1 exit. Parallel with 02.01. CS-04 (high-alert medication, second authorized user) binds to this section's pharmacist leg + 04.02 nurse leg; second-authorizer **role verification** deferred to 02.09 chunk exit.

## 2. Key artifacts
**Migration `V64__pharmacy_stock_governance.sql`** (additive; rollback note inside):
- `pharmacy.stock_ledger` — append-only movement log (`INTAKE | DISPENSE | RESTOCK | ADJUST | WASTAGE`), `quantity_delta` + `balance_after`; **Postgres trigger `trg_stock_ledger_balance_guard` (`pharmacy.fn_ledger_balance_guard`) rejects any ledger insert whose `balance_after` ≠ the referenced stock row's `quantity_on_hand`** — the trigger-enforced balance gate. Callers `saveAndFlush` the stock row before the ledger insert so the trigger validates the updated balance.
- `pharmacy.formulary_entry` — per-hospital approved-drug list, UNIQUE (hospital_id, drug_id), FK → `master_data.drug`.
- `pharmacy.indent` — store requisitions (`requested → received | cancelled`), quantity_requested/received.
- `pharmacy.controlled_register` — one row per controlled-drug dispense: batch, dispensed_by, `second_authorizer_id NOT NULL` (CS-04 pharmacist leg).
- `pharmacy.dispensation` += `stock_id`, `batch_number` (batch/expiry traceability snapshot), `substituted_drug_id/name`, `substitution_reason`.

**Service (`PharmacyService.kt`):**
- **FEFO pick** — dispensing by `drugId` without `stockId` selects the earliest-expiry active batch at the hospital covering the quantity (null-expiry batches sort last); wrong batch impossible. **`NEAR_EXPIRY_BLOCK`** — dispensing any batch at/within **30 days** of expiry is rejected, for both FEFO picks and explicit `stockId` picks.
- **Drug-master activation gate** — `addStock`, `updateStock`, `dispense`, `createIndent`, `addToFormulary` require the referenced `master_data.drug` to exist and be active (`DRUG_NOT_FOUND` / `DRUG_INACTIVE`).
- **Ledger row on every quantity mutation** — intake (`addStock`), dispense, return-restock (**restocks the dispensed batch via `stock_id`**, fixing the wrong-batch restock bug from the baseline), manual adjust on `PUT /stock/{id}` (ADJUST), indent receive, wastage.
- **Audited substitution** — `SUBSTITUTION_REASON_REQUIRED` when `substitutedDrugId/Name` present without a reason; reason recorded on the dispensation.
- **Controlled dispense (CS-04)** — `is_controlled` drug without `secondAuthorizerId` → `SECOND_AUTHORIZER_REQUIRED` (fires before near-expiry: authorization checks fail fast); with it, a register row records batch + both actor ids.
- **Wastage** — `POST /stock/{id}/wastage` shrinks stock with reason (WASTAGE ledger row); over-wastage rejected (`INSUFFICIENT_STOCK`).
- **Min-level alerts** — `GET /stock/alerts` evaluates active stock at/below `reorder_level` **on read** (deviation, §9).
- **Indents** — create (drug name resolved from master data) → receive adds stock through the ledger (`INTAKE`, reference_type=indent); non-requested indents not receivable.

**API (`PharmacyController.kt`)** — 10 new endpoints under `/api/v1/pharmacy`: `GET/POST/DELETE /formulary`, `GET/POST /indents` + `POST /indents/{id}/receive`, `GET /stock/ledger?stockId=`, `GET /stock/alerts`, `POST /stock/{id}/wastage`, `GET /controlled-register`. All reuse existing **V17** permission codes (`pharmacy:read:stock` / `pharmacy:write:stock` / `pharmacy:dispense:prescription`) — no new codes seeded. Audit aspect covers the new write endpoints (controller-intercepted).

**Contracts** — `docs/contracts/openapi-v1.json` regenerated from live `/api-docs` (25 new-path matches); `packages/api-client/src/generated/schema.d.ts` re-stamped (sha256 f1339caf…427a8d6). Event: `prescription.dispensed` unchanged.

## 3. Gap closure
- ✔ Drug master activation gate (stock/indent/dispense/formulary reference active drugs only)
- ✔ Formulary (per-hospital approved list; duplicate rejected; FK to master data)
- ✔ Indents (requisition → receive → ledger)
- ✔ Substitution audited (reason mandatory, recorded on dispensation)
- ✔ Batch/expiry traceability (stock_id + batch_number snapshotted on dispensation)
- ✔ FEFO with near-expiry BLOCK (30 days, both pick modes)
- ✔ Stock ledger trigger-enforced (balance_after == quantity_on_hand, `trg_stock_ledger_balance_guard`)
- ✔ Min-level alerts (reorder_level now evaluated)
- ✔ Controlled register (CS-04 pharmacist leg: second authorizer required + registered)
- ✔ Returns/wastage (restock targets dispensed batch; wastage ledger row)
- ✔ PIT 02.06: `pharmacyFefoAndLedgerCoverage` PASS

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | All named contents implemented; PIT walk: FEFO batch pick, NEAR_EXPIRY_BLOCK, second authorizer, substitution audit, ledger reconstruction, indent receive, alerts |
| 2 Zero-stub | PASS | CI run 44538984201 |
| 3 Types | PASS | CI run 44538984201 |
| 4 Lint + format | PASS | CI run 44538984201 (ktlintFormat produced no changes) |
| 5 Tests | PASS | CI run 44538984201; named tests below. CS-04 pharmacist leg asserted in PIT (register row + 409-style error code); nurse leg = 04.02, role verification = 02.09 |
| 6 Accessibility | PASS | CI run 44538984201 (axe 0 serious/critical). `/pharmacy` still not in SCAN_ROUTES — unchanged caveat |
| 7 Design system | PASS | CI run 44538984201 |
| 8 Contracts | PASS | OpenAPI snapshot regenerated + committed (same PR); codegen stamped; PIT `openApiSnapshotMatchesLiveContract` green in run 44538984201. Event schema-registry harness still 00.02 scope (unchanged repo-wide) |
| 9 Data | PASS | V64 additive with rollback note; ledger balance enforced by trigger; tenant pinning via `tenantResolver.pinOrDefault` + `requireSameHospital` on all cross-hospital reads/writes. RLS stage-2 remains repo-wide ADR-13 debt (not this section) |
| 10 Audit | PASS | AuditAspect intercepts all new write endpoints; ledger itself is an auditable history |
| 11 Docs | PASS | This report; LEDGER row 02.06 COMPLETE; tracker updated |
| 12 Exit item | PASS (partial) | FEFO exit-demo slice scripted at 02.09 chunk exit (per chunk file); this section supplies the PIT evidence |

## 5. Tests
Named PIT tests (green in run 44538984201): **`pharmacyFefoAndLedgerCoverage`** (new, 02.06): FEFO picks earliest-expiry batch (B2 +60d over B1 +120d) + ledger `DISPENSE` row balance-after 35; `NEAR_EXPIRY_BLOCK` error code; controlled dispense without second authorizer rejected (`SECOND_AUTHORIZER_REQUIRED`) → with authorizer: register row with both ids + batch; substitution without reason rejected / with reason recorded + batch snapshot; activation gate on stock intake + formulary add after master-data deactivate (`DRUG_INACTIVE`); formulary duplicate rejected + delete + name resolution; wastage 50→43 with ledger note "damaged vial" + over-wastage rejected; ledger deltas sum == on-hand (43) with INTAKE/DISPENSE/WASTAGE present; indent create/receive (+100 via ledger, drug name resolved); min-level alert row for drug C (3 ≤ 5). Regression legs unchanged and green in the same run: `clinicalEncounterAndPharmacyCoverage`, `fullPatientRevenueJourney`.

## 6. Accessibility
Repo-wide axe gate green (run 44538984201); `/pharmacy` outside scanned routes — no per-page axe evidence (unchanged from baseline; web surface untouched by this PR).

## 7. Issues found during build + fixes
- **Near-expiry vs controlled ordering**: first PIT draft expected `NEAR_EXPIRY_BLOCK` for a controlled drug's only covering batch but hit `SECOND_AUTHORIZER_REQUIRED` first. Fixed by design decision: authorization checks (second authorizer, substitution reason, activation) run before the stock pick; test probe moved to a non-controlled drug (D).
- **Kotlin smart-cast** on `dispensation.stockId` (mutable property) — hoisted to local `restockStockId`.

## 8. External blockers
None for this section's code (drug master data already lives in 00.06 `master_data.drug`; controlled-drug schedule list is regulatory input but `is_controlled` flag covers the register mechanics).

## 9. Deviations from chunk file
- **Min-level alerts evaluated on read** — no persisted alert rows / notification fan-out; smallest surface that makes `reorder_level` real. Notification integration stays with 00.12 engine if later wanted.
- **Second-authorizer role verification deferred** to 02.09 (CS-04 full scope: verify the authorizer holds an authorized role; here the id is mandatory + registered).
- **Baseline doc note stale**: section doc previously flagged `addStock` hardcoding hospital `…0001`; already fixed via `tenantResolver.pinOrDefault` (00.04 tenant sweep) before this PR — no fix needed.

## 10. Exit-demo slice
`pwsh scripts/phase1-e2e.ps1` walks `/api/v1/pharmacy` stock + dispensation endpoints. FEFO/ledger slice: PIT `pharmacyFefoAndLedgerCoverage` (run 44538984201); 02.09 chunk exit assembles the scripted slice.
