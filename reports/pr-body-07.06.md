## 07.06 — Full kiosk + wayfinding

Payment kiosk, report-collection kiosk, multi-language messages and an indoor routing graph per campus.

### Schema (`V103`, additive)
- `kiosk.payment_receipt`, `kiosk.report_collection`, `kiosk.campus`, `kiosk.map_node`, `kiosk.map_edge`.
- Permissions `kiosk:read|write:kiosk`; grants to `KIOSK`, `SYSTEM_ADMIN`, `HOSPITAL_ADMIN`.

### Backend (`/api/v1/kiosk`)
- **Payment:** lookup outstanding invoices by ABHA/ABHA-address/mobile; partial/full payment via the governed billing service; `KP-` receipt; overpay and cross-patient guards.
- **Reports:** released lab + signed radiology reports per patient; collect-once with `KR-` receipt; en/hi confirmation.
- **Wayfinding:** campus → nodes → edges (distance/accessible/bidirectional); Dijkstra shortest path with an accessibility filter; en/hi direction message.

### Evidence
- New PIT `fullKioskWayfindingCoverage`: payment lookup/partial pay/overpay 400/foreign invoice hidden; seeded lab report collect + 409 on repeat; step-free 40 m vs shortest 25 m route; Hindi direction; unknown node 400; cross-tenant campuses hidden.
- OpenAPI snapshot regenerated, codegen stamped; api-client 5/5.

### External / deviations
- **E12 remains open:** indoor-mapping vendor/format and coordinate import; the graph is admin-defined and routes live.
- PSP/card capture is E4; kiosk payment records CASH/CARD through billing.
- Receipt printing/QR is client-side (E10 hardware).
- Report collection covers released lab + signed radiology reports only.
- Multi-language en + hi at the API boundary.
