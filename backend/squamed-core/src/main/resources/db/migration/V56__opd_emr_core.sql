-- V56: OPD EMR core (01.04) - specialty note templates, coded diagnosis catalog,
-- procedures, referrals, note-signature (E5 PIN fallback), allergy-aware Rx columns,
-- follow-up appointment link. Catalog/template rows are global (hospital_id NULL).
-- Rollback: ALTER TABLE clinical.clinical_note DROP COLUMN signature_method, DROP COLUMN signature_data, DROP COLUMN esign_transaction_id;
--           ALTER TABLE clinical.prescription DROP COLUMN override_reason, DROP COLUMN allergy_status;
--           ALTER TABLE clinical.encounter DROP COLUMN follow_up_appointment_id;
--           DROP TABLE clinical.referral; DROP TABLE clinical.procedure;
--           DROP TABLE clinical.diagnosis_catalog; DROP TABLE clinical.note_template;

CREATE TABLE IF NOT EXISTS clinical.note_template (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id          UUID,                                       -- NULL = global catalog
    specialty            VARCHAR(80) NOT NULL,
    name                 VARCHAR(120) NOT NULL,
    note_type            VARCHAR(30) NOT NULL DEFAULT 'progress',
    subjective_template  TEXT,
    objective_template   TEXT,
    assessment_template  TEXT,
    plan_template        TEXT,
    is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_note_template_specialty
    ON clinical.note_template (specialty, is_active);

CREATE TABLE IF NOT EXISTS clinical.diagnosis_catalog (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id   UUID,                                          -- NULL = global catalog
    code          VARCHAR(20) NOT NULL,
    system        VARCHAR(20) NOT NULL DEFAULT 'ICD-10',         -- ICD-10 | SNOMED
    display       VARCHAR(255) NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

-- Global catalog entries are unique per (system, code); per-hospital copies may shadow them.
CREATE UNIQUE INDEX IF NOT EXISTS uq_diagnosis_catalog_global
    ON clinical.diagnosis_catalog (system, code) WHERE hospital_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_diagnosis_catalog_lookup
    ON clinical.diagnosis_catalog (system, code, is_active);

CREATE TABLE IF NOT EXISTS clinical.procedure (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID NOT NULL,
    encounter_id    UUID NOT NULL,
    patient_id      UUID NOT NULL,
    procedure_code  VARCHAR(40),
    procedure_name  VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'planned',      -- planned | completed | cancelled
    performed_at    TIMESTAMPTZ,
    performed_by    UUID,
    outcome         TEXT,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_procedure_status CHECK (status IN ('planned', 'completed', 'cancelled'))
);

CREATE INDEX IF NOT EXISTS idx_procedure_lookup
    ON clinical.procedure (hospital_id, encounter_id, status);

CREATE TABLE IF NOT EXISTS clinical.referral (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id       UUID NOT NULL,
    encounter_id      UUID NOT NULL,
    patient_id        UUID NOT NULL,
    from_doctor_id    UUID,
    to_department_id  UUID,
    to_specialty      VARCHAR(80),
    target_facility   VARCHAR(200),
    reason            TEXT NOT NULL,
    urgency           VARCHAR(20) NOT NULL DEFAULT 'routine',    -- routine | urgent | emergency
    status            VARCHAR(20) NOT NULL DEFAULT 'pending',    -- pending | accepted | completed | cancelled
    notes             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_referral_urgency CHECK (urgency IN ('routine', 'urgent', 'emergency')),
    CONSTRAINT ck_referral_status CHECK (status IN ('pending', 'accepted', 'completed', 'cancelled')),
    CONSTRAINT ck_referral_target CHECK (to_department_id IS NOT NULL OR to_specialty IS NOT NULL OR target_facility IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_referral_lookup
    ON clinical.referral (hospital_id, encounter_id, status);

ALTER TABLE clinical.clinical_note
    ADD COLUMN IF NOT EXISTS signature_method    VARCHAR(20),
    ADD COLUMN IF NOT EXISTS signature_data      TEXT,
    ADD COLUMN IF NOT EXISTS esign_transaction_id VARCHAR(100);

ALTER TABLE clinical.prescription
    ADD COLUMN IF NOT EXISTS override_reason  TEXT,
    ADD COLUMN IF NOT EXISTS allergy_status   VARCHAR(20);   -- checked | warn_override

ALTER TABLE clinical.encounter
    ADD COLUMN IF NOT EXISTS follow_up_appointment_id UUID;

-- Specialty note templates (global): SOAP skeletons for the 8 highest-volume OPD specialties.
INSERT INTO clinical.note_template (specialty, name, note_type, subjective_template, objective_template, assessment_template, plan_template)
VALUES
    ('General Medicine', 'General OPD consultation', 'progress',
     'Presenting complaints: {{complaints}}. Duration: {{duration}}. Relevant history: {{history}}.',
     'Vitals stable/abnormal (see vitals chart). General and systemic examination: {{exam}}.',
     'Working diagnosis: {{diagnosis}}. Differentials: {{differentials}}.',
     'Investigations ordered: {{investigations}}. Treatment started: {{treatment}}. Follow-up: {{followup}}.'),
    ('Pediatrics', 'Pediatric OPD visit', 'progress',
     'Age-appropriate history from caregiver: {{history}}. Feeding/milestones: {{feeding_milestones}}. Immunizations up to date: {{immunization}}.',
     'Weight-for-age: {{weight}}. Paediatric systemic exam: {{exam}}.',
     'Working diagnosis: {{diagnosis}}.',
     'Treatment (weight-based dosing): {{treatment}}. Caregiver advice: {{advice}}. Review: {{followup}}.'),
    ('Cardiology', 'Cardiology consultation', 'progress',
     'Chest pain / dyspnoea / palpitations: {{symptoms}}. Risk factors: {{risk_factors}}.',
     'BP {{bp}}, HR {{hr}}. Heart sounds {{sounds}}, murmurs {{murmurs}}, peripheral oedema {{oedema}}.',
     'Working diagnosis: {{diagnosis}}. Risk stratification: {{risk}}.',
     'ECG/echo/labs: {{investigations}}. Medication optimisation: {{treatment}}. Review: {{followup}}.'),
    ('Dermatology', 'Dermatology consultation', 'progress',
     'Lesion history (onset, evolution, symptoms): {{history}}. Provoking factors: {{triggers}}.',
     'Morphology: {{morphology}}. Distribution: {{distribution}}. Dermoscopy (if done): {{dermoscopy}}.',
     'Working diagnosis: {{diagnosis}}.',
     'Topical/systemic therapy: {{treatment}}. Skin-care advice: {{advice}}. Review: {{followup}}.'),
    ('Orthopedics', 'Orthopedic OPD visit', 'progress',
     'Mechanism of injury / symptom onset: {{history}}. Functional limitation: {{function}}.',
     'Inspection, palpation, ROM {{rom}}, neurovascular status {{neurovascular}}.',
     'Working diagnosis: {{diagnosis}}. Imaging correlation: {{imaging}}.',
     'Analgesia/immobilisation: {{treatment}}. Physiotherapy referral: {{physio}}. Review: {{followup}}.'),
    ('ENT', 'ENT consultation', 'progress',
     'Otological / rhinological / laryngological symptoms: {{symptoms}}. Duration: {{duration}}.',
     'Otoscopy {{otoscopy}}, anterior rhinoscopy {{rhinoscopy}}, oropharynx {{oropharynx}}, neck {{neck}}.',
     'Working diagnosis: {{diagnosis}}.',
     'Medical/surgical plan: {{treatment}}. Audiology/imaging: {{investigations}}. Review: {{followup}}.'),
    ('Ophthalmology', 'Ophthalmology consultation', 'progress',
     'Visual symptoms: {{symptoms}}. Duration and progression: {{duration}}.',
     'Visual acuity R {{va_right}} L {{va_left}}. Slit-lamp {{slit_lamp}}. Fundus {{fundus}}. IOP {{iop}}.',
     'Working diagnosis: {{diagnosis}}.',
     'Refraction/medication/surgical plan: {{treatment}}. Review: {{followup}}.'),
    ('Obstetrics & Gynaecology', 'OB/GYN consultation', 'progress',
     'LMP {{lmp}}. Gravidity/parity G{{g}}P{{p}}. Presenting complaint: {{complaints}}.',
     'Abdominal {{abdominal}} / pelvic examination {{pelvic}}. Booking vitals: {{vitals}}.',
     'Working diagnosis: {{diagnosis}}. Gestation: {{gestation}}.',
     'Antenatal/gynaecology plan: {{treatment}}. Screening/supplements: {{screening}}. Next visit: {{followup}}.')
ON CONFLICT DO NOTHING;

-- Coded diagnosis catalog seed (global): common OPD subset, ICD-10 + 4 SNOMED anchors.
INSERT INTO clinical.diagnosis_catalog (code, system, display)
VALUES
    ('A09',    'ICD-10', 'Infectious gastroenteritis and colitis, unspecified'),
    ('B34.9',  'ICD-10', 'Viral infection, unspecified'),
    ('D50.9',  'ICD-10', 'Iron deficiency anaemia, unspecified'),
    ('D64.9',  'ICD-10', 'Anaemia, unspecified'),
    ('E03.9',  'ICD-10', 'Hypothyroidism, unspecified'),
    ('E11.9',  'ICD-10', 'Type 2 diabetes mellitus without complications'),
    ('E66.9',  'ICD-10', 'Obesity, unspecified'),
    ('E78.5',  'ICD-10', 'Hyperlipidaemia, unspecified'),
    ('F32.9',  'ICD-10', 'Depressive episode, unspecified'),
    ('F41.9',  'ICD-10', 'Anxiety disorder, unspecified'),
    ('G43.909','ICD-10', 'Migraine, unspecified, without status migrainosus'),
    ('H10.9',  'ICD-10', 'Conjunctivitis, unspecified'),
    ('H66.90', 'ICD-10', 'Otitis media, unspecified'),
    ('I10',    'ICD-10', 'Essential (primary) hypertension'),
    ('J02.9',  'ICD-10', 'Acute pharyngitis, unspecified'),
    ('J00',    'ICD-10', 'Acute nasopharyngitis (common cold)'),
    ('J06.9',  'ICD-10', 'Acute upper respiratory infection, unspecified'),
    ('J20.9',  'ICD-10', 'Acute bronchitis, unspecified'),
    ('J30.4',  'ICD-10', 'Allergic rhinitis, unspecified'),
    ('J44.9',  'ICD-10', 'Chronic obstructive pulmonary disease, unspecified'),
    ('J45.909','ICD-10', 'Unspecified asthma, uncomplicated'),
    ('K02.9',  'ICD-10', 'Dental caries, unspecified'),
    ('K21.9',  'ICD-10', 'Gastro-oesophageal reflux disease without oesophagitis'),
    ('K29.70', 'ICD-10', 'Gastritis, unspecified, without bleeding'),
    ('K59.00', 'ICD-10', 'Constipation, unspecified'),
    ('L23.9',  'ICD-10', 'Allergic contact dermatitis, unspecified cause'),
    ('L50.9',  'ICD-10', 'Urticaria, unspecified'),
    ('M17.9',  'ICD-10', 'Osteoarthritis of knee, unspecified'),
    ('M25.561', 'ICD-10', 'Pain in right knee'),
    ('M25.562', 'ICD-10', 'Pain in left knee'),
    ('M51.9',  'ICD-10', 'Intervertebral disc disorder, unspecified'),
    ('M54.5',  'ICD-10', 'Pain in thoracic spine'),
    ('M54.9',  'ICD-10', 'Dorsalgia, unspecified'),
    ('M79.1',  'ICD-10', 'Myalgia'),
    ('N30.00', 'ICD-10', 'Acute cystitis without haematuria'),
    ('N39.0',  'ICD-10', 'Urinary tract infection, site not specified'),
    ('N76.0',  'ICD-10', 'Acute vaginitis'),
    ('R05',    'ICD-10', 'Cough'),
    ('R10.9',  'ICD-10', 'Unspecified abdominal pain'),
    ('R42',    'ICD-10', 'Dizziness and giddiness'),
    ('R50.9',  'ICD-10', 'Fever, unspecified'),
    ('R51.9',  'ICD-10', 'Headache, unspecified'),
    ('Z00.0',  'ICD-10', 'Encounter for general adult medical examination'),
    ('195967001', 'SNOMED', 'Asthma (disorder)'),
    ('38341003',  'SNOMED', 'Hypertensive disorder, systemic arterial (disorder)'),
    ('73211009',  'SNOMED', 'Diabetes mellitus (disorder)'),
    ('22298006',  'SNOMED', 'Myocardial infarction (disorder)')
ON CONFLICT DO NOTHING;

-- No new permission codes: procedure documentation rides clinical:write:note /
-- clinical:read:note (DOCTOR holds both since V13); referrals activate the existing
-- clinical:manage:referral; templates/catalog reads ride clinical:read:note.
