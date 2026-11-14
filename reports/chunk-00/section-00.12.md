# Section 00.12 — Consent + notifications

## 1. Scope
Consent records (purpose/scope/expiry, eSign-ready signature capture), withdrawal, channel interface: email adapter live (SMTP), SMS/WhatsApp adapters code-complete against provider sandboxes (EXTERNAL-BLOCKED on DLT header/WABA approval), DND rules, consent-check-before-send.

## 2. Built (current tree)
- `notification/` (5 files: Notification, NotificationService, NotificationController/Dtos, repository) — in-app notifications, unread-count/list/read/read-all.
- `communication/` (12 files: Channel, ChannelMember, Message, MessageAttachment, MessageRead + repositories + service + controller) — internal channels/messages with read compliance.
- Migrations `V19__tasks_notifications.sql`, `V20__communication.sql` (+ permission seed V21).
- Web: `apps/web/src/app/(app)/communication/` page + `components/notification-bell.tsx`.

## 3. Contracts delivered
- In-app notification APIs + communication channel/message/read APIs (PIT-asserted).
- NOT delivered: consent records/withdrawal/eSign (zero grep hits for consent in backend modules), email adapter (no SMTP anywhere), SMS/WhatsApp adapters (none), DND rules, consent-check-before-send.

## 4. Gates
1. FAIL — named contents missing: consent records, withdrawal, eSign capture, email adapter (chunk-00 requires it LIVE — it is absent, not merely blocked), DND, consent-check-before-send.
2. PASS — CI run 43548016617 (gate 2 green).
3. PASS — CI run 43548016617 (gate 3 green).
4. PASS — CI run 43548016617 (gate 4 green).
5. PASS — CI run 43548016617 (gate 5 green); named tests below.
6. PASS — CI run 43548016617 (gate 6 green) (communication page in axe scope).
7. PASS — CI run 43548016617 (gate 7 green).
8. FAIL — schema-registry harness not yet wired (00.02 scope).
9. FAIL — `V19__tasks_notifications.sql` + `V20__communication.sql` exist; tables hospital-scoped, RLS cross-tenant denial suite ABSENT until 00.04 lands (ADR-13).
10. PASS — AuditAspect intercepts CommunicationController/NotificationController writes; audit rows asserted PIT-side.
11. PASS — this report.
12. PASS — PIT `communicationReadComplianceCoverage` covers channel→message→read-receipt journey (API-level slice).

## 5. Tests
PIT: `communicationReadComplianceCoverage`, `collaborationAndWorkflowJourney` (notification fan-out + read-all) — green, run 43548016617. Coverage ≥0.80 merged enforced.

## 6. Accessibility
communication page in axe gate scope; run 43548016617 gate 6 green (zero critical/serious).

## 7. Issues found during build
Baseline section — see BASELINE-AUDIT.md §2.1 notification+communication row and §6 gap row (00.12 consent core). Verified current tree: zero consent/SMTP/DND code; channel adapters absent (not code-complete-against-sandbox as chunk file requires).

## 8. External blockers
E2 — DLT header approval (SMS), E3 — WABA approval (WhatsApp), per ledger exp:E2/E3. Note: email (SMTP) has NO external dependency and is still missing — that gap is build debt, not external.

## 9. Deviations from chunk file
Zero; missing contents unbuilt. SMS/WhatsApp can only ever reach "code-complete" until E2/E3 clear; email must be built regardless.

## 10. Exit-demo slice
PIT `communicationReadComplianceCoverage` (create channel → post message → mark read → compliance assertions) — green in run 43548016617. Consent-gated send demo impossible until consent core exists.
