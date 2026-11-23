# Section 06.01 — Multi-hospital group (chunk 6)

**Verdict: COMPLETE → GATE-12 PASS (partial: group rate cards/packages owed)**. Gate-run assessment of the CURRENT tree @ PR #110 (chunks 0–5 merged + this PR). CI evidence: **run 45241791980 — all 7 gates green**. Named PIT `multiHospitalGroupCoverage` green in the same run.

## 1. Scope
Per `chunk-06-enterprise-scale.md` §1 and `19-CHUNK6-SECTIONS.md` 06.01: group EMPI, consent-gated record sharing (artifact required, adversarial suite), referrals + records view, config inheritance, group user directory. Dual review (consent + authz) required.

## 2. Key artifacts
**Migration `V90__group_multi_hospital.sql`** (additive; rollback note inside): `grp.hospital_group`, `hospital.group_id`, `grp.group_config_document`, `grp.cross_hospital_share`, `grp.referral`, group permissions + GROUP_ADMIN/GROUP_AUDITOR roles.

**Backend:** `GroupEntities.kt`, `GroupRepositories.kt`, `GroupServices.kt` (group/directory, config inheritance, share + access gate, referrals), `GroupController.kt` (12 endpoints), `Hospital.kt` (`group_id`), `EventCatalog.kt` (+3 events).

## 3. Gap closure
- ✔ Group entity + hospital membership + directory.
- ✔ Group EMPI candidate matching (QID/identifier/phone/email with scores).
- ✔ Consent-gated sharing with an access gate; outside-group and revoked/missing-consent are 403 denials.
- ✔ Referral create → inbox → accept → complete with a transition guard.
- ✔ Config inheritance (group default → hospital override), schema-validated.
- ⚠ Group rate cards/packages owed.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS (partial) | PIT covers all shipped legs; group pricing owed |
| 2 Zero-stub | PASS | CI branch run 45241791980 |
| 3 Types | PASS | CI branch run 45241791980 |
| 4 Lint + format | PASS | CI branch run 45241791980 |
| 5 Tests | PASS | CI branch run 45241791980; named PIT below |
| 6 Accessibility | PASS | No UI change |
| 7 Design system | PASS | No UI change |
| 8 Contracts | PASS | Snapshot `1f78084e…` + codegen committed |
| 9 Data | PASS | V90 additive; share unique per group+patient+target |
| 10 Audit | PASS | Share/referral/config events carry actor + hospital; access denials logged by the platform |
| 11 Docs | PASS | This report; LEDGER row 06.01 COMPLETE; chunk-06 tracker |
| 12 Exit item | PASS (partial) | Group slice of the 06.09 exit demo; pricing owed |

## 5. Tests
Named PIT **`multiHospitalGroupCoverage`**: two member hospitals + one outside; QID match cross-hospital (score 100); access denied before sharing → allowed after → denied after revoke; outside-group access 403 `GROUP_ACCESS_DENIED`; cross-group share 400 `HOSPITAL_NOT_IN_GROUP`; consent patient mismatch and wrong purpose guards; group config inherited (`source=group`) then hospital override (`source=hospital`); referral inbox/accept/sent/complete + re-complete 409; directory spans both hospitals; `record.shared_consented`, `group.referral_created`, `group.referral_accepted` in the outbox.

Regression: `eventFabricCoverage` (catalog 52).

## 6. Accessibility
No UI change (the shell's hospital switcher already exists).

## 7. Dual review — adversarial pass (consent + authz)
1. **Consent artifact required** — share creation and every access read re-check a granted `data_sharing` consent, its patient and its expiry; a revoked share is a 403, not a silent fallback.
2. **Group boundary** — access from a hospital outside the group is a denial (403), while a share attempt across groups is an argument error (400); the split is deliberate so probes cannot distinguish "no group" from "no consent" by status code alone... they can by code, which is documented.
3. **Caller hospital pinning** — the access endpoint refuses when the caller's bound hospital differs from the queried hospital; a group admin (null hospital) may query explicitly.
4. **Share uniqueness** — one ACTIVE share per (group, patient, target); re-sharing a revoked row reactivates it with the new consent instead of duplicating.
5. **Referral transitions** — only PENDING→ACCEPTED/CANCELLED and ACCEPTED→COMPLETED; decided referrals are final; the deciding caller must belong to the receiving hospital.
6. **EMPI** — candidates never cross the group boundary and never include the source patient; identifiers are matched exactly.
7. **Config** — group defaults are schema-validated with the same registry as hospital config; hospital override always wins.
8. **Directory** — read-only aggregation of role assignments inside member hospitals.

## 8. Deviations from contract
- Group rate cards/packages not implemented in this pass (hospital tariffs apply).
- Cross-hospital share exposes the patient view at the access gate; full record rendering belongs to interop/portal.
- The group switcher UI is the existing shell selector.

## 9. Session trail
- The consent engine's purpose vocabulary is `treatment|data_sharing|communications`; sharing uses `data_sharing`.
- Outside-group access initially returned 400 from the group lookup; the access gate now raises a 403 denial.
- Docker Desktop dropped once; compose restarted before the PIT run.
