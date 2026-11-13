# COMPLIANCE PACK — DPDP · records law · audit evidence
**Scope:** technical compliance surface of the platform (India first). Legal review of terms/contracts is business work — flagged where it touches code.

## 1. DPDP Act 2023 → platform obligations map
| DPDP requirement | Platform implementation | Verify |
|---|---|---|
| Notice + consent at collection | Consent engine v1 (chunk 0): purpose-scoped notice at patient registration, signature evidence | Registration flow shows notice; consent row created |
| Purpose limitation | Every data use tagged to purpose (treatment / insurance / communications / ABDM share); ABAC scopes follow | Purpose field on consents + access logs |
| Consent withdrawal | Withdrawal API + propagation: comms stop (DND), shares revoke; clinical record retention unaffected (law) | CS case in catalog |
| Erasure / right to correction | Erasure tool for **non-clinical** data (marketing prefs, contact artifacts); clinical records retained under medical regulations (below) — correction = versioned amendment | Erasure runbook + audit event |
| Children's data | <18 ⇒ guardian consent mandatory, no tracking/behavioral ads ever (hard-coded product rule) | Registration age gate test |
| Security safeguards | §07 program | Pen-test + audit evidence |
| Breach notification | IR runbook §07.6 → Data Protection Board + affected individuals, templates pre-drafted | Drill report |
| Significant Data Fiduciary duties (if designated) | DPO records, annual audit, impact assessment templates | Compliance calendar |
| Cross-border transfer | Allowed to notified countries; default: India-region processing for PHI (master plan §8) | Egress allowlist audit |

## 2. Medical records law (India) → retention schedule
| Record class | Retention | Enforcement |
|---|---|---|
| Indoor/IPD records | 10 years | retention_class `clinical_10y`; erasure job never touches (legal retention overrides DPDP erasure) |
| OPD records | 10 y (conservative; state CEA minimums vary — configurable per tenant) | same |
| Minors | Until 10y past majority | retention solver computes from DOB |
| Medico-legal cases | Legal hold — indefinite until released | `legal_hold` flag blocks all erasure; release = 2-person + audit |
| Money/audit trails | 10y | WORM audit + GL journals |
| AI audio | 30–90 d (config, default 30) | lifecycle purge |
| Communications metadata | 3 y | lifecycle |
Erasure jobs run monthly, produce an audit event + report; attempts blocked by legal hold are listed, not executed.

## 3. Audit evidence pack (what we hand an auditor/hospital board — generated, not hand-made)
1. Hash-chain verification report (chain integrity, per period) 2. Access review attestations (quarterly export) 3. Consent register (who/what/when/signature evidence) 4. Breach drill + incident postmortems 5. Retention/erasure run reports 6. Pen-test summary + remediation status 7. Role-binding export per audit period 8. eSign evidence records. All exportable from `/audit/query` + admin endpoints; stored under WORM alongside audit stream.

## 4. Consent artifact schema (platform-native + ABDM-mappable)
`{id, patient, purposes[] (treatment|insurance|comms|abdm_share|research), scope (data classes), channels[] (for comms), valid_from, expires_at, evidence{type: esign|biometric|paper_scan, ref, signer}, withdrawn_at, version}` — ABDM artefact id attached when the consent originated/was registered in ABDM. Every access check that filters by consent cites the artifact id in the audit event.
