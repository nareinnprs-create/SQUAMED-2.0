# Section 00.14 — Design system

## 1. Scope
Tokens (`02-DESIGN-SYSTEM.md` §2–4), primitives (Button, Field, Chip, StatusDot), DataTable (density modes), Storybook + visual regression.

## 2. Built (current tree)
- `packages/design-system/src/tokens/{colors,typography,layout,index}.ts` + `src/themes/index.ts` — token source of truth.
- `packages/ui/src/primitives/` (incl. CommandBar, CommandPalette, StatusIndicator), `packages/ui/src/domain/` (ClinicalNoteEditor, OrderEntryForm, PrescriptionBuilder), `packages/ui/src/layout/` (9 layout comps), `packages/ui/src/theme/ThemeProvider.tsx` (light/dark + `prefers-contrast: more` handling) — ~62 TSX component files.
- PR #10 contrast fixes: token palette AA-tuned (e.g. secondaryText #596267 on #FFFFFF surface; ThemeProvider `prefers-contrast` mode).
- Visual regression: `apps/web/tests/visual/visual-regression.spec.ts` driven by CI a11y-perf job.
- Consumers rebuilt by PR #8 (master-data/orders/settings workspaces) prove component real-world use.

## 3. Contracts delivered
- Token + theme contracts (colors/typography/layout), component API surface via `@squamed/ui`.
- NOT delivered: DataTable with density modes (zero `DataTable` hits in packages/ui), Storybook + its visual-regression harness (repo has app-level VRT only; no `.storybook/` dir), no unit tests inside packages/ui.

## 4. Gates
1. FAIL — named contents missing: DataTable (density modes), Storybook + component-level visual regression.
2. PASS — CI run 43548016617 (gate 2 green).
3. PASS — CI run 43548016617 (gate 3 green).
4. PASS — CI run 43548016617 (gate 4 green).
5. PARTIAL via repo suite — CI run 43548016617 (gate 5 green); but packages/ui itself ships ZERO unit tests (coverage carried by consumers + app VRT).
6. PASS — CI run 43548016617 (gate 6 green, axe zero critical/serious) — includes PR #10 contrast fixes in scanned pages.
7. PASS — CI run 43548016617 (gate 7 green) — visual-regression screenshots match, Lighthouse budget in same job.
8. FAIL — schema-registry harness not yet wired (00.02 scope) (component props are TS-typed, no schema registry).
9. PASS — no DB tables in this section.
10. PASS — no write paths in this section.
11. PASS — this report.
12. FAIL — no named PIT test / scripted exit slice for a design-system journey (VRT suite is the closest evidence but is not a scripted exit-demo slice).

## 5. Tests
- `apps/web/tests/visual/visual-regression.spec.ts` (gate 7 evidence, run 43548016617 green).
- `command-bar.test.tsx` (app-level component test).
- `master-data.test.tsx`, `orders.test.tsx`, `settings.test.tsx` (PR #8 — @squamed/ui consumers under test).
- Gap: 0 test files in `packages/ui/src` itself.

## 6. Accessibility
Gate 6 green (run 43548016617): axe zero critical/serious across scanned app pages, which exercise the primitives; keyboard/focus paths covered by CI. Component-level axe runs (Storybook a11y) absent.

## 7. Issues found during build
Baseline section — see BASELINE-AUDIT.md §2.2 packages/ui + packages/design-system rows. Verified in current tree: PR #10 contrast handling present in ThemeProvider + tokens; PR #8 rebuilt the 3 consumer pages with real wiring + tests; Storybook and DataTable still absent.

## 8. External blockers
None.

## 9. Deviations from chunk file
Zero; DataTable/Storybook are unbuilt, not re-scoped.

## 10. Exit-demo slice
`npm run visual-regression` (apps/web) inside the a11y-perf gate — screenshots match in run 43548016617. Storybook-based demo slice not available.
