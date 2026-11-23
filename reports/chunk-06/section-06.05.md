# Section 06.05 — Ops modules (chunk 6)

**Verdict: COMPLETE → GATE-12 PASS (partial: partial receipts/stock lots and machine integration owed)**. Gate-run assessment of the CURRENT tree @ PR #113 (chunks 0–5 + 06.01/06.04 merged + this PR). CI evidence: **run 45282390759 — all 7 gates green**. Named PIT `opsModulesCoverage` green in the same run.

## 1. Scope
Per `chunk-06-enterprise-scale.md` §5 and `19-CHUNK6-SECTIONS.md` 06.05: procurement (indent→PO→receiving→GRN→stock, vendor performance), CSSD (trays, cycles, issue/return, OT traceability), facilities/assets (registry, PM, work orders, downtime), full ED (triage, resus, dashboard).

## 2. Key artifacts
**Migration `V92__ops_modules.sql`** (additive; rollback note inside): 12 `ops` tables, ops permissions, PROCUREMENT_OFFICER / CSSD_TECHNICIAN / FACILITIES_OFFICER roles.

**Backend:** `OpsEntities.kt`, `OpsRepositories.kt`, `OpsServices.kt` (procurement, CSSD, facilities, ED), `OpsController.kt` (19 endpoints), `EventCatalog.kt` (+6 events, catalog 60).

## 3. Gap closure
- ✔ Indent → submit → approve → PO → GRN → stock with state guards.
- ✔ Vendor performance (orders, received, total, average lead days).
- ✔ CSSD tray lifecycle + sterilization cycles + issue to an OT case + traceability + return.
- ✔ Asset registry + work orders + downtime.
- ✔ Full ED encounter with triage/resus/disposition + dashboard.
- ⚠ Partial receipts/back-orders and stock lots/expiry owed.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS (partial) | PIT covers all four sub-modules |
| 2 Zero-stub | PASS | CI branch run 45282390759 |
| 3 Types | PASS | CI branch run 45282390759 |
| 4 Lint + format | PASS | CI branch run 45282390759 |
| 5 Tests | PASS | CI branch run 45282390759; named PIT below |
| 6 Accessibility | PASS | No UI change |
| 7 Design system | PASS | No UI change |
| 8 Contracts | PASS | Snapshot `af1ccec2…` + codegen committed |
| 9 Data | PASS | V92 additive; unique tray/asset/stock keys |
| 10 Audit | PASS | Six ops events with actor; receipts carry receiver |
| 11 Docs | PASS | This report; LEDGER row 06.05 COMPLETE; tracker |
| 12 Exit item | PASS (partial) | Ops slice of the 06.09 exit demo |

## 5. Tests
Named PIT **`opsModulesCoverage`**: requisition 150 QAR → early order 409 → submit/approve → PO → GRN → stock 10 → double receive 409 → vendor performance; tray cycle → issue-in-cycle 409 → PASS → issue to an OT case → traceability (1 cycle, 1 issue) → return; asset → work order (asset UNDER_MAINTENANCE) → complete with downtime 50 → ACTIVE; ED ESI2 → RESUS → IN_TREATMENT → DISCHARGED → reopen 409 → dashboard discharged 1; procurement officer denied CSSD write; all six `ops.*` events.

Regression: `eventFabricCoverage` (catalog 60).

## 6. Accessibility
No UI change.

## 7. Dual review — adversarial pass
1. **State machines** — requisition (DRAFT→SUBMITTED→APPROVED→ORDERED), PO (ISSUED→RECEIVED), tray (AVAILABLE→IN_CYCLE→AVAILABLE→ISSUED→AVAILABLE), work order (OPEN→COMPLETED), ED (WAITING/RESUS→IN_TREATMENT→ADMITTED/DISCHARGED); invalid transitions are 409.
2. **Hospital scoping** — every lookup is hospital-scoped; roles split by sub-module (procurement cannot touch CSSD).
3. **Receipt integrity** — receipt items must be on the requisition; quantities positive; the PO closes on receipt, so stock cannot double-count.
4. **Stock** — unique per hospital+item; increments from receipts only (no manual adjustments in this pass).
5. **CSSD traceability** — cycles and issues are append-only rows; the tray state is the guard.
6. **Facilities** — an asset is marked UNDER_MAINTENANCE while a work order is open; downtime is recorded at completion.
7. **ED** — triage range enforced; ESI ≤2 defaults to RESUS; closure is final and emits the event.
8. **Event wire names** — `ops.*` prefix keeps the domain topic clean.

## 8. Deviations from contract
- All-or-nothing receipts (no partial/back-order), quantity-only stock (no lots/expiry).
- CSSD cycles are manual (no sterilizer integration); PM schedules are a `nextPmOn` date only (no scheduler).
- ED is the operational flow + dashboard; clinical decision support is out of scope.

## 9. Session trail
- The ops PIT needed an OT case for CSSD issue; consent + schedule were created through the existing chunk-4 APIs.
- Docker Desktop stayed healthy this run.
