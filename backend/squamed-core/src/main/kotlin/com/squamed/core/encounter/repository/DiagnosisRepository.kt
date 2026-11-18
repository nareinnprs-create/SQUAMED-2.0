package com.squamed.core.encounter.repository

import com.squamed.core.encounter.domain.Diagnosis
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DiagnosisRepository : JpaRepository<Diagnosis, UUID> {
  fun findByEncounterId(encounterId: UUID): List<Diagnosis>

  fun findByEncounterIdAndIsActiveTrue(encounterId: UUID): List<Diagnosis>

  fun findByPatientId(patientId: UUID): List<Diagnosis>
}
