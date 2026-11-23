# Section 04.06 — OT + anaesthesia safety (chunk 4)

**Gate-run assessment of the CURRENT tree** @ PR #97 (chunks 0–3 + 04.01-04.03/04.05 merged + this PR). Verdict: **COMPLETE → GATE-12 PASS (partial: chunk-4 exit demo slice owed at 04.09)**. CI evidence: **run 45144865455 — all 7 gates green**. Named PIT `surgicalSafetyAndChargesCoverage` green in the same run.

## 1. Scope
Per `chunk-04-inpatient-surgery-critical.md` §5: WHO Surgical Safety Checklist enforced at sign-in/time-out/sign-out, pre-op fitness/consent (chunk-0 consent engine), intra-op consumables → charge events, SSI follow-up flag to the IPC stub. §6 anaesthesia (evaluation, record, monitoring, recovery, PACU) is baseline code covered by `anaesthesiaWorkflowCoverage`.

## 2. Key artifacts
**Migrations:**
- `V81__ot_safety_charges.sql` — `surgery.surgery_schedule.consent_id`, `surgery.pre_op_checklist.stage`, `surgery.surgical_consumable`.
- `V82__consent_admin_grant.sql` — SYSTEM_ADMIN gains `consent:manage:consent` (V51 granted it only to DOCTOR/NURSE; the admin 403 was found by this section's PIT).

**Behaviour:**
- WHO checklist seeding (3 stages, idempotent) + state-machine enforcement: `started` requires all SIGN_IN + TIME_OUT items and a granted consent; `completed` requires SIGN_OUT (`SURGERY_WHO_INCOMPLETE`, `SURGERY_CONSENT_REQUIRED`, `SURGERY_CONSENT_WITHDRAWN`).
- Consumables stored and emitted as `billing.chargeable_performed` (`PROCEDURE`, procedure code) → chunk-3 ledger.
- SSI flag opens a high IPC task (`relatedType=SSI`).
- OpenAPI snapshot regenerated (`34b977bc…`), codegen stamped.

## 3. Gap closure
- ✔ WHO checklist at three stages, enforced
- ✔ Consent gate before start (withdrawal blocks)
- ✔ Consumables → charge events
- ✔ SSI follow-up flag (IPC stub task)
- ✔ PIT 04.06: `surgicalSafetyAndChargesCoverage` PASS

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | PIT covers WHO seed/enforcement, consent gate, consumable charge, SSI task |
| 2 Zero-stub | PASS | CI branch run 45144865455 |
| 3 Types | PASS | CI branch run 45144865455 |
| 4 Lint + format | PASS | CI branch run 45144865455 |
| 5 Tests | PASS | CI branch run 45144865455; named PIT below |
| 6 Accessibility | PASS | CI branch run 45144865455 (web untouched) |
| 7 Design system | PASS | CI branch run 45144865455 |
| 8 Contracts | PASS | Snapshot + codegen committed; parity green |
| 9 Data | PASS | V81/V82 additive + rollback notes; consumables link their charge event |
| 10 Audit | PASS | Checklist checks carry the checker; consumables record the recorder; SSI task carries the actor |
| 11 Docs | PASS | This report; LEDGER row 04.06 COMPLETE; tracker updated |
| 12 Exit item | PASS (partial) | OT slice of the 04.09 exit demo |

## 5. Tests
Named PIT **`surgicalSafetyAndChargesCoverage`** (new): WHO seed groups 5/4/4 + idempotent re-seed; consumable → ledger row (`PROCEDURE`, `CH-01`); SSI flag → IPC task; consent withdrawal blocks start with `SURGERY_CONSENT_WITHDRAWN`.

Regressions green: `surgicalWorkflowCoverage` (updated: consent at creation, WHO stages checked before start/complete), `anaesthesiaWorkflowCoverage`, `consentNotificationCoverage`, `chargeCaptureLedgerCoverage`.

## 6. Accessibility
Web surface untouched; repo-wide axe gate in CI.

## 7. Dual review — adversarial pass
1. **WHO enforcement** is server-side on the state machine; a schedule without seeded items cannot start (`SURGERY_WHO_INCOMPLETE`) — teams must seed first.
2. **Consent** is checked at the start transition, so a consent withdrawn after scheduling blocks the case.
3. **Consumables** emit at record time (one event per consumable); the ledger's source-event idempotency prevents double charges on replay.
4. **SSI flag** is a task, not a clinical diagnosis — IPC triage owns follow-up.
5. **V82** closes the admin consent gap; the permission matrix otherwise unchanged.
6. **Baseline PIT** updated to the new gates (same pattern as the discharge checklist) — no silent weakening.
7. **Cross-test interference:** the new PIT uses its own patient/schedule; consumables land in the shared ledger but are patient-scoped.

## 9. Deviations from contract
- PACU Aldrete scoring is the baseline recovery endpoint.
- Consent policy evaluation is at capture/start (no continuous re-evaluation).
- WHO items are the standard set; hospital additions use the existing checklist create endpoint.

## 8. Session trail
- Consent capture 403 for the admin → root-caused to V51's DOCTOR/NURSE-only grant (V25 catch-all predates) → V82.
- Consent `purpose` must be `treatment|data_sharing|communications` — PIT corrected from `surgery`.
- Duplicate `SurgicalConsumable` class from a double append — removed; checklist repository gained the ordered query.
- Docker Desktop died twice; compose restarted, postgres healthy.
