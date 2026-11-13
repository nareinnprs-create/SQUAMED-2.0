# CLINICAL CONTENT PLAN
**Owner:** clinical lead; approval gate = clinical lead + tech lead (master plan §11). Content ships as versioned config documents (`config_document`), never code — this file schedules it and fixes structure.

## 1. Content taxonomy (all versioned, tenant-overridable within safety bounds)
| Type | Examples | Safety note |
|---|---|---|
| Note templates | SOAP (general + per-specialty), procedure note, H&P | Sections map to structured fields; free text allowed only alongside coded fields |
| Order sets | "Fever OPD", "Pre-op basic", "Chest pain rule-out" | Sets carry codes; favorite-first ordering |
| Care plans | Diabetes, HTN, post-op wound, pneumonia, DVT prophylaxis | Problem→intervention→goal structure (chunk 4) |
| Risk scales | GCS, SOFA, Braden, Morse fall, Aldrete, APA/Site scores | Calculators informational; thresholds from config; changes audited |
| Vitals sets | OPD baseline, ICU 1–2h set, pre/post-op set | Units fixed; ranges from reference master |
| Discharge summary structure | Fixed sections: dx (coded), course, meds (rec), advice, follow-up | Med-rec task hook enforced (CS-11) |
| Patient leaflets | Advice sheets, procedure prep (en + hi + 1 regional) | Reading level checked; layout from print sheet component |
| Referral templates | Intra-hospital, cross-hospital (chunk 6), external | Carry coded problem + findings |
| Specialty packs | 22 blueprint specialties (chunk 7) = bundle of the above + coding favorites + flowsheet defaults | Installing a pack is config-only (<1 day exit test) |

## 2. Authoring workflow
Draft (clinical lead) → specialist advisor review (external, per specialty) → safety review (interactions with alert rules/high-alert list) → version + approve → publish to config registry → synthetic-hospital verification run → tenant rollout via flag. Emergency content changes: clinical lead + tech lead joint approval, retrospective full review within 5 days.

## 3. Delivery schedule (content must land **before** the chunk that needs it)
| By end of | Content due |
|---|---|
| Chunk 1 kickoff −2 wk | OPD SOAP template, vitals sets, allergy list structure, top-500 ICD-10 codes mapped, 10 common order sets, 5 basic specialty templates (Gen Med, Peds, OB-GYN, Ortho, Derma), consult receipt + advice leaflet |
| Chunk 2 −2 wk | Lab panels + reference ranges (age/sex), radiology report templates (Xray/US/CT basic), formulary tier-1 (500 SKUs), antibiotic stewardship starter rules |
| Chunk 3 −2 wk | Coding handbook subset, package definitions (10 surgical/diagnostic), payer doc checklists |
| Chunk 4 −2 wk | Nursing assessments, 10 care plans, 5 risk scales, WHO surgical checklist config, anaesthesia record template, blood bank SOPs, discharge summary master |
| Chunk 5 −2 wk | Scribe eval gold sets (200 consults, 5 specialties), note-template fields AI fills |
| Chunk 7 −4 wk | All 22 specialty packs, remaining scales/sops, mass-casualty content |

## 4. Content QA
Monthly spot audit (random 5% of template instances in test hospital) vs template + coding accuracy; advisor sign-off logged; template version history in audit (content is safety-critical — treated like code, reviewed like code).
