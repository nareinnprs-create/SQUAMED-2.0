# Section 06.04 — Workforce / HR-lite (chunk 6)

**Verdict: COMPLETE → GATE-12 PASS (partial: payroll stays external by design)**. Gate-run assessment of the CURRENT tree @ PR #111 (chunks 0–5 + 06.01 merged + this PR). CI evidence: **run 45248098230 — all 7 gates green**. Named PIT `workforceHrCoverage` green in the same run.

## 1. Scope
Per `chunk-06-enterprise-scale.md` §4 and `19-CHUNK6-SECTIONS.md` 06.04: employee master activation, rostering + shift planning, leave/attendance, credential-expiry registry with renewal tasks, nurse-patient ratio report; payroll external via export.

## 2. Key artifacts
**Migration `V91__workforce_hr_lite.sql`** (additive; rollback note inside): `hr.roster`, `hr.shift`, `hr.leave_request`, `hr.attendance`, `hr.credential`; permissions + HR_ADMIN role.

**Backend:** `HrEntities.kt`, `HrRepositories.kt`, `HrServices.kt` (roster, leave, attendance, credential scan, reports), `HrController.kt` (11 endpoints), `EventCatalog.kt` (+2 events).

## 3. Gap closure
- ✔ Rostering with week normalisation, duplicate/role/code guards, publish freeze.
- ✔ Leave request + single decision.
- ✔ Attendance upsert per employee+day.
- ✔ Credential registry + 30-day renewal window with exactly one renewal task and an event.
- ✔ Nurse-patient ratio per ward.
- ✔ Payroll export (shifts/hours/leave days) — no payroll engine.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | PIT covers every shipped leg |
| 2 Zero-stub | PASS | CI branch run 45248098230 |
| 3 Types | PASS | CI branch run 45248098230 |
| 4 Lint + format | PASS | CI branch run 45248098230 |
| 5 Tests | PASS | CI branch run 45248098230; named PIT below |
| 6 Accessibility | PASS | No UI change |
| 7 Design system | PASS | No UI change |
| 8 Contracts | PASS | Snapshot `9d8a4767…` + codegen committed |
| 9 Data | PASS | V91 additive; unique constraints on roster/shift/attendance |
| 10 Audit | PASS | Publisher/decider recorded; roster + credential events |
| 11 Docs | PASS | This report; LEDGER row 06.04 COMPLETE; tracker |
| 12 Exit item | PASS (partial) | Workforce slice of the 06.09 exit demo |

## 5. Tests
Named PIT **`workforceHrCoverage`**: roster draft → 3 shifts → duplicate shift 409 → publish → post-publish add 409 `ROSTER_NOT_DRAFT`; duplicate roster 409; leave approved then re-decide 409; attendance check-in then check-out keeps one row; credential scan links a renewal task and a re-scan creates no second task; ratio 1 patient / 2 nurses = 0.5; payroll row 1 shift / 8 hours; `hr.roster_published` + `hr.credential_expiring` in the outbox.

Regression: `eventFabricCoverage` (catalog 54).

## 6. Accessibility
No UI change.

## 7. Dual review — adversarial pass
1. **Roster immutability** — published rosters reject edits; a duplicate roster for the same ward/week is refused.
2. **Shift validity** — the date must fall inside the roster week; codes are constrained; duplicate employee/date/code refused.
3. **Hospital scoping** — employees must belong to the acting hospital on roster/leave/attendance/credential paths.
4. **Leave decisions are final** — a decided request cannot be re-decided.
5. **Attendance upsert** — one row per employee+day; partial updates (in then out) never duplicate.
6. **Credential idempotence** — the renewal task is linked once; re-scans neither duplicate the task nor the event; past-due flips to EXPIRED.
7. **Ratio correctness** — patients come from admitted admissions via bed→ward; nurses from NURSE shifts on the date; unassigned bucket is explicit; zero nurses yields a null ratio rather than a divide-by-zero.
8. **Payroll** — export only; hours are a documented 8/shift convention.

## 8. Deviations from contract
- Shift codes (EARLY/LATE/NIGHT) instead of free start/end instants; 8 h/shift for the export.
- No roster auto-generation, no leave balance ledger, no biometric attendance, no payroll engine.

## 9. Session trail
- `TaskService.createTask` needs a non-null creator; the credential scan takes the actor id non-null.
- Docker Desktop dropped once; compose restarted before the PIT run.
