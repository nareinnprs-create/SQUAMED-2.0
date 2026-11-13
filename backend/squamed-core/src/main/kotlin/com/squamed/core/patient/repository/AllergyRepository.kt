package com.squamed.core.patient.repository

import com.squamed.core.patient.domain.Allergy
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AllergyRepository : JpaRepository<Allergy, UUID> {
  fun findByPatientId(patientId: UUID): List<Allergy>
}
