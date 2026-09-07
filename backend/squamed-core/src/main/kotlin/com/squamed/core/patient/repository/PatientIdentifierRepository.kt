package com.squamed.core.patient.repository

import com.squamed.core.patient.domain.PatientIdentifier
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PatientIdentifierRepository : JpaRepository<PatientIdentifier, UUID> {
  fun findByPatientId(patientId: UUID): List<PatientIdentifier>

  fun findByIdValue(idValue: String): List<PatientIdentifier>

  fun existsByIdTypeAndIdValue(
    idType: String,
    idValue: String,
  ): Boolean
}
