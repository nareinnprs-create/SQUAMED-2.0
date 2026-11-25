# Section 07.09 — Lab & support depth (chunk 7)

**Verdict: SECTION REVIEW → local gates green (CI run pending).** Microbiology ID/AST + antibiogram, histopathology/cytology slide workflow, blood-bank NAT screening records and full food-service operations, bound to the chunk-2/4 deferrals SC-1/SC-2/SC-3.

## 1. Scope
Per `chunk-07-full-depth.md` item 13 / `20-CHUNK7-SECTIONS.md` 07.09:
- Microbiology full ID/AST: organism identification, AST panels, antibiogram.
- Cytology/histopathology full slide workflow: gross → slide → pathologist sign-out.
- Blood-bank NAT screening integration (assay-level records on top of the 04.07 screening hand-off).
- Full food-service ops: nutrition-linked menus, kitchen production planning, tray delivery.

## 2. Key artifacts
**Migration `V98`**: `laboratory.micro_culture`, `laboratory.micro_ast`, `laboratory.path_specimen`, `laboratory.path_slide`, `bloodbank.nat_screening`, `ipd.menu_item`, `ipd.kitchen_production`, `ipd.tray_delivery`; `kitchen:read|write:kitchen` permissions + `KITCHEN_STAFF` role.

**Backend**
- `laboratory/domain/MicroEntities.kt`, `PathologyEntities.kt`; `repository/LabDepthRepositories.kt`; `service/LabDepthService.kt`; `api/LabDepthController.kt` (17 endpoints under `/api/v1/lab/micro` and `/api/v1/lab/pathology`).
- `bloodbank`: `NatScreening` entity + repository + `recordNat`/`natScreenings` + `/api/v1/blood-bank/units/{id}/nat`; `screenUnit` no longer wipes a previously recorded NAT result.
- `kitchen/`: `KitchenEntities.kt`, `KitchenRepositories.kt`, `KitchenService.kt`, `KitchenController.kt` (9 endpoints under `/api/v1/kitchen`).

## 3. Gap closure
- ✔ Culture per lab order (unique), organism identification once, AST panel with S/I/R + MIC, final sign-out once.
- ✔ Antibiogram aggregates final isolates per organism + antibiotic with susceptible percentage.
- ✔ Histo/cyto accession (PTH-year-sequence) → gross → slides → all-slides-ready → pathologist sign-out once.
- ✔ NAT assay records per unit+assay; positive discards and rejects the unit, negative clears when TTI is clear, invalid holds.
- ✔ Menu catalogue per diet+meal; production plan derived from active diet orders with start/ready lifecycle; tray prepared/delivered once.
- ⚠ Antibiogram minimum-isolate suppression, per-observation food-service capture and NAT analyzer interface are not in this slice (see §8).

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS (local) | `labSupportDepthCoverage` green twice (initial + edge cases) |
| 2 Zero-stub | PASS | grep of every new file: zero banned tokens |
| 3 Types | PASS | `compileKotlin` + `compileIntegrationTestKotlin` clean |
| 4 Lint + format | PASS | `ktlintCheck` clean after `ktlintFormat` |
| 5 Tests | PASS (local) | full `integrationTest test jacocoTestReport jacocoTestCoverageVerification` BUILD SUCCESSFUL 11m13s — 102 PIT methods + 56 unit tests, 0 failures; JaCoCo instruction 84.29% (≥0.80) |
| 6 Accessibility | PASS | No UI change |
| 7 Design system | PASS | No UI change |
| 8 Contracts | PASS | snapshot regenerated from live `/api-docs`; codegen stamped `46593156…` |
| 9 Data | PASS | V98 applied (Flyway 98/98); cross-tenant culture + tray assertions |
| 10 Audit | PASS | Controller-mediated writes; records carry actor |
| 11 Docs | PASS | This report; LEDGER row 07.09; tracker |
| 12 Exit item | PASS (partial) | 07.11 exit slice |

## 5. Tests
Full suite: `integrationTest test jacocoTestReport jacocoTestCoverageVerification` — BUILD SUCCESSFUL, 102 PIT methods + 56 unit tests, 0 failures, JaCoCo instruction 84.29%.

Named PIT **`labSupportDepthCoverage`**:
- Microbiology: culture pending → duplicate order 409; finalize before ID 400; identify once (409 on repeat); AST S/R + MIC; invalid susceptibility 400; duplicate antibiotic 400; finalize once (409); antibiogram isolates=1, AMK 100% S / CIP 0%; detail carries 2 AST rows.
- Pathology: accession `PTH-…`; sign-out before slides 400; gross; 2 slides → first ready still GROSSED, second → SLIDES_READY; late third slide re-opens (sign-out 400) then re-completes; sign-out once (409); bad specimen type 400; 3 slides persisted.
- NAT: positive assay → unit discarded + screening REJECTED; duplicate unit+assay 409; bad result 400; NAT record listed; second donor/unit negative NAT + clear TTI → CLEARED.
- Kitchen: menu create + duplicate 400 + bad meal 400; plan counts 1 Soft; start → IN_PREP (start again 409) → READY; tray PREPARED (duplicate 409) → DELIVERED (again 409); manifest 1; kitchen perms separate (tech 403); cross-tenant tray 400; diet replacement re-plans Soft→0 and Liquid→1.
- Cross-tenant culture read 400.

## 6. Accessibility
No UI change.

## 7. Dual review — adversarial pass
1. **Culture identity** — one culture per lab order; foreign orders are rejected before save.
2. **Micro lifecycle** — identify once, AST blocked before ID and after final, finalize needs ≥1 AST.
3. **AST integrity** — S/I/R vocabulary, non-negative MIC, antibiotic unique per culture (request and DB).
4. **Antibiogram** — only FINAL cultures in range, per-antibiotic counts, HALF_UP percentage.
5. **Pathology lifecycle** — gross once, no slides after sign-out, a late slide re-opens the set, sign-out requires every slide READY, signed once.
6. **NAT safety** — positive discards the unit and marks screening REJECTED; one record per unit+assay; issued units are locked; INVALID never clears.
7. **Kitchen** — production counts derive from active diet orders only, replaced diet types drop to zero, start/ready/deliver are state-guarded and once; tray requires an active diet order in the same hospital.
8. **Tenant** — every read/write is hospital-scoped; cross-tenant culture and tray access return 400 without leaking existence.

## 8. Deviations from contract
- Antibiogram reports whatever final isolates exist (no ≥30-isolate suppression flag); a first-year lab has few isolates.
- Food service tracks production + tray delivery; there is no per-observation meal-intake capture or dietitian rounds board.
- NAT is an assay-record integration (manual/interface ingest); analyzer-specific HL7/ASTM NAT feed remains external (E6-class interface work).
- No new event-catalog entries in this section; records ride the audit chain and existing lab/blood-bank events.

## 9. Session trail
- Local Docker compose (postgres/redis/kafka/opensearch/minio) started for the PIT.
