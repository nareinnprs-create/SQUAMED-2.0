# Section 05.04 — Intelligence rail (chunk 5)

**Verdict: COMPLETE → GATE-12 PASS (partial: act/inspect links owed to the IPD/OT screens)**. Gate-run assessment of the CURRENT tree @ PR #106 (chunks 0–4 + 05.01/05.03/05.07 merged + this PR). CI evidence: **run 45215766050 — all 7 gates green**. Named PIT `aiRailAttentionCoverage` green in the same run.

## 1. Scope
Per `chunk-05-ai-intelligence.md` §3: a contextual attention list assembled from governed events (critical results, discharge blockers, OT delays, claim denials) with act/inspect, dismissal reasons tracked and alert-fatigue caps.

## 2. Key artifacts
**Migration `V88__ai_attention_rail.sql`** (additive; rollback note inside): `ai.attention_item` with a per-hospital dedupe key.

**Backend:** `AiRail.kt` (scan/raise/refresh/clear/dismiss + events), `AiController.kt` (`/ai/rail/items`, `/ai/rail/items/{id}/dismiss`), `EventCatalog.kt` (`AttentionItemRaised/Cleared`, catalog 49).

**Web:** AI page **Rail** tab (severity, type/source/detail, dismissal reason, Dismiss).

## 3. Gap closure
- ✔ No rail → items derived from discharge blockers, OT delays, pending AI approvals, overdue tasks.
- ✔ Dismissal reasons tracked; dismissed items do not re-raise (dedupe across statuses).
- ✔ Cap 20, high severity first.
- ⚠ Act/inspect buttons limited to dismissal until IPD/OT screens exist; the item carries the source ref.
- ⚠ Claim denials are not yet an item type (03.04 insurance is externally gated).

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS (partial) | Four item types + dismiss/dedupe/cap; inspect links owed |
| 2 Zero-stub | PASS | CI branch run 45215766050 |
| 3 Types | PASS | CI branch run 45215766050 (`tsc --noEmit` clean locally) |
| 4 Lint + format | PASS | CI branch run 45215766050 |
| 5 Tests | PASS | CI branch run 45215766050; named PIT below |
| 6 Accessibility | PASS (caveat) | Rail tab uses labelled inputs and buttons; /ai page outside axe `SCAN_ROUTES` |
| 7 Design system | PASS | @squamed/ui controls |
| 8 Contracts | PASS | Snapshot `b8a7baa6…` + codegen committed |
| 9 Data | PASS | V88 additive; dedupe unique per hospital |
| 10 Audit | PASS | Dismissal records actor + reason; raised/cleared events |
| 11 Docs | PASS | This report; LEDGER row 05.04 COMPLETE; tracker |
| 12 Exit item | PASS (partial) | Rail slice of the chunk-5 exit; scribe (05.05) and agent personas (05.06) separate |

## 5. Tests
Named PIT **`aiRailAttentionCoverage`**: overdue discharge + delayed OT + pending draft + overdue task all appear, high severity first, ≤20; dismiss keeps the reason, re-dismiss 409 `AI_ATTENTION_NOT_OPEN`, the dismissed blocker does not re-raise; `ai.attention_item_raised/cleared` in the outbox.

Regressions: `aiGovernanceRegistryCoverage`, `eventFabricCoverage` (catalog 49).

## 6. Accessibility
Labelled reason input, real buttons, status via text/badges.

## 7. Dual review — adversarial pass
1. **No source mutation** — scan only reads; acting on an item goes through the source workflow.
2. **Dedupe** — unique (hospital, key); a cleared key is never re-raised, so a dismissal sticks until the condition changes identity.
3. **Resolution clearing** — open items whose condition disappeared are cleared with `condition resolved`.
4. **Tenant isolation** — every lookup is hospital-scoped.
5. **Cap/order** — 20 items, severity rank then newest.
6. **Task scope** — tasks with null hospital are treated as tenant-visible (legacy rows) - documented.
7. **Scan cost** — four bounded queries per refresh; no event fan-out.

## 8. Deviations from contract
- Derived on scan, not pushed on every event.
- Inspect/act links owed to the missing IPD/OT screens.
- Per-role caps are a single global 20 (role model on the rail owed).
- Claim-denial items await 03.04.

## 9. Session trail
- A compiler warning (-Werror) exposed a redundant null check on the non-null `scheduledStartAt`; removed.
- Docker Desktop dropped once; compose restarted before the PIT run.
