package com.squamed.core.patient.repository

import com.squamed.core.patient.domain.PatientRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PatientRecordRepository : JpaRepository<PatientRecord, UUID> {
  fun findByMrnIgnoreCase(mrn: String): PatientRecord?

  fun findByHospitalIdAndMrnIgnoreCase(
    hospitalId: UUID,
    mrn: String,
  ): PatientRecord?

  fun findByPhone(phone: String): List<PatientRecord>

  fun findByPhoneContaining(phone: String): List<PatientRecord>

  fun findByQid(qid: String): List<PatientRecord>

  fun findByEmailIgnoreCase(email: String): List<PatientRecord>

  fun findByLastNameContainingIgnoreCaseAndFirstNameContainingIgnoreCase(
    lastName: String,
    firstName: String,
  ): List<PatientRecord>

  fun findTopByOrderByMrnDesc(): PatientRecord?

  /** Race-free MRN source (V53): nextval is atomic; gaps on rollback are acceptable for MRNs. */
  @Query(value = "SELECT nextval('patient.mrn_seq')", nativeQuery = true)
  fun nextMrnSequence(): Long

  fun findByFirstNameContainingIgnoreCase(firstName: String): List<PatientRecord>

  fun findByLastNameContainingIgnoreCase(lastName: String): List<PatientRecord>

  fun findByPassportNumber(passportNumber: String): List<PatientRecord>

  fun findByIdNumber(idNumber: String): List<PatientRecord>

  fun findByHospitalId(hospitalId: UUID): List<PatientRecord>

  /**
   * All detail sub-resources in one round trip as a jsonb document (NFR §6: getDetail paid 8 sequential
   * queries per read; under CPU saturation each ~26 ms RT compounded into ~800 ms p50). JSON keys are
   * camelCase and match PatientDetailSubresources; PostgreSQL encodes uuid/date/timestamptz as JSON strings,
   * which Jackson's JavaTimeModule parses back.
   */
  @Query(
    """
    SELECT jsonb_build_object(
      'identifiers', COALESCE((SELECT jsonb_agg(jsonb_build_object(
          'id', i.id, 'patientId', i.patient_id, 'idType', i.id_type, 'idValue', i.id_value,
          'issuingAuthority', i.issuing_authority, 'primaryFlag', i.primary_flag))
        FROM patient.patient_identifier i WHERE i.patient_id = :id), '[]'::jsonb),
      'contactPoints', COALESCE((SELECT jsonb_agg(jsonb_build_object(
          'id', c.id, 'patientId', c.patient_id, 'type', c.type, 'value', c.value,
          'purpose', c.purpose, 'isPrimary', c.is_primary))
        FROM patient.contact_point c WHERE c.patient_id = :id), '[]'::jsonb),
      'addresses', COALESCE((SELECT jsonb_agg(jsonb_build_object(
          'id', a.id, 'patientId', a.patient_id, 'type', a.type, 'line1', a.line1, 'line2', a.line2,
          'city', a.city, 'stateProvince', a.state_province, 'country', a.country,
          'postalCode', a.postal_code, 'isPrimary', a.is_primary))
        FROM patient.address a WHERE a.patient_id = :id), '[]'::jsonb),
      'allergies', COALESCE((SELECT jsonb_agg(jsonb_build_object(
          'id', al.id, 'patientId', al.patient_id, 'allergen', al.allergen, 'reaction', al.reaction,
          'severity', al.severity, 'recordedDate', al.recorded_date, 'isActive', al.is_active, 'notes', al.notes))
        FROM patient.allergy al WHERE al.patient_id = :id), '[]'::jsonb),
      'conditions', COALESCE((SELECT jsonb_agg(jsonb_build_object(
          'id', m.id, 'patientId', m.patient_id, 'conditionName', m.condition_name,
          'diagnosisCode', m.diagnosis_code, 'diagnosisSystem', m.diagnosis_system,
          'onsetDate', m.onset_date, 'resolvedDate', m.resolved_date, 'notes', m.notes))
        FROM patient.medical_history m WHERE m.patient_id = :id), '[]'::jsonb),
      'notes', COALESCE((SELECT jsonb_agg(jsonb_build_object(
          'id', n.id, 'patientId', n.patient_id, 'authorId', n.author_id, 'title', n.title,
          'body', n.body, 'isFlagged', n.is_flagged, 'createdAt', n.created_at))
        FROM patient.patient_note n WHERE n.patient_id = :id), '[]'::jsonb),
      'relationships', COALESCE((SELECT jsonb_agg(jsonb_build_object(
          'id', r.id, 'patientId', r.patient_id, 'relatedPatientId', r.related_patient_id,
          'relationshipType', r.relationship_type, 'isPrimaryGuardian', r.is_primary_guardian,
          'notes', r.notes, 'reverse', r.reverse))
        FROM (
          SELECT pr.*, FALSE AS reverse FROM patient.patient_relationship pr WHERE pr.patient_id = :id
          UNION ALL
          SELECT pr.*, TRUE AS reverse FROM patient.patient_relationship pr WHERE pr.related_patient_id = :id
        ) r), '[]'::jsonb)
    )::text
    """,
    nativeQuery = true,
  )
  fun findDetailSubresources(
    @Param("id") id: UUID,
  ): String?
}
