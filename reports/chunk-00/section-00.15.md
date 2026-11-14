# Section 00.15 — App shell (universal search part covered; shell composition partial)

## 1. Scope
Nav rail, hospital selector, universal search (OpenSearch index + pipeline), notification center, task inbox, user/security menu, i18n (en/hi, ICU, no-hardcoded-string lint live).

## 2. Built (current tree)
- `search/SearchService.kt`, `SearchIndexer.kt` (indexes `squamed-patients`, `squamed-encounters`, backfill), `SearchApi.kt`, `SearchModels.kt`, `search/config/OpenSearchConfig.kt` — OpenSearch universal search backend.
- Web: `components/ai/command-bar.tsx` (+ `command-bar.test.tsx`) — universal search entry; `components/notification-bell.tsx` — notification center; `packages/ui/src/layout/` — nav/shell layout primitives.
- PIT spins a real OpenSearch container in integration tests.

## 3. Contracts delivered
- `/api/v1/search/search?q=` + `/search/search/indices` APIs; two indexes with backfill pipeline (PIT-asserted).
- NOT delivered: hospital selector, task inbox page (no task dir under apps/web), user/security menu (no page), i18n en/hi ICU + no-hardcoded-string lint (only `toLocaleDateString()` false positives; no i18n framework), Postgres FTS (this section's chunk-row sibling 00.07 gap). 00.13 offline dependency ABSENT (blocks full shell).

## 4. Gates
1. FAIL — named contents missing: hospital selector, task inbox, user/security menu, i18n (en/hi ICU + string lint), offline groundwork dependency.
2. PASS — CI run 43548016617 (gate 2 green).
3. PASS — CI run 43548016617 (gate 3 green).
4. PASS — CI run 43548016617 (gate 4 green) (whitelabel string-lint portion cannot be fully exercised: i18n lint not live).
5. PASS — CI run 43548016617 (gate 5 green); named tests below.
6. PASS — CI run 43548016617 (gate 6 green) (shell pages in axe scope).
7. PASS — CI run 43548016617 (gate 7 green).
8. FAIL — schema-registry harness not yet wired (00.02 scope).
9. PASS — no DB tables in this section (OpenSearch indexes only).
10. PASS — no direct write paths (indexing follows audited writes).
11. PASS — this report.
12. PASS — PIT `searchAndPatient360Journey` covers create patient → indexed → found via `/search` → indices listed (API-level slice).

## 5. Tests
PIT: `searchAndPatient360Journey` (patient found through universal search, indices endpoint), `injectionPayloadsAreNeutralized` (OpenSearch query_string injection safety). Web: `command-bar.test.tsx`. Green, run 43548016617. Coverage ≥0.80 merged enforced.

## 6. Accessibility
Shell pages (dashboard, patients, command bar) in axe gate scope; run 43548016617 gate 6 green (zero critical/serious).

## 7. Issues found during build
Baseline section — see BASELINE-AUDIT.md §2.1 search row. Verified current tree: `SearchService.kt` still fails silently (`catch → emptyList`, 3 catch sites) — violates zero-silent-drop spirit; only 2 indexes; i18n absent.

## 8. External blockers
None (00.13 offline dependency is internal, not external).

## 9. Deviations from chunk file
Zero; missing shell pieces unbuilt. Note section title in chunk file is "App shell" — search module alone was mapped in baseline; shell composition remains largely unbuilt.

## 10. Exit-demo slice
PIT `searchAndPatient360Journey` (register patient → search finds by name → indices endpoint) — green in run 43548016617.
