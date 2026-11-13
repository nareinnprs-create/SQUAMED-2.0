package com.squamed.core.hospitalconfig.repository

import com.squamed.core.hospitalconfig.domain.Campus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CampusRepository : JpaRepository<Campus, UUID> {
  fun findByHospitalId(hospitalId: UUID): List<Campus>
}
