package com.squamed.core.encounter.repository

import com.squamed.core.encounter.domain.Encounter
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EncounterRepository : JpaRepository<Encounter, UUID> {
  fun findByPatientId(patientId: UUID): List<Encounter>

  fun findByStatus(status: String): List<Encounter>

  fun findByVisitId(visitId: UUID): List<Encounter>

  fun findByHospitalId(hospitalId: UUID): List<Encounter>

  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): Encounter?
}
