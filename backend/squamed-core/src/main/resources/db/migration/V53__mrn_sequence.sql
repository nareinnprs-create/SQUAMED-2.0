-- MRN collision-safe generation (00.16 remediation): the old max+1 check-then-insert raced
-- between concurrent creates (both read the same MRN, one insert died on the unique
-- constraint -> 409). A sequence is race-free by construction; rollback gaps are
-- acceptable for MRNs. Bootstrap past every existing MQ- MRN tail number.
CREATE SEQUENCE IF NOT EXISTS patient.mrn_seq;

SELECT setval(
  'patient.mrn_seq',
  GREATEST(
    COALESCE((SELECT MAX(substring(mrn FROM '[0-9]+$')::int) FROM patient.patient_record WHERE mrn LIKE 'MQ-%'), 0),
    1
  )
);
