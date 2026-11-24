# Chunk 06 PIT tracker

Seed frame: 00.00. Keep one line per PIT.

| ID | Description | Status | Run | Notes |
|----|-------------|--------|-----|-------|
| 06.01 | multiHospitalGroupCoverage | PASS | local (CI pending) | QID cross-hospital match 100; access 403 before share -> shared -> 403 revoked; outside group 403; cross-group share 400; consent patient/purpose guards; config group->hospital override; referral inbox/accept/sent/complete + re-complete 409; directory 2 hospitals; record.shared_consented + referral events; catalog 52 |
| 06.04 | workforceHrCoverage | PASS | local (CI pending) | roster draft -> 3 shifts -> duplicate 409 -> publish -> frozen 409; leave approved then 409; attendance upsert one row; credential scan one renewal task, re-scan no duplicate; ratio 1/2=0.5; payroll 1 shift/8h; hr.roster_published + hr.credential_expiring; catalog 54 |
| 06.05 | opsModulesCoverage | PASS | local (CI pending) | indent 150 QAR -> early order 409 -> PO -> GRN -> stock 10 -> double receive 409 -> vendor performance; tray cycle -> issue-in-cycle 409 -> PASS -> issue to OT case -> trace 1 cycle/1 issue -> return; asset -> WO (UNDER_MAINTENANCE) -> complete downtime 50 -> ACTIVE; ED ESI2 RESUS -> treatment -> discharge -> reopen 409 -> dashboard 1; procurement denied CSSD 403; six ops events; catalog 60 |
| 06.03 | patientPortalCoverage | PASS | local (CI pending) | link + duplicate 409; own MRN/ABHA; own bill only; cross-patient pay 403; own invoice paid due 0; appointment booked for portal patient; prescription/lab/care plan; notification read; push subscribe + hijack 403 + configured=false; unlinked 403; SC-5 open (push delivery) |
