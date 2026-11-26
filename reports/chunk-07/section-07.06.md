# Section 07.06 — Full kiosk + wayfinding (chunk 7)

**Verdict: SECTION REVIEW → local gates green; EXTERNAL-BLOCKED item remains (E12 indoor-mapping vendor/format).** Payment kiosk, report-collection kiosk, multi-language messages and an indoor routing graph per campus.

## 1. Scope
Per `chunk-07-full-depth.md` item 6 / `20-CHUNK7-SECTIONS.md` 07.06: payment kiosk, report-collection kiosk, indoor navigation with live routing (map config per campus), multi-language.

## 2. Key artifacts
**Migration `V103`**: `kiosk.payment_receipt`, `kiosk.report_collection`, `kiosk.campus`, `kiosk.map_node`, `kiosk.map_edge`; `kiosk:read|write:kiosk`; grants to `KIOSK`, `SYSTEM_ADMIN`, `HOSPITAL_ADMIN`.

**Backend**
- `kiosk/domain/KioskEntities.kt`, `repository/KioskRepositories.kt`, `service/KioskExtrasService.kt`, `api/KioskFullController.kt` (9 endpoints under `/api/v1/kiosk`).
- `KioskService.lookupPatient` exposed for the new kiosk surfaces.
- Patient-scoped report queries added to `LabReportRepository` and `RadiologyReportRepository`.

## 3. Gap closure
- ✔ Payment kiosk: lookup outstanding invoices by ABHA/ABHA-address/mobile; partial/full payment through the governed billing service; receipt number; overpay and cross-patient guards.
- ✔ Report-collection kiosk: released lab reports + signed radiology reports per patient; collect-once with receipt; Hindi/English confirmation message.
- ✔ Indoor wayfinding: campus → nodes (entrance/corridor/department) → edges (distance, accessible, bidirectional); shortest-path routing with an accessibility filter; localized direction message.
- ✔ Multi-language: en + hi server messages for payment/report/direction.
- ⚠ Vendor indoor-map import (format/coordinates) remains external (E12); the graph is admin-defined today.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | `fullKioskWayfindingCoverage` green |
| 2 Zero-stub | PASS | new files grep clean; CI |
| 3 Types | PASS | `compileKotlin` + `compileIntegrationTestKotlin` |
| 4 Lint + format | PASS | `ktlintCheck` clean |
| 5 Tests | PASS (local) | full chain BUILD SUCCESSFUL 12m24s — 107 PIT methods + 56 unit tests, 0 failures; JaCoCo instruction 84.77% (>=0.80) |
| 6 Accessibility | PASS | No web UI change (kiosk client) |
| 7 Design system | PASS | No web UI change |
| 8 Contracts | PASS | snapshot regenerated from live `/api-docs`; codegen stamped `4343a0de…` |
| 9 Data | PASS | V103 additive; cross-tenant campus/route assertions |
| 10 Audit | PASS | Controller-mediated writes; receipts carry actor |
| 11 Docs | PASS | This report; LEDGER; tracker |
| 12 Exit item | PASS (partial) | 07.11 kiosk slice |

## 5. Tests
Full suite: `integrationTest test jacocoTestReport jacocoTestCoverageVerification` — BUILD SUCCESSFUL, 107 PIT methods + 56 unit tests, 0 failures, JaCoCo instruction 84.77%.

Named PIT **`fullKioskWayfindingCoverage`**:
- Payment lookup by mobile; partial card payment → `KP-` receipt, remaining due 150; overpay 400; another patient's invoice hidden (400).
- Seeded released lab report visible as uncollected; collect → `KR-` receipt + Hindi message; second collect 409.
- Campus + 3 nodes + 3 edges; step-free route takes the 40 m accessible edge; shortest route is 25 m via the corridor; Hindi direction; unknown node 400.
- Second hospital's kiosk sees no campuses and cannot route the foreign campus (400).

## 6. Accessibility
No web UI change; kiosk client accessibility is a device/hardware concern (E10). Routing exposes an `accessibleOnly` filter for step-free paths.

## 7. Dual review — adversarial pass
1. **Payment ownership** — a kiosk can only pay the identified patient's invoice; foreign invoice ids return the same not-found error.
2. **Amounts** — positive amount, never above the outstanding due; method restricted to CASH/CARD; execution goes through the governed billing service.
3. **Report ownership** — collection requires the report to belong to the identified patient and the kiosk hospital; collect-once.
4. **Routing** — Dijkstra over admin edges; accessibility filter; unknown/foreign campus rejected.
5. **Tenant** — every kiosk endpoint requires a hospital-bound principal; cross-tenant campuses are invisible.
6. **Receipts** — unique per hospital; max+1 numbering is a documented shortcut.
7. **No PHI leakage** — kiosk responses carry only the identified patient's own dues/reports; QR/identifier errors stay generic.

## 8. Deviations / blockers
- **E12 (external):** indoor-mapping vendor/format and campus coordinate import are not integrated; the graph is admin-defined (nodes/edges) and routes live.
- Payment is recorded by the platform; live PSP/card acquiring remains E4 (kiosk method is CASH/CARD recorded, not captured).
- Receipt printing/QR rendering stays client-side (thermal printer hardware E10).
- Report collection covers released lab reports and signed radiology reports; other document types are not included.
- Multi-language messages are en + hi at the API boundary; the kiosk UI uses the house i18n.

## 9. Session trail
- Local Docker compose restarted after an engine stop; fixed two Spring bean/entity name collisions (`Campus`, `MapNode`, `MapEdge`) by prefixing the kiosk types.
