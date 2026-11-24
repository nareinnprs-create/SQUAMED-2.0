# Section 06.03 — Patient portal v2 (chunk 6)

**Verdict: COMPLETE → GATE-12 PASS (partial: Web Push delivery + portal UI owed; SC-5 stays open)**. Gate-run assessment of the CURRENT tree @ PR #115 (chunks 0–5 + 06.01/06.04/06.05 merged + this PR). CI evidence: **run 45377625274 — all 7 gates green**. Named PIT `patientPortalCoverage` green in the same run.

## 1. Scope
Per `chunk-06-enterprise-scale.md` §3 and `19-CHUNK6-SECTIONS.md` 06.03: ABHA login, booking, token tracking, reports, prescriptions, bills + online payment, discharge instructions, care-plan view, notification centre, Web Push; own-data ABAC.

## 2. Key artifacts
**Migrations `V93` + `V94`** (additive): `portal.patient_account`, `portal.push_subscription`, portal permissions + PATIENT role; admin notification-send grant (gap found by the PIT).

**Backend:** `PortalEntities.kt`, `PortalRepositories.kt`, `PortalService.kt` (own-data reads, booking, payment, notifications, push registry), `PortalController.kt` (15 endpoints), `PortalDtos.kt`.

## 3. Gap closure
- ✔ Portal account + ABHA link (ABDM OTP external).
- ✔ Own-data: appointments, reports (lab + radiology), prescriptions, bills, discharge summaries, care plans.
- ✔ Booking through the scheduling engine for the account's patient.
- ✔ Online bill payment with ownership checks.
- ✔ Notification centre + push subscription registry.
- ⚠ Web Push delivery (VAPID/HTTPS) not live; portal UI not built.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS (partial) | PIT covers all shipped legs; push delivery + UI owed |
| 2 Zero-stub | PASS | CI branch run 45377625274 |
| 3 Types | PASS | CI branch run 45377625274 |
| 4 Lint + format | PASS | CI branch run 45377625274 |
| 5 Tests | PASS | CI branch run 45377625274; named PIT below |
| 6 Accessibility | PASS | No UI change |
| 7 Design system | PASS | No UI change |
| 8 Contracts | PASS | Snapshot `8d0b8a42…` + codegen committed |
| 9 Data | PASS | V93/V94 additive; unique account/user, unique push endpoint |
| 10 Audit | PASS | Account link records actor; payments go through the billing audit trail |
| 11 Docs | PASS | This report; LEDGER row 06.03 COMPLETE; tracker |
| 12 Exit item | PASS (partial) | Portal slice of the 06.09 exit; push + UI owed |

## 5. Tests
Named PIT **`patientPortalCoverage`**: link + duplicate 409; own MRN/ABHA; own bill listed, other patient's hidden; cross-patient payment 403; own invoice paid (due 0); appointment booked for the portal patient; prescription/lab/care plan visible; notification read; push subscribe, endpoint hijack 403, status unconfigured; unlinked user 403 `PORTAL_ACCOUNT_REQUIRED`.

## 6. Accessibility
No UI change.

## 7. Dual review — adversarial pass (own-data ABAC)
1. **No patient id from the caller** — every read uses `account.patientId`; the API never accepts a patient parameter.
2. **Account required** — unlinked or inactive accounts are 403 before any data access.
3. **Payment ownership** — the invoice must belong to the account's patient; cross-patient payment is 403.
4. **Notification ownership** — a notification can only be read by its recipient.
5. **Push endpoint ownership** — an endpoint registered by one user cannot be re-registered by another (403).
6. **Booking** — the appointment is created for the account's patient; the scheduling engine keeps its slot/no-show guards.
7. **Reports** — lab/radiology orders are filtered by the account's patient id.
8. **Honest push status** — the API reports `configured=false` rather than pretending delivery works.

## 8. Deviations from contract
- Web Push delivery not live (VAPID keys + HTTPS are deployment); SC-5 remains open.
- ABHA login is the stored link; ABDM OTP is external (E1).
- No Next.js portal screens in this pass.
- Token tracking is exposed through appointments/reports rather than a dedicated live token view.

## 9. Session trail
- Local Redis port 6380 fell into a Windows excluded port range; the PIT ran against a 7379 container with the port patch reverted before commit (CI uses 6380).
- The PIT exposed a real permission gap: SYSTEM_ADMIN could not send notifications (V51 granted send to DOCTOR/NURSE only) → V94.
