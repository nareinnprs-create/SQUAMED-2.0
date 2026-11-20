# Section 03.01 — Charge capture ledger (chunk 3)

**Gate-run assessment of the CURRENT tree** @ PR #82 (chunks 0–2 merged + this PR). Verdict: **COMPLETE → GATE-12 PASS (partial: chunk-3 exit demo slice owed at 03.07)**. CI evidence: **run 44801191143 — all 7 gates green**. Named PIT `chargeCaptureLedgerCoverage` green in the same run.

## 1. Scope
Per `chunk-03-revenue-cycle.md` §1: unified charge ledger fed by events from chunks 1–2 (`ChargeablePerformed`, consults, procedures, room-time) + manual charge entry (audited, reason mandatory). Rate cards, tax, and packages are 03.02/03.03 scope.

## 2. Key artifacts
**Migration `V72__charge_capture_ledger.sql`** (additive; rollback note inside):
- `billing.charge` += `source_event_id` (partial unique index `uq_charge_source_event` — the consumer's idempotency key), `entry_mode` (`EVENT|MANUAL`), `entry_reason`; index `(hospital_id, posted_at)`.

**Event-driven capture:**
- `ChargeEventConsumer` — `@KafkaListener(topics = ["squamed.domain-events"], groupId = "billing-ledger")`; own consumer group so the ledger offset is independent of the notification bridge. Advances the `billing-ledger` cursor per processed event.
- `ChargeCaptureService.capture(event)` — `billing.chargeable_performed` → one ledger row: patient from payload, code/name from the performed tests, quantity summed, `category` from `chargeCategory` (LAB / IMAGING), `referenceType`/`referenceId` from the event entity, `sourceEventId` = event id, `entryMode=EVENT`, status `posted`.
- **Pricing:** active tariff row matching the performed code (hospital-scoped, null-safe); no tariff → 0. Rate cards with versioning arrive in 03.02.
- **Idempotency:** `ChargeRepository.findBySourceEventId` short-circuits replays; the partial unique index is the DB backstop.

**Manual entry:**
- `POST /api/v1/billing/charges` requires `reason` → 400 `CHARGE_REASON_REQUIRED`; the row records `entryMode=MANUAL`, `entryReason`, `postedBy`, `postedAt` (audit).

**Contracts:** OpenAPI snapshot regenerated (`PostChargeDto.reason`; `ChargeView.sourceEventId/entryMode/entryReason`), LF-normalized (sha256 `dfc99baf…`), codegen stamped.

## 3. Gap closure
- ✔ Event-fed ledger (lab + imaging `ChargeablePerformed` from 02.08/02.05)
- ✔ Idempotent consumption (source-event key + unique index)
- ✔ Manual entry audited with a mandatory reason
- ✔ Own consumer cursor (`billing-ledger`)
- ✔ PIT 03.01: `chargeCaptureLedgerCoverage` PASS

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | PIT covers event capture, pricing, idempotency, manual audit, cursor |
| 2 Zero-stub | PASS | CI branch run 44801191143 |
| 3 Types | PASS | CI branch run 44801191143 |
| 4 Lint + format | PASS | CI branch run 44801191143 |
| 5 Tests | PASS | CI branch run 44801191143; named PIT below |
| 6 Accessibility | PASS | CI branch run 44801191143 (web surface untouched) |
| 7 Design system | PASS | CI branch run 44801191143 |
| 8 Contracts | PASS | Snapshot + codegen committed; api-client parity green |
| 9 Data | PASS | V72 additive + rollback note; unique source-event index; tenant-scoped tariff lookup |
| 10 Audit | PASS | Manual rows carry poster + reason; event rows carry the source event id and the event envelope's actor/correlation |
| 11 Docs | PASS | This report; LEDGER row 03.01 COMPLETE; chunk-03 tracker created |
| 12 Exit item | PASS (partial) | Ledger slice of the 03.07 exit demo; this section supplies the PIT evidence |

## 5. Tests
Named PIT **`chargeCaptureLedgerCoverage`** (new): tariff rows for the performed lab code (75.0) and the imaging modality (250.0); lab order full flow → release → polled ledger row `EVENT`, `sourceEventId` set, code = performed test, category LAB, unit/total 75.0, status posted; imaging study completion → `IMAGING` row priced 250.0; replaying the same event through `ChargeCaptureService.capture` twice resolves to the same row and the patient still has exactly two charges; manual charge without reason → 400 `CHARGE_REASON_REQUIRED`, with reason → `MANUAL` + reason + poster and no source event; `billing-ledger` cursor present.

Regressions green: `financialAndOperationsCoverage`, `fullPatientRevenueJourney` (manual-charge sites updated with reasons), `chargeEventsAndInventoryCoverage`.

## 6. Accessibility
Web surface untouched by this PR; repo-wide axe gate in CI.

## 7. Dual review — adversarial pass
1. **Idempotency window:** a crash between `save` and the cursor advance replays the event → `findBySourceEventId` returns the existing row, no duplicate. The unique index covers the concurrent-consumer case.
2. **Event without patientId:** the consumer logs and skips (no orphan charges). Emitters always carry patientId today.
3. **Pricing drift:** a tariff edited after capture does not reprice existing rows (capture-time price) — correct ledger semantics; rate-card versioning is 03.02.
4. **Zero-price rows:** no tariff → price 0, status posted — visible in the ledger for later pricing/integrity rules (03.05) rather than silently dropped.
5. **Manual reason:** enforced server-side; whitespace-only rejected (trim).
6. **Consumer group isolation:** a slow/failed ledger consumer does not block notifications (separate group/offsets); a poison message is logged and skipped, not retried forever.
7. **Cross-test interference:** charges are patient-scoped and the PIT asserts counts on its own patient only; other tests' events create their own rows.

## 9. Deviations from contract
- Consult/procedure/room-time event capture arrives with their emitters (chunk 4/6); 03.01 ships the consumer + the lab/imaging legs that exist today.
- No tax lines (03.03) and no rate-card versioning (03.02); event lines are priced from the baseline tariff table.
- `ChargeablePerformed` is the only consumed event type in this section.

## 8. Session trail
- Docker Desktop died again (postgres exited 255); restarted compose, healthy.
- PIT round 1 blocked by a postgres still-starting context failure; re-ran healthy, PASSED.
- `JsonNode.size` vs `size()` compile error fixed.
