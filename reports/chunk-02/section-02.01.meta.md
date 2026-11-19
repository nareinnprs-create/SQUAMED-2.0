---
title: "Section 02.01 — Analyzer intake (Mirth)"
description: "Analyzer message log, ASTM bridge adapter, quarantine+alert (zero silent drops), version-controlled Mirth channel config, PIT analyzer_intake_zero_silent_drop_coverage (PASS 44488415740, PR #66)."
---
- Open (2025-06-12): 21 CFR Part 11 E6 spec
- Closed (2025-06-12): —
- Pending (2025-06-12): owner responsibilities exp:E10 hardware soaks
---
- Chain-of-custody: Mirth (ADR-14) → SQM analyzer intake → analyzer_message_log (ACCEPTED|QUARANTINED).
- Version control: `backend/squamed-core/src/main/resources/mirth/analyzer-intake.channel.json`, `V62__analyzer_intake.sql`, `AnalyzerIntakeService.kt`, `AstmFrames.kt`, `AnalyzerController.kt`, `AnalyzerIntakeConfigTest.kt`, PIT `analyzer_intake_zero_silent_drop_coverage`.
---
- media-use hook: no media required for this section; ongoing supplier announcements required (E6 spec).