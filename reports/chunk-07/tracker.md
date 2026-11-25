# Chunk 07 PIT tracker

Seed frame: 00.00. Keep one line per PIT.

| ID | Description | Status | Run | Notes |
|----|-------------|--------|-----|-------|
| 07.05 | fullEdTraumaCoverage | PASS | CI 45432011945 | ATS scale + duplicate/level 400; resus/acute bays + duplicate/type 400; bay OCCUPIED, double-assign 409; trauma once then 409; mass casualty once then 409 + stand-down; dashboard resus/trauma/mass-casualty; two events; catalog 63 |
| 07.08 | qmsIpcLegalCoverage | PASS | CI 45450152195 | indicator upsert ON->OFF one row; CAPA close 409; audit complete; risk 16 + bounded; CLABSI resolve 409 + type 400; outbreak dup 400 + close; hygiene 90.00% + impossible 400; hold release 409; summary counts |
| 07.09 | labSupportDepthCoverage | PASS | CI 45544449085 | micro culture→ID→AST→final + antibiogram 100/0%; histo accession→gross→slides→sign-out 409/400; NAT positive discards, negative+TTI clears, dup assay 409, bad result 400; kitchen menu dup 400, plan count 1, start/ready/tray/deliver once 409, cross-tenant 400/403 |
| 07.01 | aiOrchestrationCoverage | PASS | CI 45627032693 | one blocker per domain seeded; 50/50 primary-blocker accuracy; executive synthesis = 6 agents + critical blocker + owner/action; 9 personas eval 10/10 unauthorized 0; cross-domain proposal 400 |
| 07.04 | fullFinancialsCoverage | PASS | CI 45639038785 | chart 13; invoice 1000+5%=1050, payment, refund 50; AP 300/100; post-operations 5 idempotent; TB balanced AR 50 cash 900; budget 5100 actual 300 variance 200; asset 12000/12 dep 1000; P&L -300; BS balanced; aging 50; forecast 3; GST 1000/25/25 file-once 409; bank match 1/1; close 0.00 once 409; cross-tenant 0 |
| 07.02 | voiceOperatingInterfaceCoverage | PASS | CI 45646897017 | navigate en/hi + screen-aware; search; task draft voice-approve once 409; reject path; discharge + Hindi med HIGH + approve 403; unknown ESCALATED 0.40; help; cross-tenant 0; blank 400 |
| 07.07 | alliedAndPatientExperienceCoverage | PASS | CI 45664781492 | PHYSIO referral lifecycle + discipline 400; schedule/sign/complete once 409; completion needs signed session 400; interaction template + close 409; intl close 409; feedback promoter/detractor task, score 11 400, resolve 409, NPS 0.00; cross-tenant 0 |
