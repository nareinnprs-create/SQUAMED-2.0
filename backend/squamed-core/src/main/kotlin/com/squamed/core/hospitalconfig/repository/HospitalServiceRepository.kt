package com.squamed.core.hospitalconfig.repository

import com.squamed.core.hospitalconfig.domain.HospitalService
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface HospitalServiceRepository : JpaRepository<HospitalService, UUID> {
  fun findByHospitalId(hospitalId: UUID): List<HospitalService>

  fun findByIsActiveTrueAndHospitalId(hospitalId: UUID): List<HospitalService>
}
