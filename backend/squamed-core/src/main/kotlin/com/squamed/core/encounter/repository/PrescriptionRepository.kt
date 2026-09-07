package com.squamed.core.encounter.repository

import com.squamed.core.encounter.domain.Prescription
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PrescriptionRepository : JpaRepository<Prescription, UUID> {
  fun findByEncounterId(encounterId: UUID): List<Prescription>

  fun findByPatientId(patientId: UUID): List<Prescription>
}
