## 07.09 — Lab & support depth

Binds the chunk-2/4 deferrals: microbiology ID/AST, histo/cyto slide workflow, blood-bank NAT screening, full food-service ops.

### Schema (`V98`, additive)
- `laboratory.micro_culture` (one per lab order, PENDING → ORGANISM_IDENTIFIED → FINAL) and `laboratory.micro_ast` (S/I/R + MIC, unique antibiotic per culture).
- `laboratory.path_specimen` (ACCESSIONED → GROSSED → SLIDES_READY → SIGNED_OUT) and `laboratory.path_slide` (unique slide number per specimen).
- `bloodbank.nat_screening` (one assay record per unit+assay).
- `ipd.menu_item`, `ipd.kitchen_production`, `ipd.tray_delivery`.
- Permissions `kitchen:read|write:kitchen`; role `KITCHEN_STAFF`.

### Backend
- **`/api/v1/lab/micro`:** culture create, identify-once, AST panel (S/I/R, MIC, duplicate guard), finalize-once (needs organism + AST), antibiogram aggregated over FINAL isolates with HALF_UP susceptible %.
- **`/api/v1/lab/pathology`:** accession (`PTH-year-sequence`), gross-once, slides, mark-ready (specimen completes only when every slide is ready; a late slide re-opens the set), pathologist sign-out-once.
- **`/api/v1/blood-bank/units/{id}/nat`:** assay-level NAT record; positive discards + REJECTED, negative clears with TTI, invalid holds, one record per unit+assay; `screenUnit` no longer wipes a prior NAT result.
- **`/api/v1/kitchen`:** nutrition-linked menu catalogue; production plan derived from active diet orders (replaced diet types drop to zero) with start/ready; tray prepared/delivered once.

### Evidence
- New PIT `labSupportDepthCoverage`: micro culture→ID→AST→final + antibiogram (AMK 100% S / CIP 0% R); duplicate/final-once guards; histo accession→gross→2 slides→late slide re-open→sign-out; NAT positive discards, negative+TTI clears, duplicate assay 409, bad result 400; kitchen menu/plan/start/ready/tray/deliver guards + re-plan after diet replacement; cross-tenant culture 400, tray 400, kitchen permission 403.
- OpenAPI snapshot regenerated (`46593156…`), codegen stamped; api-client 5/5.

### Deviations
- Antibiogram reports all final isolates (no ≥30-isolate suppression flag).
- Food service tracks production + tray delivery; no per-observation meal-intake capture.
- NAT is an assay-record integration; analyzer-specific HL7/ASTM NAT feed remains external interface work.
- No new event-catalog entries; records ride the audit chain and existing lab/blood-bank events.
