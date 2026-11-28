The 06.09 exit demo still told the reader that three delivered things were owed.

Verified on merged main before touching it: **07.11 exit demo 32 passed / 0 failed**, **06.09 exit demo 28 passed / 0 failed** — no regression from this session's changes to `EncounterService`, the AI/BI controller annotations, the `/api/**` interceptor or the audit verify endpoint.

Then corrected the demo's own reporting:

- **1,500-user load** — was listed as owed; it has been run (0 errors). The honest status is now "NFR unverified — host-dominated", not "owed".
- **06.06 BI/ClickHouse, 06.07 outbound FHIR** — were listed as owed; both are built and merged.
- **06.02 telemedicine** — was listed as "video SDK"; the guideline rules are built, so the owed item is now the SDK and payment link specifically.
- **Datacenter-kill DR drill** — unchanged, still owed, still needs a second region.

Re-ran the demo after the edit: **28 passed, 0 failed**, with the corrected lines.

No code change. It is a PR rather than an inline edit because the exit demo is evidence, and its evidence text should be reviewable.
