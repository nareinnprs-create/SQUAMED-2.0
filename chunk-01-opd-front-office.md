# CHUNK 01 — OPD & FRONT OFFICE
**Status:** PLANNED · **Window:** Mar → May 2027 (12 wk) · **Depends on:** chunk 00 exit
**Stack entering:** OpenSearch already in; TV/kiosk PWA targets; eSign integration
**Baseline (SC-6):** `master` prior build covers much of this chunk (scheduling, reception queue/visit registration, encounter EMR, Patient 360) — sections may enter gates on existing code after the chunk-0 baseline audit.

## GOAL
**A real OPD day runs end-to-end on Squamed** — the first moment the product touches live hospital operations.

## IN SCOPE
1. **Reception workspace:** registration (fast-path from chunk 0 EMPI), patient lookup, check-in, admission-initiation stub, visitor/attendant pass, receipts printing, cancel/reschedule, patient calling.
2. **Appointment & scheduling engine (full resource model — fixes blueprint gap #20):** doctor schedule templates (session-based), resources (doctor + room + equipment), slot generation, booking (counter/phone/online-stub), waitlist, overbooking rules (config), no-show tracking, teleconsult slot type (used in chunk 6).
3. **Queue & token engine:** token issuance per dept/doctor, priority classes (emergency, VIP, assisted, appointment-order) governed by config, queue transfer, re-print, estimated wait display, QR queue ID.
4. **TV display app:** current/next token per counter/room, department header, offline cache mode (shows last state + reconnect banner), browser-based on any Android TV/stick — **hardware soak test** in exit week.
5. **Voice announcement engine:** TTS (OS/browser free tier + cloud voice option), multilingual (en/hi + one regional), repeat & priority rules, audio zone mapping per display/counter, volume config.
6. **Kiosk-lite:** check-in by ABHA/mobile/QR, token print (thermal printer via browser print), department search, wayfinding text (floor/dept/room) — payment/report-collection kiosk + indoor navigation land in chunk 7.
7. **OPD consultation EMR:** encounter lifecycle (in-progress → signed), clinical note (SOAP + specialty template from config), vitals capture (nurse/paramedic pre-consult), diagnosis (ICD-10 + SNOMED, §3 enforcement), procedure entry, orders (lab/imaging/pharmacy — **fulfilled by chunk 2 stubs that create tasks**, real fulfillment comes in chunk 2), **ePrescription** (drug master from chunk 0, allergy check against patient record, print + patient copy), referral, follow-up booking, note signature (IT Act eSign path + fallback PIN signing with audit).
8. **Patient 360 v1:** demographics, allergies (structured), conditions, encounters, medications, vitals trend, documents, consents, tasks — persistent patient context bar across all screens.
9. **Emergency fast-path:** walk-in triage-lite tag (red/yellow/green) that boosts queue priority — full ED module is chunk 4.
10. **Downtime pack v1:** printed fallback for reception/queue + re-entry tool for tokens/checkins.
11. **Analytics mini:** today's OPD counts, wait times (KPI §9), no-show rate.

## OUT OF SCOPE
Real lab/imaging/pharmacy fulfillment (chunk 2 — orders create tracked tasks in this chunk), billing beyond receipt-of-consult-fee (chunk 3), IPD, AI.

## DATA MODEL (key additions)
`appointment, schedule_template, session_slot, resource(doctor/room/equipment binding), waitlist_entry, token, queue(class rules), encounter(OPD), clinical_note(versioned, signed), vitals_observation, diagnosis(coded), order(lab/imaging/pharmacy, status machine), prescription(+items), allergy, condition, referral, receipt(consult-fee), announcement_rule, display_zone, kiosk_session, downtime_log`

## APIS
- `/appointments` (book/cancel/reschedule/waitlist), `/schedules/{doctorId}/slots`
- `/queues/{deptId}/tokens`, `/tokens/{id}:call|transfer|complete`
- `/encounters`, `/encounters/{id}/note:sign`, `/encounters/{id}/orders`, `/prescriptions`
- `/patients/{id}/allergies|conditions|vitals`
- `/displays/{zone}:subscribe` (WS), `/announcements:enqueue`
- `/kiosk/checkin`

## EVENTS
`AppointmentCreated/Cancelled/Rescheduled, PatientCheckedIn, TokenIssued, TokenCalled, ConsultationStarted, EncounterSigned, OrderCreated, PrescriptionIssued, AllergyRecorded, CriticalQueueEvent(urgent arrival)`

## PERMISSIONS (adds)
Reception: booking/check-in/token ops. Nurse: vitals, queue call assist. Doctor: note sign, orders, eRx (own encounters only — ABAC on encounter owner). Auditor: read all.

## ADDITIONS (whitelabel, per master plan §14)
- **Entitlement enforcement live:** feature gates read the entitlement service — reseller plans can hide/disable modules from day one of external tenants.
- Notification layer resolves **per-tenant DLT headers/WhatsApp WABA brand** (templates + sender identity per operating entity).
- Receipts/reports render from tenant brand packs; eSign certificate per hospital entity.

## SAFETY (from §5)
- Two-identifier confirmation on every encounter-scoped write.
- Allergy documented before eRx enabled for that encounter (record may be "no known allergies" — explicit, audited).
- eRx blocked on `BLOCK`-severity allergy match; `WARN` requires typed reason.
- Note signature immutable after sign; amendments = new version with link (never overwrite legal record).
- Downtime drill (30 min paper mode) in exit week.

## TESTS
- Queue fairness: priority classes interleaving per config; emergency bump latency < 2 s to display.
- eSign: signature verification; unsigned note cannot generate bill line (chunk 3 dependency hook).
- Kiosk: 50 check-ins/hr soak; printer paper-out recovery.
- E2E demo scenario automated in `tests/e2e/`.

## EXIT CRITERIA (demo scenario, live at pilot-ready state)
Walk-in patient → registered in <90 s → booked appointment patient checks in at kiosk → tokens on TV with voice announcement in Hindi → nurse captures vitals → doctor consults with template note → eRx (one WARN override with reason, one BLOCK attempt rejected) → order for CBC created (lands as tracked task) → consult receipt printed → follow-up booked → Patient 360 shows the full thread → TV runs 24 h on flaky network without blank screen.

## ROLLOUT / ROLLBACK
Pilot department go-live (one dept, shadow-run 1 week vs paper parallel). Rollback = paper pack + re-entry of tokens/checkins only.

## OPEN DECISIONS
- Thermal printer model + QR queue ID physical card vs printed slip.
- Regional language #1 (depends on pilot hospital state).
