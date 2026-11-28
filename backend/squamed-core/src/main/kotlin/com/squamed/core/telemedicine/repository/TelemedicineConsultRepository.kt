package com.squamed.core.telemedicine.repository

import com.squamed.core.telemedicine.domain.TelemedicineConsult
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TelemedicineConsultRepository : JpaRepository<TelemedicineConsult, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): TelemedicineConsult?

  fun findByHospitalIdOrderByScheduledAtDesc(hospitalId: UUID): List<TelemedicineConsult>
}
