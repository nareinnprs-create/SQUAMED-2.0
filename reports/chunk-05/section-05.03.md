# Section 05.03 — Command bar (?K) (chunk 5)

**Verdict: COMPLETE → GATE-12 PASS (partial: intelligence rail 05.04 and eval suite 05.02 separate)**. Gate-run assessment of the CURRENT tree @ PR #104 (chunks 0–4 + 05.01 merged + this PR). CI evidence: **run 45196111696 — all 7 gates green**. Named PIT `aiCommandBarGroundingCoverage` green in the same run.

## 1. Scope
Per `chunk-05-ai-intelligence.md` §2: intent routing (navigation, permission-aware retrieval with citations, action-drafting as draft proposal → approval UI), and the "no ungrounded clinical answers possible" rule (§SAFETY rule 3).

## 2. Key artifacts
**Migration `V86__ai_action_drafts.sql`** (additive; rollback note inside): `ai.action_draft` + queue index.

**Backend:** `AiActions.kt` (propose/approve/reject/list, TTL, scope check, governed execution, events), `AiIntentService.kt` (citations, DRAFT_TASK/DRAFT_DISCHARGE intents, draft render branches), `AiService.kt` (grounding guard, draft path, command trace), `AiController.kt` (draft/queue/approve/reject endpoints), `EventCatalog.kt` (4 AI action events).

**Web:** `command-bar.tsx` (Sources line, Draft proposal panel with Approve/Reject, inline decision errors), `command-bar.test.tsx` (8 its).

## 3. Gap closure (from the gate-run report §7)
- ✔ No citations/grounding → every record-backed intent carries citations; `AI_UNGROUNDED_ANSWER` if absent.
- ✔ No approval UI → inline draft panel; approval executes through `TaskService` / `AdmissionService` with the approver's scopes.
- ✔ Commands not logged → command capability trace (05.01) with redaction.

## 4. Gates 1–12
| Gate | Result | Evidence |
|---|---|---|
| 1 Functional completeness | PASS | PIT: citations, draft → queue → approval/rejection, governed execution, scope denial, events |
| 2 Zero-stub | PASS | CI branch run 45196111696 |
| 3 Types | PASS | CI branch run 45196111696 (`tsc --noEmit` clean locally) |
| 4 Lint + format | PASS | CI branch run 45196111696 |
| 5 Tests | PASS | CI branch run 45196111696; named PIT below |
| 6 Accessibility | PASS (caveat) | Draft panel is keyboard-reachable buttons with an aria-label; the /ai page remains outside the axe `SCAN_ROUTES` set (unchanged caveat) |
| 7 Design system | PASS | Uses @squamed/ui Button/Badge tokens |
| 8 Contracts | PASS | Snapshot `d5cdfda5…` + codegen committed; parity green |
| 9 Data | PASS | V86 additive; hospital-scoped drafts; queue index |
| 10 Audit | PASS | Draft decisions are controller-mediated (AuditAspect); events carry actor + result ref |
| 11 Docs | PASS | This report; LEDGER row 05.03 COMPLETE; chunk-05 tracker |
| 12 Exit item | PASS (partial) | Command-bar slice of the chunk-5 exit demo; rail/eval owed |

## 5. Tests
Named PIT **`aiCommandBarGroundingCoverage`** (fresh hospital): count/analytics citations (`count.patients`, `analytics.overview`), patient search cites the record id, `create task` draft on the approval queue, doctor approving a discharge plan 403 `AI_SCOPE_DENIED`, admin approval creates the task and sets `expectedDischargeAt`, reject recorded + re-approve 409 `AI_ACTION_NOT_PENDING`, outbox has `ai.action_proposed/approved/rejected`.

Regressions: `aiGatewayGovernanceCoverage`, `aiGatewayAndInteropJourney`, `eventFabricCoverage` (catalog 46), `consentNotificationCoverage`.

## 6. Accessibility
Draft panel: `role="group"` + aria-label, real buttons, no colour-only status (status text rendered). The page-level axe scan still does not include /ai.

## 7. Dual review — adversarial pass
1. **Grounding guard placement** — after parsing, before render; a record-backed intent without citations throws instead of returning prose.
2. **Negative answers** — an empty patient search cites the search query itself, so "no matches" is grounded too.
3. **Approval is the trust boundary** — proposal is open to `ai:chat` users; approval re-checks the underlying permission (`task:manage:task`, `ipd:write:admission`), so an assistant cannot escalate.
4. **Idempotence/decision race** — only `PROPOSED` can be decided; a second decision is 409.
5. **Expiry** — TTL 24 h, enforced on decision (lazy sweeper not built); expired drafts emit `ai.action_expired`.
6. **Payload trust** — approve reads only known keys and parses UUIDs/instants defensively; unknown keys are ignored.
7. **Tenant isolation** — drafts are found by id **and** hospital; a foreign id is 404.
8. **Trace** — commands log capability `command`, redacted text, 0 tokens (no model call).

## 8. Deviations from contract
- Drafts expire lazily (no scheduled sweeper).
- Citations are record references, not sentence-level spans.
- The approval UI is inline in the command bar; the dedicated /ai workspace is unchanged.

## 9. Session trail
- The first PIT used a doctor to prove the approval denial, but DOCTOR/NURSE hold `task:manage:task` (V50), so the denial moved to the discharge plan (DOCTOR lacks `ipd:write:admission`).
- Draft titles initially lost case (the parser lowercases for matching); the title now comes from the original query.
- Docker Desktop dropped once; compose restarted before the PIT run.
