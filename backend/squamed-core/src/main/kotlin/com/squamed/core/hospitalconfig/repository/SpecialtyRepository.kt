package com.squamed.core.hospitalconfig.repository

import com.squamed.core.hospitalconfig.domain.Specialty
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpecialtyRepository : JpaRepository<Specialty, UUID> {
  fun findByHospitalId(hospitalId: UUID): List<Specialty>

  fun findByDepartmentId(departmentId: UUID): List<Specialty>
}
