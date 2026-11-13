package com.squamed.core.patient.repository

import com.squamed.core.patient.domain.EmpiScore
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EmpiScoreRepository : JpaRepository<EmpiScore, UUID> {
  fun findByPatientId(patientId: UUID): List<EmpiScore>
}
