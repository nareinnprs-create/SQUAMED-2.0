# CHUNK 1 — SECTION GRAPH (OPD & front office)
Ledger source for wave 2. Est. in **agent-plan weeks** (`13-AI-BUILD-WORKFORCE.md` §7, ~6 wk total). Contract: `chunk-01-opd-front-office.md`. Each section passes the 12 gates before the next lane step.

| # | Section | Contents | Depends on | Est. | Notes |
|---|---|---|---|---|---|
| 01.01 ⛔ | Scheduling engine | Doctor schedule templates (session-based), resources (doctor+room+equipment binding), slot generation, booking, waitlist, overbooking rules (config), no-show tracking, teleconsult slot type (used chunk 6) | chunk 0 exit | 1.0 | |
| 01.02 ⛔ | Queue & token engine | Token issuance per dept/doctor, priority classes (emergency/VIP/assisted/appointment-order) config-driven, transfer, re-print, estimated wait, QR queue ID, emergency bump <2 s to display | chunk 0 exit | 1.0 | Parallel with 01.01 |
| 01.03 | Reception workspace | Registration fast-path (chunk-0 EMPI), lookup, check-in, visitor/attendant pass, consult-fee receipts, cancel/reschedule, patient calling | 01.01, 01.02 | 1.0 | |
| 01.04 ⛔ | OPD EMR core | Encounter lifecycle, SOAP + specialty template notes, vitals capture, coded diagnosis (ICD-10/SNOMED), procedures, orders → tracked tasks (fulfilled chunk 2), **ePrescription** with allergy BLOCK/WARN, referral, follow-up booking, note signature (eSign + PIN fallback, immutable) | chunk 0 exit | 1.5 | Parallel lane; dual review (clinical write) |
| 01.05 | Patient 360 v1 | Demographics, allergies, conditions, encounters, meds, vitals trend, documents, consents, tasks; persistent context bar | 01.04 | 1.0 | |
| 01.06 | TV display app | Current/next token per counter/room, dept header, offline cache + reconnect banner; **hardware soak 24 h flaky network** | 01.02 | 0.5 | ⛔ external: Android TV hardware lab |
| 01.07 | Voice announcement engine | TTS (en/hi + regional), repeat/priority rules, audio zone mapping | 01.02 | 0.5 | Regional language #1 = pilot decision |
| 01.08 | Kiosk-lite | ABHA/mobile/QR check-in, thermal token print, dept search, wayfinding text; 50 check-ins/hr soak | 01.02 | 0.5 | ⛔ external: thermal printer |
| 01.09 | Emergency fast-path + downtime pack v1 | Triage-lite tags boost queue priority; printed reception/queue fallback + token/check-in re-entry tool | 01.03 | 0.5 | Downtime drill in exit week |
| 01.10 | Entitlement & tenant brand live | Feature gates read entitlement service (reseller plans hide modules); per-tenant DLT header/WABA brand; branded receipts; eSign cert per hospital entity | chunk 0 exit | 0.5 | Whitelabel §14 verification |
| 01.11 | Analytics mini + shadow-run prep | OPD counts, wait times, no-show rate; pilot shadow-run checklist | 01.03, 01.04 | 0.5 | |
| 01.12 ⛔ | **Chunk exit** | Live exit demo (chunk file), TV 24 h soak, kiosk soak, downtime drill, dual review (clinical writes), `reports/chunk-01/FINAL.md` | all | 0.5 | |

**Lanes (≈6 wk):** (01.01→01.03→01.09) · (01.02→01.06/07/08) · (01.04→01.05) · (01.10, 01.11) · exit last. Raw sum 9.0 wk over 4 lanes.
