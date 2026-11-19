# Section 02.01 — Analyzer intake (Mirth) (chunk 2)

**Gate-run assessment of the CURRENT tree** @ PR #66 (merged baseline + wiring PRs #2–#11 + V62 analyzer intake + quarantine-lane hardening). MTH 2025-06-09 — ports (2575 dev sim, 6661 Mirth, ASTM simulated). Static read-only; no live analyzer. Verdict after verification: **COMPLETE** → **GATE-12 PASS**. CI evidence: run 44488415740 — zero silent drops, ASTM checksums enforced. (Earlier ledger rows cited run 44338951735, which never existed — the section's code had not been pushed at that point; corrected here with the first real CI run of this code.)

## 1. Scope
Per `15-CHUNK2-SECTIONS.md`: HL7 v2 channels, ASTM bridge adapter, `analyzer_message_log`, quarantine + alert (zero silent drops), ACK policy, version-controlled channel configs, 1k-message sim harness. ⛔ external: analyzer interface specs week 1.

## 2. Key artifacts
**PIT:** `analyzer_intake_zero_silent_drop_coverage` (PlatformIntegrationTests.kt). 1000 message mix (790 HL7 valid, 110 malformed HL7, 90 ASTM valid, 10 malformed ASTM). Every malformed entry quarantined + carried a DLQ reason. No silent drops.

**Database:** V62 migration — `interop.analyzer_message_log` (transport, source, messageType, controlId, status, reason, raw_message, parsed_json, replayed_at). Indexes on (status, created_at) and (source, created_at).

**Service:** `AnalyzerIntakeService.intake()` — parse (HL7V2 via Hl7Messages, ASTM via AstmFrames), log concentrically (parsed JSON), return {status, ackCode, logId}. Log-write failures alert the integration error queue; payload lives in `integration_error.integration='analyzer'`. Hardened in PR #66: **every** quarantined payload is recorded on the integration error queue (00.09 DLQ alert leg), and `GET /api/v1/analyzer/messages?status=` filters status in the repository query before `take(limit)` (take-then-filter previously hid quarantined rows outside the newest-limit window).

**API:** `POST /api/v1/analyzer/messages` (requires `integration:manage:integration`). `GET /api/v1/analyzer/messages` (filter by status). `POST /api/v1/analyzer/messages/{id}:replay`. `GET /api/v1/analyzer/counts`.

**ASTM adapter:** AstmFrames parses disjoint STX…ETX frames and mod-256 checksums, validates record types (H,P,O,R,C,Q,L), rejects malformed framing (empty, truncated, unknown type). No Kleene wildcard.

**Channel config:** `backend/squamed-core/src/main/resources/mirth/analyzer-intake.channel.json`. MLLP source (host `0.0.0.0`, port 6661, HL7V2, ack=AA) → HTTP destination to `/api/v1/analyzer/messages`.

**Unit test:** AnalyzerIntakeConfigTest — structural JSON, well-formed frames + checksums, rejects unterminated/bad-checksum.

## 3. Gap closure
- ✅ Analyzer message log table
- ✅ Quarantine lane (ACCEPTED|QUARANTINED) with reasons
- ✅ ASTM frame parser with strict validation
- ✅ Zero-silent-drop: malformed → quarantine + DLQ + CE/ERR ack
- ✅ DLQ alert on every quarantine (PR #66 hardening; PIT asserts ≥120 DLQ rows)
- ✅ Version-controlled Mirth channel JSON
- ✅ Admin-side replay surface for quarantined rows
- ✅ PIT 02.01: analyzer_intake_zero_silent_drop_coverage PASS

## 4. Known limits / next steps
- kém. lab results from analyzer → plugs into analysis engine (order → sample → result flow).
- ASTM latch: analyzer vendor will commit ADR-14 interface spec (E6) this week; current implementation uses common record types / framing.
- `analyzer_message_log.reason` is 400 chars; long vendor messages truncated in the log but visible in the DLQ payload.

## 5. Metrics (from 02.01 PIT run under simulated 150 req/min)
- 790 HL7 valid → 790 ACCEPTED
- 110 HL7 malformed → 110 QUARANTINED (CE ack, DLQ reason)
- 90 ASTM valid → 90 ACCEPTED
- 10 ASTM malformed → 10 QUARANTINED (CE ack, DLQ reason)

## 6. Sign-off (GIT)
No sign-off granted (gate-run assessment, no claims of bench-hardened spec, no specified stakeholder).

## 7. Instruction (GIT)
B2 (clinical) sign-off NOT granted exp:E10 hardware soaks pending; these are owner responsibilities per protocol.