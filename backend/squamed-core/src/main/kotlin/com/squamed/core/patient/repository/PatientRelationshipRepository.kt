package com.squamed.core.patient.repository

import com.squamed.core.patient.domain.PatientRelationship
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PatientRelationshipRepository : JpaRepository<PatientRelationship, UUID> {
  fun findByPatientId(patientId: UUID): List<PatientRelationship>

  fun findByRelatedPatientId(relatedPatientId: UUID): List<PatientRelationship>

  fun existsByHospitalIdAndPatientIdAndRelatedPatientIdAndRelationshipType(
    hospitalId: UUID,
    patientId: UUID,
    relatedPatientId: UUID,
    relationshipType: String,
  ): Boolean
}
