# SQUAMED DESIGN SYSTEM — "The Calm Instrument"
**Status:** Design baseline for chunk 0 (packages/design-system) · Targets: PWA (Windows desktop, iPad/Android tablets, Android phones), TV, kiosk
**Standards cited:** WCAG 2.2 (by SC number) · Apple HIG foundations: `accessibility.md`, `layout.md`, `typography.md`, `color.md` · `references/web-pwa.md` (web + installed-PWA host-OS adoption)

---

## 1. DESIGN THESIS

Squamed's visual world is drawn from **objects already trusted on the ward**: patient monitors, infusion pumps, lab reports, specimen labels, whiteboards. Not consumer health apps. Not sci-fi command decks. The screen should read like a well-made instrument: quiet, dense where it must be, and unambiguous under pressure.

**The one sentence:** *a hospital screen is an instrument panel — every pixel either states a fact or helps act on one.*

**Signature element — the Context Spine.** A persistent patient bar on every patient-scoped screen: name + photo + **MRN/ABHA in tabular mono**, allergy flag, live status dots, encounter stage. It is simultaneously (a) the design's memorable element and (b) the clinical two-identifier safety rule rendered in UI (master plan §5.1). The signature belongs to this product because it *is* the product's most important rule.

**Second signature — instrument numerals.** Every clinical number (vitals, lab values, doses, money, IDs) renders in tabular monospace. Numbers align in columns, never jitter, read like equipment readouts. Body/UI text stays proportional.

**Critique of this plan (anti-default check):** A generic brief would produce Inter/Roboto + purple-gradient AI accents + rounded-3xl glassy cards. This plan instead: chooses IBM Plex **because India-first multilingual (Devanagari) needs a premium family that actually supports it** — a subject-driven choice; takes the palette from the already-approved blueprint §86 (canvas `#F7F8F6`, teal `#087F7A`); and makes the signature element a safety mechanism, not decoration. Removed accessories: shadows (borders carry structure), gradients (banned), decorative motion (one purposeful moment only). This plan would not survive translation to a fintech brief unchanged — that's the test.

---

## 2. COLOR — semantic tokens, light-first

Light mode is the product (blueprint §86: never a dark product). Dark exists only as the opt-in **Command mode** (command centre, ED board, night ops).

### Light (default)
| Token | Value | Role | Contrast vs surface |
|---|---|---|---|
| `--sq-canvas` | `#F7F8F6` | App background | — |
| `--sq-surface` | `#FFFFFF` | Cards, tables, sheets | — |
| `--sq-sunken` | `#EEF1EF` | Wells, table header, page furniture | — |
| `--sq-border` | `#DDE2DF` | Structure (1px, carries hierarchy — no shadows) | 1.4:1 non-text (decorative) |
| `--sq-ink` | `#15191A` | Primary text | **15.8:1** |
| `--sq-ink-2` | `#596267` | Secondary text, labels | **5.6:1** |
| `--sq-teal` | `#087F7A` | Identity: primary buttons, links, selection, focus ring | **4.8:1** on white (text ≥ 14px semibold or ≥ 18px); white-on-teal 4.8:1 — button-safe |
| `--sq-teal-text` | `#066A66` | Teal as small text (< 14px) | **6.2:1** |
| `--sq-critical` | `#B42318` | Critical results, BLOCK alerts, stat | **6.4:1** · tint `#FDEFED` |
| `--sq-warning` | `#B54708` | WARN alerts, pending, SLA-at-risk | **5.7:1** · tint `#FDF4E7` |
| `--sq-success` | `#067647` | Validated, completed, settled | **5.9:1** · tint `#E9F6EF` |
| `--sq-info` | `#175CD3` | Information, in-progress | **5.7:1** · tint `#EAF1FB` |

Rules (`color.md › Best practices` + WCAG 1.4.3/1.4.11):
- **One color, one meaning.** Teal never means status; status colors never decorate. Department identity is expressed by **name labels, never color** (blueprint §86).
- **Never color alone** (`accessibility.md › Vision`): every status pairs color + icon + text. Critical = ▲ glyph + word "CRITICAL" + red. Colorblind-safe under deuteranopia by construction.
- Status tints are backgrounds for chips/rows only; text on tints uses the base status color (≥ 4.5:1 verified above) — never ink-on-tint shortcuts.
- Hardcoded hex in component code is a lint failure; components consume tokens only (`web-pwa.md › Color and theming`).

### Command mode (opt-in dark)
| Token | Value |
|---|---|
| `--sq-canvas` | `#0E1416` (not pure black) |
| `--sq-surface` | `#161F22` |
| `--sq-border` | `#2A3538` |
| `--sq-ink` | `#E8EDEB` (softened white — never `#FFF`) |
| `--sq-ink-2` | `#9BA7A4` |
| `--sq-teal` | `#2BB8B0` (brightened; large text/graphics ≥ 3:1 per WCAG 1.4.3 large-text rule) |
| Status | Brightened variants, same tints inverted at 12% alpha |

Follows OS via `prefers-color-scheme` where the surface allows; Command mode is an explicit workspace setting, default "system" (`web-pwa.md › theming`).

### Anti-slop banned list (enforced in design review, not vibes)
Cream + serif + terracotta defaults · near-black + acid-green/vermilion accent · purple-blue "AI gradient" · glassmorphism/blur on any surface · neon · gradient hero numbers · big-number-over-label KPI cards · `01/02/03` markers on non-sequences · rounded-3xl mega-cards · emoji as icons · rainbow module colors · dark-mode-only styling. If a comp arrives looking like any of these, it is rejected at wireframe stage (chunk 0 process).

---

## 3. TYPOGRAPHY

| Face | Use | Why |
|---|---|---|
| **IBM Plex Sans** (Regular/Medium/Semibold only) | UI, body, headings | Premium family with full **Devanagari** — Hindi is a day-1 language; supports en + Indic without a second system (`typography.md › Minimize the number of typefaces`). Weights below Semibold banned for < 14px text (`typography.md › avoid light weights`) |
| **IBM Plex Mono** (tabular) | **All clinical numerals**: vitals, labs, doses, money, MRN/ABHA, timestamps | Instrument readout signature; column alignment in tables |

Scale (rem-based, `clamp()` fluid on web; root never locked — WCAG 1.4.4):
| Style | Size/line | Use |
|---|---|---|
| Display | 24/30 Semibold | Screen titles (sparingly — workspace headers) |
| Title | 18/24 Semibold | Section headers, sheet titles |
| Heading | 15/20 Semibold | Card headers, group labels |
| Body | 14/20 (patient-facing 16/24) | Default UI text, notes |
| Data | 13/18 **Mono Medium** | Table cells, vitals, IDs — the workhorse size |
| Label | 12/16 Medium | Field labels, chips |
| Micro | 11/14 Medium | Metadata, timestamps (floor — nothing smaller) |

- Line length 45–75ch for prose (notes, summaries) (`web-pwa.md › Typography`); tables may run wider.
- Devanagari: line-height +0.05em globally when `lang` is Indic; no all-caps (no case in Devanagari — height-caps only on Latin).
- Hierarchy survives 200% text scaling: layout reflows to stacked, tables grow rows — never clip (`layout.md › Be prepared for text-size changes`).

---

## 4. LAYOUT, DENSITY, FORM

- **4px grid.** Spacing tokens: 4 · 8 · 12 · 16 · 24 · 32 · 48.
- **Density modes:** Clinical (default; table rows 44px) and Enterprise (rows 36px, +8% data per viewport) — per-workspace, user-switchable, never mixed on one screen.
- **Radius:** 6px controls · 10px cards/sheets · 999px chips. Nothing rounder — instrument, not toy.
- **Elevation:** borders and background tone carry structure; **one** shadow token (`0 8px 24px rgba(21,25,26,.12)`) reserved for the floating layer (command bar, popovers). No glass, no blur (`liquid-glass` rule: glass belongs to floating functional layers only — and blueprint §86 bans it outright).
- **Fluid web layout:** CSS Grid + `minmax()`/`clamp()`; reflows without 2-D scroll at 320px (`web-pwa.md › WCAG 1.4.10`); `100dvh` not `100vh`; container queries for table/card switching inside panes.
- **Responsive matrix** (persona surfaces):
| Surface | Layout |
|---|---|
| Desktop ≥ 1280 | 3-pane: nav rail (72px icon rail, expandable) · workspace · intelligence rail (collapsible) |
| Tablet 768–1279 | Split view; bedside mode = 48px targets, bottom action bar |
| Mobile < 768 | Single column, task-first, bottom nav, thumb-reach zone for primary actions |
| TV | Read-only boards; 10-foot scale (min 24px text), auto-refresh, no interaction |
| Kiosk | Wizard, one question per step, 56px targets, timeout-with-recovery |

**Patient 360 (doctor, desktop):**
```text
┌──┬────────────────────────────────────────────┬─────────────┐
│▐▓│ ▣ Context Spine: Sharma, R · MRN 004821 ·  │ INTELLIGENCE│
│▓▓│   ABHA 12-3456-7890 · ⚠ ALLERGY: Penicillin│  RAIL       │
│▓▓│   IPD · Ward B 204 · Day 3  ● ● ●○        │ 2 attention │
│▓▓├────────────────────────────────────────────┤ items       │
│▓▓│ [Overview][Encounters][Labs][Meds][Docs]   │ ─────────── │
│▓▓├──────────────────────────────────┬─────────┤ ✓ critical  │
│▓▓│ Vitals  (mono, trend ↗↘, spark)  │ Active  │   result    │
│▓▓│ BP 128/82  HR 76  SpO₂ 98  T 98.4│ orders  │ ─────────── │
│▓▓│ ──────────────────────────────── │ + tasks │   OT delay  │
│▓▓│ Labs: latest w/ range bars       │ + plan  │  (inspect→  │
│▓▓│ Hb 9.8 [====|————] 13.0 LOW      │         │   act)      │
│▓▓│ Meds: active MAR strip           │         │             │
└──┴──────────────────────────────────┴─────────┴─────────────┘
```
**Ward board (nurse, tablet landscape):** bed grid tiles (status dot + name + flag chips), tap → Context Spine + task list; eMAR rows 56px for gloved touch.
**Command centre (Command mode, TV):** full-bleed board, 4 KPI columns max, status color + glyph + value in mono; no decoration; readable at 6m.

---

## 5. MOTION

- Language: 120–180ms, ease-out, `transform`/`opacity` only; interruptible; `prefers-reduced-motion` disables all but opacity fades (`web-pwa.md › Motion`).
- **The one orchestrated moment:** token-called. On queue screens the called row's status dot pulses once (300ms) and the TV board highlights next-token — mirrors the voice announcement. Purposeful: it confirms the audio event visually. Everything else is instant.
- No parallax, no confetti, no loading theatrics. Skeletons only where data > 400ms (`loading` discipline); otherwise instant render.

---

## 6. COMPONENT INVENTORY (chunk 0 build order — the platform library)

**Structural:** AppShell (rail + workspace + rail) · Context Spine · PageHeader · DataTable (density modes, sticky header, row-status strip, saved views) · Timeline · Tabs · Sheet/Drawer (one at a time, swipe/Escape out — `modality` rules) · CommandBar · IntelligenceRail · ApprovalCard (propose/approve/reject/expire states).

**Clinical domain (the differentiators):**
- `VitalChip` — value + unit in mono, trend glyph, out-of-range state.
- `LabResultRow` — value rendered **on a reference-range track** (position of value within normal band) — the instrument vernacular; abnormal end flagged by glyph, not color alone.
- `MedOrderLine` — drug, dose, route, schedule in fixed mono columns; high-alert = second-verify affordance built in.
- `StatusDot` / `StatusChip` — the color+glyph+text primitive behind every status.
- `DualConfirm` — two-identifier confirmation block; the signature component; hard-renders in every patient-scoped mutation flow.
- `ScanField` — barcode input with camera/scanner fallback.
- `MARTimeGrid`, `BedTile`, `QueueRow` (with the token-called pulse), `RangeBar`.

**Forms & feedback:** Field (label + hint + inline error via `aria-describedby`; real `<label>`s — `web-pwa.md › Forms`) · autosave draft indicator · OfflineBanner (honest state, retry — never lie about connectivity) · EmptyState (invites next action) · Skeleton · ErrorState (what happened + what to do) · Toast (success/failure, `aria-live` polite; alerts reserved for rare irreversibles) · PrintSheet (print parity for receipts/reports/prescriptions).

**State completeness:** every component ships hover, `:focus-visible`, active, disabled, loading, empty, error variants — verified in Storybook (`web-pwa.md › Interaction states`).

---

## 7. AI UX RULES (chunk 5 — no chatbot blob)

- AI output renders **inline with provenance**: `◆ AI-draft` chip, fields tagged visually distinct until signed (master plan §8).
- Approval pattern: proposal cards with Approve/Reject/Expire — never auto-applied; high-risk actions route to explicit approval UI only.
- Intelligence rail, not a chat window; capped items; "why am I seeing this" on every rail entry.
- Command bar answers cite their sources (record refs); ungrounded text is labeled inference.
- When AI flag is off, surfaces degrade to plain workflows — no empty AI shells (`generative-ai` graceful-off principle).

---

## 8. ACCESSIBILITY CONFORMANCE (WCAG 2.2 AA — chunk-exit gate)

| SC | Implementation |
|---|---|
| 1.4.3 Contrast | Values in §2 computed; verified light + dark + Command |
| 1.4.4 Resize | rem everywhere; no `user-scalable=no`; layouts survive 200% |
| 1.4.10 Reflow | No 2-D scroll at 320px; tables → stacked cards via container query |
| 1.4.11 Non-text | Focus ring, borders, status dots ≥ 3:1 |
| 1.4.12 Spacing | Text spacing overrides don't clip |
| 2.1.1 / 2.1.2 | Full keyboard paths; reception/lab/pharmacy function-key flows; no traps |
| 2.4.7 Focus | 2px `--sq-teal` ring, 2px offset; never `outline: none` bare |
| 2.5.8 Target | ≥ 24px floor; **44px standard, 48px bedside/kiosk** (gloves, motion, panic — above web minimum deliberately) |
| 4.1.2 Names | Semantic HTML; icon-only controls carry `aria-label` |
| 4.1.3 Status | `aria-live` announces: result validated, token called, task escalated, save/autosave |
| 2.3.1 Motion | Reduced-motion honored; nothing flashes > 3/s |
| forced-colors / prefers-contrast | Windows High Contrast tested per release |
| RTL | Logical CSS properties everywhere (future Arabic/Urdu markets) |

Environment overrides above platform minimums: bedside tablets raise contrast (glare), kiosk adds timeout-with-recovery, TV disables interaction entirely (`accessibility.md › Cognitive`: no auto-dismiss without control on kiosk either — kiosk timeout shows "Press to continue").

---

## 9. IMPLEMENTATION (chunk 0)

- Tokens: CSS custom properties `--sq-*` in `packages/design-system` → Tailwind theme mapping; themes = token sets (light, command, tenant-brand overlays from master plan §14 — brand may swap teal-for-accent and logo, never semantic colors).
- Iconography: single 24px-grid stroke set (1.5px stroke matching data-weight); no emoji, no mixed languages; every icon labeled.
- QA automation: Storybook (all states) · Playwright visual regression per component (light + command + one brand overlay) · axe-core in CI (zero critical) · Lighthouse budgets from `01-ENGINEERING-STANDARDS.md` · WCAG spot-audit per chunk exit.
- Font loading: Plex self-hosted, subset per script (Latin + Devanagari), `font-display: swap`, fallback stack metrics-compatible.

**Removed-one-accessory check:** shadows → replaced by borders (§4). Nothing further removes without loss — the system is already lean.
