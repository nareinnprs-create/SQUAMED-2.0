# CHUNK 1 — FINAL REPORT (section 01.12 chunk exit)

## 1. Scope
Chunk-1 exit per `chunk-01-opd-front-office.md` EXIT CRITERIA (L60-61), SAFETY (L47-52), TESTS (L57-58) and `03-BUILD-PROTOCOL.md` §7/§7.6: the full walk-in-to-Patient-360 demo live at pilot-ready state, kiosk 50/hr soak, TV soak, 30-min downtime drill (SAFETY L52), dual clinical-write review (13-AI-BUILD-WORKFORCE §5), blind re-verification of 20% of completed sections (§7.6), this report, and the sign-off block. **01.12 carries zero feature code by design** — it is the exit protocol section.

**Honest chunk state at exit:** 10 of 12 sections COMPLETE (01.01-01.09, 01.11). **01.10 Entitlement & tenant brand remains PLANNED / externally blocked** (`exp:E2,E5` — DLT/WABA + BSP brand contract; entitlement service) — tracked, not skipped, per protocol L28-30. **01.12 lands at SECTION REVIEW, never COMPLETE**, because hardware soak evidence is owed to `exp:E10` and the dual clinical-write review returned FAIL on both agents (§7) — internal hardening debt that must clear before clinical sign-off.

## 2. Built (this docs batch)
- `scripts/demo-01.12-chunk1-exit.ps1` — 30-step exit demo (full L61 scenario).
- `scripts/soak-01.12-kiosk.ps1` — 60 API check-ins @72 s ≈ 72 min ≥ 50 check-ins/hr.
- `scripts/soak-01.12-tv-display.ps1` — 30-min display-board poll + queue churn, 15-40 s jitter.
- `docs/runbooks/downtime-drill.md` — the 30-min human paper-mode drill runbook (open window → 20-min paper flow → close → batch reentry → verify).
- `reports/chunk-01/section-01.12.md` + this FINAL + LEDGER row 71.

## 3. Chunk-1 section statuses
| Section | Status | Report |
|---|---|---|
| 01.01 Scheduling engine | COMPLETE (PR #39) | `reports/chunk-01/section-01.01.md` |
| 01.02 Queue & token engine | COMPLETE (PR #41) | `reports/chunk-01/section-01.02.md` |
| 01.03 Reception workspace | COMPLETE (PR #43) | `reports/chunk-01/section-01.03.md` |
| 01.04 OPD EMR core | COMPLETE (PR #48) | `reports/chunk-01/section-01.04.md` |
| 01.05 Patient 360 v1 | COMPLETE (PR #50) | `reports/chunk-01/section-01.05.md` |
| 01.06 TV display app | COMPLETE (PR #54; 24 h hardware soak owed `exp:E10`) | `reports/chunk-01/section-01.06.md` |
| 01.07 Voice announcements | COMPLETE (PR #52) | `reports/chunk-01/section-01.07.md` |
| 01.08 Kiosk-lite | COMPLETE (PR #56; hardware soak + paper-out owed `exp:E10`) | `reports/chunk-01/section-01.08.md` |
| 01.09 Emergency fast-path + downtime pack | COMPLETE (PR #58) | `reports/chunk-01/section-01.09.md` |
| 01.10 Entitlement & tenant brand | **PLANNED — externally blocked `exp:E2,E5`** | - |
| 01.11 Analytics mini + shadow-run prep | COMPLETE (PR #60) | `reports/chunk-01/section-01.11.md` |
| 01.12 Chunk exit | **SECTION REVIEW** (`exp:E10` + §7 dual-review items) | `reports/chunk-01/section-01.12.md` |

## 4. Chunk exit demo evidence
`pwsh scripts/demo-01.12-chunk1-exit.ps1` against the app on :8080 (compose dev stack, post-recovery boot): **30 passed, 0 failed, live 2026-11-18, first run.** Full contract scenario: admin login → fresh hospital → doctor + receptionist + nurse + kiosk logins → walk-in registered with MRN **in 0.93 s** (<90 s criterion) → session template → today slot → appointment 201 → kiosk check-in issues queue token → TV display board fetched → zone + announcement rule configured → `announcement.enqueued` carries the Hindi voice text (outbox verified) → encounter started → **nurse vitals 403** (chunk-1 permission boundary, §9) → vitals captured → SOAP note signed with PIN → re-sign **409 `NOTE_ALREADY_SIGNED`** → allergy **BLOCK 409 `ALLERGY_BLOCK`** → WARN without reason **409 `ALLERGY_WARN_REASON_REQUIRED`** → WARN override saved with typed reason → clean Rx `allergyStatus=checked` → CBC order placed → `encounter.order.placed` in the outbox → nurse task pending→in_progress → consult-fee receipt → doctor follow-up **403** → receptionist books follow-up (encounter stamped) → encounter completed → Patient 360 aggregation + vitals thread populated.

## 5. Soaks
- **Kiosk soak (API, ≥50/hr target), attempt 9:** **INFRA-INVALID - Docker death #18 at check-in 29** (checks 1-28 green, 5-streak guard abort at 33, no rate verdict; log `reports/soak-01.12-kiosk-live9.log`). Attempts 1-2: script bug #1 (missing `doctorId` → `APPOINTMENT_DOCTOR_REQUIRED`); attempt 3: INFRA-INVALID (Docker death #14); attempt 4: script bug #2 (slip envelope unwrap); attempt 5: INFRA-INVALID (Docker death #15); attempt 6: script bug #3 (15-min token TTL outlived); attempt 7: INFRA-INVALID (Docker death #16); attempt 8: INFRA-INVALID (Docker death #17) - all fixed, see section report §7.
- **TV display soak (API, 30-min poll + churn), attempt 6:** **PASS - 70 rounds, 0 malformed, 70 called, ~30 min, live 2026-11-18** (log `reports/soak-01.12-tv-live6.log`). Attempts 1-2: INFRA-INVALID (Docker deaths #13 - 625 rounds, 592 malformed, 0 called - and #14 at ~round 10); attempt 3: script bug #2 (churn counter envelope unwrap); attempt 4: INFRA-INVALID (Docker death #15); attempt 5: script bug #3 (token TTL outlived after 30 clean rounds).
- **Hardware soaks owed `exp:E10`** (tracked, not skipped): 24 h TV on real Android TV hardware on flaky network (L61); 50/hr kiosk soak on real hardware + printer paper-out recovery (L57).

## 6. CS-09 downtime drill (SAFETY L52)
- **Tooling rehearsal:** `scripts/demo-01.09-emergency-fastpath-downtime.ps1` re-run live 2026-11-18: **31 passed, 0 failed** (window open → paper tokens DT-01/DT-02 preserved verbatim → reentry + visit re-stamp → close → nothing lost on the board).
- **Human drill runbook shipped:** `docs/runbooks/downtime-drill.md` — 30-min drill: open window → 20-min paper flow ( registrations/tokens on paper ) → close → batch reentry via the downtime API → verify nothing lost. The drill itself is a human operational activity scheduled in exit week with the clinical lead (13-AI-BUILD-WORKFORCE §6 human gate).

## 7. Dual clinical-write security review — BOTH AGENTS RETURNED **VERDICT: FAIL**

Two independent agents reviewed the clinical-write surface against SAFETY L47-52 + L40 (ABAC on encounter owner). Verdicts recorded verbatim: **Agent A — VERDICT: FAIL. Agent B — VERDICT: FAIL.** Overlapping findings:

| # | Finding | Contract line |
|---|---|---|
| 1 | `requireMrn` (`EncounterService.kt:583-591`) enforced only on `placeOrder` (:321) + `prescribe` (:389); note sign / vitals / other encounter-scoped writes skip the two-identifier confirmation | SAFETY L48 |
| 2 | Empty allergy list passes silently with `allergyStatus='checked'`; no NKA sentinel exists (grep: 0 hits) — a never-filled list is indistinguishable from verified "no known allergies" | SAFETY L49 |
| 3 | `Allergy.severity` (`Allergy.kt:19-20`) is a free string, never consulted; BLOCK/WARN decided by lowercased substring shape (≥4 chars either direction) | SAFETY L50 |
| 4 | Note immutability **HOLDS** (no PUT/PATCH; re-sign 409 `NOTE_ALREADY_SIGNED`), but amendment-as-new-version-with-link is missing | SAFETY L51 |
| 5 | ABAC is RBAC-only: `SquamedPermissionEvaluator` is a permission string match, no encounter-owner predicate; `StartEncounterDto.doctorId` is client-supplied | L40 |
| 6 | Additional: `AuditRecorder` fails open (`:91-94`); completed encounters accept writes; `removeDiagnosis` hard-deletes unguarded; `cancelOrder` unexposed-but-unguarded (`:361-371`); `FhirService.allEncounters()` no tenant filter (`FhirService.kt:386`) | - |

**Disposition:** items 1-6 are the **chunk-1 safety register (owed hardening)** — internal, no external dependency; they gate clinical sign-off below and are queued at the top of the next build lane. The exit demo runs on the gates that ARE implemented (BLOCK/WARN semantics as built, immutability, role boundaries) — those work as demoed; the FAIL verdicts describe what a clinical write surface must still harden before a human clinical lead signs.

## 8. Blind re-verification (§7.6) — 2/10, both PASS-WITH-NOTES
Seeded pick logged: **seed = 20261118 → 01.07, 01.01**. Fresh agent per section, contract-only prompt, read-only + web vitest only (no PIT), forbidden from `reports/`.
- **01.07 Voice announcements: PASS-WITH-NOTES** (vitest 120/120) — cloud TTS not implemented (browser SpeechSynthesis, recorded decision); regional-language fallback speaks English; `/announcements` intentionally not in nav (TV page); enqueue rides `/queue/announcements` with `queue:manage:queue`; behavior pinned by PIT `voiceAnnouncementsJourney` (`PlatformIntegrationTests.kt:7990-8151`).
- **01.01 Scheduling engine: PASS-WITH-NOTES** (vitest 120/120) — no backend unit tests for the scheduling engine (PIT-pinned per the 01.01 report); waitlist at `/schedules/waitlist*` not `/appointments`; no web UI for template/waitlist admin; `roomId`/`equipmentIds` unvalidated refs; booking `source` free-text; no dedicated web test for the appointments page.

No failures → no section reopen, no gate-adequacy review triggered.

## 9. Safety register (chunk-1 close state)
| Item | State at chunk exit |
|---|---|
| Two-identifier confirmation on every encounter-scoped write (L48) | **PARTIAL** — orders + Rx only; §7 item 1 owed |
| Explicit NKA before eRx (L49) | **PARTIAL** — allergy documented via the BLOCK/WARN gate, but empty-list ≠ verified NKA; §7 item 2 owed |
| eRx BLOCK hard-fail + WARN typed reason (L50) | **WORKING as built** — demo-proven (`ALLERGY_BLOCK` 409, `ALLERGY_WARN_REASON_REQUIRED` 409, override w/ reason saved); severity data model weak per §7 item 3 |
| Note signature immutable; amendments = new version (L51) | **IMMUTABILITY HOLDS** (409 on re-sign); amendment mechanism missing (§7 item 4) |
| Downtime drill 30-min paper mode (L52) | Runbook shipped + tooling rehearsal 31/31; human drill scheduled exit week |
| ABAC on encounter owner (L40) | **NOT ENFORCED** — RBAC-only (§7 item 5) |
| Kiosk 50/hr soak (L57) | API soak no clean run today (infra); hardware soak owed `exp:E10` |
| TV 24 h flaky-network soak (L61) | API soak PASS (70 rounds, 0 malformed, 70 called, ~30 min); hardware soak owed `exp:E10` |

**Vitals role mapping deviation (chunk L40 vs permission matrix):** `clinical:write:vital` is granted to DOCTOR + SYSTEM_ADMIN only (verified via psql; no NURSE row) — the demo asserts the nurse 403 as the chunk-1 boundary; matrix fix is a permission-owner decision, recorded per the no-silent-deviation rule.

## 10. Sign-off block — PENDING
Per master plan §11 / 13-AI-BUILD-WORKFORCE §6: **clinical lead + tech lead human sign-off required for chunk close.** Status at this report: **not claimed and not granted** — the dual clinical-write review returned FAIL 2/2 (§7) and the hardware soaks are owed (`exp:E10`). The exit demo, drill rehearsal, blind re-verification, and the TV API soak are green (kiosk API soak: no clean run - Docker death #18); sign-off remains open until the §7 hardening lands and a human clinical lead reviews the safety register.
- Exit demo: PASS (30/30, first run)
- CS-09 drill rehearsal: PASS (31/31) + runbook shipped
- Soaks: attempts aborted for infra (Docker deaths #13-#18) and four soak-script bugs (kiosk doctorId, slip envelope unwrap + TV churn-counter unwrap, 15-min token TTL outlived, missing API-call timeout - all fixed); TV evidence PASS (attempt 6: 70 rounds, 0 malformed, 70 called, ~30 min); kiosk API soak no clean run (attempt 9 INFRA-INVALID: Docker death #18 at check-in 29, checks 1-28 green, guard abort at 33)
- Dual clinical-write review: **FAIL (2/2 agents)** — verdicts verbatim §7; must-fix list registered §9
- Blind re-verification: PASS-WITH-NOTES (2/2 sections)
- Date: 2026-11-18
