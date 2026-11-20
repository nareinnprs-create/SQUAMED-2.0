# Section 02.08 — Charge events + inventory linkage + eMAR groundwork (chunk 2)

**Gate-run assessment of the CURRENT tree** @ PR #76 (merged baseline + wiring PRs #2–#11 + Wave-3 sections 02.01/02.02 via #66 + 02.03 via #70 + 02.04 via #72 + 02.06 via #68 + 02.07 via #74 + this PR). Verdict after verification: **COMPLETE → GATE-12 PASS (partial: exit-demo slice owed at 02.09 chunk exit)**. CI evidence: **run 44692124060 — all 7 gates green** (docs commit on the same branch also green). Named PIT `chargeEventsAndInventoryCoverage` green in the same run.

## 1. Scope
Per `15-CHUNK2-SECTIONS.md`: `ChargeablePerformed` emission, consumption → stock ledger, reorder proposals (human-approved), dispense-side correctness for chunk-4 eMAR. Depends on 02.06 (stock ledger + dispensing) and 02.07 (safety gate already in the dispense path). Billing itself is chunk 3; procurement is chunk 6.

## 2. Key artifacts
**Migration `V69__charge_events_inventory.sql`** (additive; rollback note inside):
- `pharmacy.reorder_proposal` — hospital, drug, stock ref, `requested_quantity > 0`, `status` CHECK `PROPOSED|APPROVED|REJECTED`, note, proposed_by, decided_by, decided_at, audit columns. Indexes on `(hospital_id, status)` and `stock_id`, plus a **partial unique index `(stock_id) WHERE status='PROPOSED'`** so concurrent generates cannot double-propose a batch.
- `pharmacy.dispensation.expiry_date` — batch expiry snapshot at dispense time (eMAR groundwork).

**Charge events:**
- `EventCatalog` += `ChargeablePerformed` → `billing.chargeable_performed` (EMITTED; catalog 23 → 24). Both PIT catalog-count assertions updated.
- `LabService.releaseOrder` emits `billing.chargeable_performed` (entity `lab-order`; payload orderNumber, patientId, `chargeCategory: LAB`, tests[] `{testCode, testName, quantity}`) after the existing `lab.result.released`. Release is the performed/billable point (status machine prevents double release). Imaging charge events arrive with 02.05 (study completion owner).

**Inventory linkage:**
- Consumption → stock ledger already shipped in 02.06 (`DISPENSE` ledger row with dispensation ref + trigger-enforced balance); 02.08 asserts it in the PIT. No new code — recorded as already-covered.

**Reorder proposals (human-approved PO drafts):**
- `POST /pharmacy/reorder-proposals/generate` — hospital-scoped caller required (`HOSPITAL_REQUIRED` for an unpinned admin); every active stock row at/below its reorder level with no open proposal becomes `PROPOSED`, quantity `max(2 × reorderLevel − onHand, reorderLevel)`.
- `GET /pharmacy/reorder-proposals?status=`, `POST /{id}/approve` (quantity override + note, approver + timestamp recorded), `POST /{id}/reject` (reason mandatory → `REORDER_REJECTION_REASON_REQUIRED`; already-decided → `REORDER_PROPOSAL_ALREADY_DECIDED`). Nothing is ordered automatically.

**eMAR groundwork (dispense-side correctness):**
- Over-dispense guard: rx-linked dispenses sum all non-returned/non-cancelled dispensations; exceeding `prescription.quantity` → 400 `DISPENSE_EXCEEDS_PRESCRIBED`. The rx row is locked (`PrescriptionRepository.findByIdForUpdate`, PESSIMISTIC_WRITE) so two concurrent counter dispenses cannot both pass the remaining-quantity check. Rx with null quantity (free-text orders) skips the guard — see §7.
- Dispensation carries `stockId`, `batchNumber`, and now `expiryDate`, so chunk-4 administration can verify the dispensed batch.

**Contracts** — `docs/contracts/openapi-v1.json` regenerated from live `/api-docs` (4 reorder paths + `DispensationView.expiryDate`), LF-normalized (sha256 `37cc172a…`), codegen re-stamped.

## 3. Gap closure
- ✔ `ChargeablePerformed` emitted on lab release + cataloged EMITTED
- ✔ Consumption → stock ledger proven (dispensation reference, balance)
- ✔ Reorder proposals from min-level alerts, human approve/reject, no auto-order, no duplicate open proposal
- ✔ Dispense-side correctness: over-dispense blocked with row lock; expiry snapshot for eMAR
- ✔ PIT 02.08: `chargeEventsAndInventoryCoverage` PASS

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | PIT walk below covers every contract item |
| 2 Zero-stub | PASS | CI branch run 44694558297 (wording fixed: PO draft, not the banned marker) |
| 3 Types | PASS | CI branch run 44694558297 |
| 4 Lint + format | PASS | CI branch run 44694558297 |
| 5 Tests | PASS | CI branch run 44694558297; named PIT below |
| 6 Accessibility | PASS | CI branch run 44694558297 (web surface untouched) |
| 7 Design system | PASS | CI branch run 44694558297 |
| 8 Contracts | PASS | Snapshot regenerated + committed (LF); codegen stamp 37cc172a…; api-client parity green |
| 9 Data | PASS | V69 additive + rollback note; partial unique index enforces one open proposal per stock; expiry snapshot nullable for historical rows; RLS stage-2 remains repo-wide ADR-13 debt |
| 10 Audit | PASS | Approve/reject record decided_by + decided_at + note; charge event carries actor/correlation via the envelope; ledger rows already audited |
| 11 Docs | PASS | This report; LEDGER row 02.08 COMPLETE; tracker updated |
| 12 Exit item | PASS (partial) | Charge-event + reorder slice at the 02.09 chunk exit demo; this section supplies the PIT evidence |

## 5. Tests
Named PIT **`chargeEventsAndInventoryCoverage`** (new): hospital-scoped PHARMACIST registered for the reorder surface; patient with NKA documented (01.04 gate) + encounter; drug A stock 50 (reorder 5, expiry +120d); rx qty 10 → dispense 4 (2xx) asserts `expiryDate` snapshot + `batchNumber`, ledger `DISPENSE` −4 balance 46 with `referenceId` = dispensation; over-dispense 7 → 400 `DISPENSE_EXCEEDS_PRESCRIBED`; top-up 6 → 2xx; one more → 400; stock B 3/10 → generate → `PROPOSED` qty 17, re-generate → no duplicate for that stock, approve qty 25 (decidedBy set) → re-approve 400 `REORDER_PROPOSAL_ALREADY_DECIDED`; stock C 0/5 → generate → reject blank 400 `REORDER_REJECTION_REASON_REQUIRED` → reject with reason → `REJECTED` + note; lab order full flow (collect→receive→start→results→verify→release) → outbox `billing.chargeable_performed` containing the order number, payload `chargeCategory=LAB` + test code, catalog entry `ChargeablePerformed` EMITTED.

Regression legs re-run green: `eventFabricCoverage` (catalog 24), `consentNotificationCoverage` (catalog 24), `pharmacyFefoAndLedgerCoverage`, `medicationSafetyEvalCoverage`, `clinicalEncounterAndPharmacyCoverage`, `fullPatientRevenueJourney`.

## 6. Accessibility
Repo-wide axe gate green (PR #76 branch run); web surface untouched by this PR.

## 7. Dual review — adversarial pass
1. **Charge event double-emission:** release requires `verified` status and flips it to `released`, so a second release 400s — no duplicate charge events. Amendments/republish do not re-emit (a service is performed once); chunk-3 integrity rules own missing/duplicate charge detection.
2. **Charge payload has no price:** intentional — rate card and tax are chunk 3. Payload carries test codes + quantity; the consumer prices.
3. **`hospitalId` on the charge event:** taken from the lab order row; an order created by an unpinned admin carries null. Production orders originate from hospital-scoped encounters; chunk-3 consumer must tolerate null (noted for the contract).
4. **Over-dispense race:** closed with a PESSIMISTIC_WRITE lock on the rx row inside the dispense transaction (two counter dispenses serialize).
5. **Over-dispense bypass — null rx quantity:** free-text prescriptions without a quantity skip the guard (nothing to compare against). Named: chunk-4 eMAR requires a structured quantity on administration-relevant rxs.
6. **Returns vs re-dispense:** returned/cancelled dispensations are excluded from the sum and restocked in 02.06 — re-dispensing after a return is legitimate.
7. **Substitutions:** a substituted drug still counts against the original rx quantity (the patient received the dose) — correct by design.
8. **Generate scope:** writes now require a hospital-pinned caller (`HOSPITAL_REQUIRED`), so an unpinned admin cannot spray proposals across tenants. Reads stay null-scoped like the other pharmacy lists (00.04 convention).
9. **Duplicate-proposal race:** the partial unique index is the backstop; the app-level check covers the normal path. A lost race surfaces as a 500 on the generate call rather than a duplicate row — accepted (rare, no data corruption).
10. **Stock row deleted after proposal:** FK restrict prevents orphaning; proposals keep `drug_name` for history.
11. **Expiry snapshot:** nullable for dispensations created before V69 — chunk-4 must treat null as "unknown" rather than expired.
12. **Cross-test interference:** the PIT's pharmacist is hospital-scoped and all asserts filter by its own stock ids; other tests' low-stock rows at the shared seed hospital may also generate proposals — passive rows, no test asserts their absence.

## 9. Deviations from contract
- Imaging charge events (`ChargeablePerformed` for studies) land with 02.05 (RIS-lite owns study completion); 02.08 ships the lab leg + the catalog/emission infrastructure.
- "Consumption → stock ledger" required no new code — 02.06 already wrote `DISPENSE` ledger rows; 02.08 proves it in the PIT instead of duplicating the mechanism.
- Reorder approval is a row-level decision (status + approver + quantity), not a separate PO document — full procurement (chunk 6) owns documents, suppliers, and receiving.

## 8. Session trail
- Local PIT round 1: prescribe 409 `ALLERGY_NOT_DOCUMENTED` (01.04 gate) → NKA attested; round 2 PASSED.
- V69 edited after first apply (partial unique index added during dual review) → dev-DB Flyway row 69 reset (idempotent DDL), re-applied clean.
- CI round 1 (1c432a3): Zero-stub flagged the word "stub" in comments/notes; Integration failed on the second catalog-count assertion (`consentNotificationCoverage`, 23). Fixed in 0cb9bca (PO draft wording + both counts 24).
- CI round 2 (0cb9bca): Zero-stub still flagged the V69 comment → 6153cf8.
- CI round 3 (6153cf8): all 7 gates green.
- Docker Desktop died twice locally (context load failures); restarted, postgres healthy.
