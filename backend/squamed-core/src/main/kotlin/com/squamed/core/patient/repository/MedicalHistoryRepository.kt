package com.squamed.core.patient.repository

import com.squamed.core.patient.domain.MedicalHistory
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MedicalHistoryRepository : JpaRepository<MedicalHistory, UUID> {
  fun findByPatientId(patientId: UUID): List<MedicalHistory>
}
