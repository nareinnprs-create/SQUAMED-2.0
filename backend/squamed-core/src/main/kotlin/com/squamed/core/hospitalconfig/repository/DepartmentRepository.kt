package com.squamed.core.hospitalconfig.repository

import com.squamed.core.hospitalconfig.domain.Department
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DepartmentRepository : JpaRepository<Department, UUID> {
  fun findByHospitalId(hospitalId: UUID): List<Department>

  fun findByParentId(parentId: UUID): List<Department>

  fun findByHospitalIdAndType(
    hospitalId: UUID,
    type: String,
  ): List<Department>
}
