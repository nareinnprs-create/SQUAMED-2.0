package com.squamed.core.encounter.repository

import com.squamed.core.encounter.domain.VitalSign
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface VitalSignRepository : JpaRepository<VitalSign, UUID> {
  fun findByEncounterId(encounterId: UUID): List<VitalSign>

  fun findTopByPatientIdOrderByRecordedAtDesc(patientId: UUID): VitalSign?

  fun findTop10ByPatientIdOrderByRecordedAtDesc(patientId: UUID): List<VitalSign>
}
