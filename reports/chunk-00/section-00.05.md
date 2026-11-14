# Section 00.05 — Org tree & config engine

## 1. Scope
Location closure table (campus→building→ward→room→bed), config documents (versioned, validated JSONB), config APIs, audit on config change.

## 2. Built (current tree)
- `hospitalconfig/domain/{Hospital,Campus,Building,Ward,Room,Bed,Department,Specialty,HospitalService}.kt` — org-tree entities.
- `hospitalconfig/repository/*` (9 repositories) — persistence.
- `hospitalconfig/api/HospitalConfigController.kt` + `HospitalConfigDtos.kt` — CRUD + tree APIs.
- Migration `V4__hospital_config.sql` (+ permission seed V5).
- Web `apps/web/src/app/(app)/hospital-config/` — config workspace page.
- Audit: all config writes intercepted by `AuditAspect` (generic audit trail).

## 3. Contracts delivered
- Location tree CRUD + validation APIs (PIT-validated branches).
- NOT delivered: config documents as versioned, validated JSONB (zero hits for config-document machinery), dedicated config-change audit trail beyond the generic interceptor.

## 4. Gates
1. FAIL — named contents missing: versioned validated JSONB config documents.
2. PASS — CI run 43548016617 (gate 2 green).
3. PASS — CI run 43548016617 (gate 3 green).
4. PASS — CI run 43548016617 (gate 4 green).
5. PASS — CI run 43548016617 (gate 5 green); named tests below.
6. PASS — CI run 43548016617 (gate 6 green) (hospital-config page in axe scope).
7. PASS — CI run 43548016617 (gate 7 green).
8. FAIL — schema-registry harness not yet wired (00.02 scope).
9. FAIL — `V4__hospital_config.sql` exists; org-tree tables are hospital-scoped but RLS cross-tenant denial suite ABSENT until 00.04 lands (ADR-13).
10. PASS — AuditAspect intercepts HospitalConfigController writes; audit rows asserted PIT-side (`/audit/logs` reads after config/patient writes).
11. PASS — this report.
12. PASS — PIT `catalogsAndHospitalStructureCoverage` + `hospitalConfigValidationBranchesCoverage` cover the org-tree journey (API-level scripted slice).

## 5. Tests
PIT: `catalogsAndHospitalStructureCoverage`, `hospitalConfigValidationBranchesCoverage` (green, run 43548016617). Coverage ≥0.80 merged enforced.

## 6. Accessibility
hospital-config page in axe gate scope; run 43548016617 gate 6 green.

## 7. Issues found during build
Baseline section — see BASELINE-AUDIT.md §2.1 hospitalconfig row. Verified current tree: no config-document JSONB machinery added by wiring PRs; generic audit does cover config writes.

## 8. External blockers
None.

## 9. Deviations from chunk file
Zero; JSONB config documents are unbuilt, not re-scoped.

## 10. Exit-demo slice
PIT `hospitalConfigValidationBranchesCoverage` (create campus→bed tree, invalid transitions rejected) — green in run 43548016617.
