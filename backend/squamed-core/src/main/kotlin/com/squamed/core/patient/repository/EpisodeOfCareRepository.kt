package com.squamed.core.patient.repository

import com.squamed.core.patient.domain.EpisodeOfCare
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EpisodeOfCareRepository : JpaRepository<EpisodeOfCare, UUID> {
  fun findByPatientId(patientId: UUID): List<EpisodeOfCare>
}
