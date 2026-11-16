# 00.14 design-system rework — remediation of the §7.6 FAIL verdict

Closes the reopened 00.14 item (chunk-00 FINAL §10.1). The re-verification verdict said the section failed a fresh-agent brief; the brief existed only in the verifier's session. It is now **re-derived and persisted** (section-00.14.md §11.1) and the rework is executed against it — deviations recorded there, none silent.

## What landed

**Typography (02-DESIGN-SYSTEM §3)** — IBM Plex Sans 400/500/600 + IBM Plex Sans Devanagari (separate Google family) + IBM Plex Mono 400/500 via `next/font/google` in `apps/web/src/app/layout.tsx`; `--font-sans`/`--font-mono` rewired in globals.css; `--sq-font-mono` token added in themes.css; Plex stacks in `packages/design-system` typography tokens. All new clinical numerals (MRN/ABHA, vitals, labs, doses) render mono + `tabular-nums`.

**Signature components (§1/§6)** — 12 new components, house inline-style pattern, token-only colors, logical CSS properties:
- Domain: `ContextSpine` (name + mono MRN/ABHA, allergy flag, status dots, encounter stage), `VitalChip` (mono value, trend glyph, out-of-range aria), `RangeBar` + `LabResultRow` (value on the reference-range track), `MedOrderLine` (fixed mono columns, high-alert + second-verify affordance), `DualConfirm` (two identifiers + checkbox-gated confirm).
- Primitives: `StatusDot`, `StatusChip` (label required — never color alone; on-soft text tokens), `EmptyState`, `ErrorState` (`role=alert` + i18n retry), `OfflineBanner` (`role=status` + retry).
- +3 glyphs: `TriangleIcon`, `TrendUpIcon`, `TrendDownIcon`.

**Motion (§5)** — `StatusIndicator` and `TokenDisplay` fixed from infinite 1.8s/1.4s loops to **one 300ms single pulse**, `transform/opacity` only, `prefers-reduced-motion` disables both. StatusIndicator public API unchanged.

**§2 never-color-alone** — `PatientBanner` allergy now glyph + word + color (was color alone); its MRN moved to mono.

**i18n** — +19 keys in en + hi (allergy, trend, lab statuses, high-alert, dual-confirm copy, offline/error copy).

## Surface changes

- `/design-system` showcase gains 7 sections (Context Spine, status parts, vitals, lab rows, range bars, med orders, dual confirm, feedback states).
- `LabResultViewer` data-row type renamed `LabResultViewerRow` (zero consumers) to free the spec-required `LabResultRow` name.
- Stories 32 → 44 (StatusParts, ClinicalData, Feedback files).
- Exit demo `scripts/demo-00.14-design-system.ps1` extended 17 → 29 checks (29/29 PASS on the production build + 44-story Storybook).
- VRT: only `design-system.png` re-baselined (new sections lengthen the page); other 5 baselines byte-stable — fonts are invisible to VRT by design (`font-normalize.css` pins the stack, animations disabled).

## Gates

- `npx turbo typecheck lint test` — 20/20 tasks green, packages/ui **48/48** (was 18).
- `next build` green; Storybook build green (44 stories in `index.json`).
- VRT 6/6 after the single re-baseline.
- CI: watch this PR's run (gates incl. axe/Lighthouse/VRT/PIT).

## Kept out (unchanged backlog, documented in section-00.14 §9/§11.2)

ScanField, MARTimeGrid, BedTile, QueueRow, PrintSheet, IntelligenceRail, ApprovalCard, saved views — no chunk-0 consumer.
