# Section 07.08 — QMS + IPC + legal/risk (chunk 7)

**Verdict: COMPLETE → GATE-12 PASS (partial: evidence-pack bundle + per-observation hygiene owed)**. Gate-run assessment of the CURRENT tree @ PR #123 (chunks 0–6 + 07.05 merged + this PR). CI evidence: **run 45450152195 — all 7 gates green**. Named PIT `qmsIpcLegalCoverage` green in the same run.

## 1. Scope
Per `chunk-07-full-depth.md` / `20-CHUNK7-SECTIONS.md` 07.08: QMS indicators, accreditation evidence, CAPA, audit program; IPC surveillance, outbreaks, device-associated infections, hand hygiene; legal/risk (litigation hold, risk registers, evidence preservation).

## 2. Key artifacts
**Migration `V97`**: `qms.indicator`, `capa`, `audit`, `risk`, `infection_case`, `outbreak`, `hand_hygiene`, `litigation_hold`; permissions + `QMS_OFFICER` role.

**Backend:** `QmsEntities.kt`, `QmsRepositories.kt`, `QmsService.kt`, `QmsController.kt` (14 endpoints incl. summary).

## 3. Gap closure
- ✔ Indicator register with target-vs-actual status.
- ✔ CAPA lifecycle; audit program with findings.
- ✔ Risk register with computed score.
- ✔ Device-associated infection surveillance + resolve; outbreak declare/close.
- ✔ Hand-hygiene compliance per ward+period.
- ✔ Litigation holds (evidence preservation) + release.
- ⚠ Evidence-pack bundling and per-observation hygiene owed.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS (partial) | PIT covers every shipped leg |
| 2 Zero-stub | PASS | CI branch run 45450152195 |
| 3 Types | PASS | CI branch run 45450152195 |
| 4 Lint + format | PASS | CI branch run 45450152195 |
| 5 Tests | PASS | CI branch run 45450152195; named PIT below |
| 6 Accessibility | PASS | No UI change |
| 7 Design system | PASS | No UI change |
| 8 Contracts | PASS | Snapshot `5840710f…` + codegen committed |
| 9 Data | PASS | V97 additive; unique indicator/capa/outbreak/hygiene keys |
| 10 Audit | PASS | Controller-mediated writes; records carry actor |
| 11 Docs | PASS | This report; LEDGER row 07.08 COMPLETE; tracker |
| 12 Exit item | PASS (partial) | QMS/IPC slice of the 07.11 exit |

## 5. Tests
Named PIT **`qmsIpcLegalCoverage`**: indicator upsert flips ON_TRACK→OFF_TRACK with one row; CAPA close then 409; audit complete; risk 4×4=16 + bounded; CLABSI record/resolve + type guard; outbreak duplicate 400 + close; hand hygiene 90.00% + impossible counts 400; hold release then 409; summary counts.

## 6. Accessibility
No UI change.

## 7. Dual review — adversarial pass
1. **Indicator identity** — one row per code+period; status is derived, never set by the caller.
2. **Lifecycle finals** — CAPA close, audit completion, infection resolve, outbreak close and hold release are each once (409 on repeat).
3. **Risk scale** — likelihood/impact bounded 1–5; score computed server-side.
4. **Infection typing** — only the device-associated/other vocabulary is accepted.
5. **Outbreak uniqueness** — code unique per hospital.
6. **Hygiene counts** — compliant ≤ opportunities, opportunities > 0; percentage computed HALF_UP.
7. **Tenant** — every query is hospital-scoped.
8. **Legal** — holds are hospital-scoped and released with a timestamp; the erasure gate remains the 06.08 governance hold (documented follow-up).

## 8. Deviations from contract
- Accreditation evidence packs are the register records + summary (no bundle export).
- Hand hygiene is aggregate per ward+period (no per-observation capture).
- Outbreaks are hospital-level (no ward case-linkage board).
- Two hold tables (QMS litigation vs governance erasure gate) are not yet unified.

## 9. Session trail
- Local Redis 6380 is in a Windows excluded port range; the PIT ran on 7379 with the patch reverted before commit.
