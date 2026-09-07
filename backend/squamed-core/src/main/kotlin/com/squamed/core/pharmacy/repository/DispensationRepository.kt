package com.squamed.core.pharmacy.repository

import com.squamed.core.pharmacy.domain.Dispensation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DispensationRepository : JpaRepository<Dispensation, UUID> {
  fun findByPatientId(patientId: UUID): List<Dispensation>

  fun findByPrescriptionId(prescriptionId: UUID): List<Dispensation>

  fun findByStatus(status: String): List<Dispensation>

  fun findByStatusIn(statuses: Collection<String>): List<Dispensation>

  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): Dispensation?
}
