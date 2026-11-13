package com.squamed.core.hospitalconfig.repository

import com.squamed.core.hospitalconfig.domain.Building
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BuildingRepository : JpaRepository<Building, UUID> {
  fun findByHospitalId(hospitalId: UUID): List<Building>

  fun findByCampusId(campusId: UUID): List<Building>
}
