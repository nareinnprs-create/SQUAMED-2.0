package com.squamed.core.masterdata.repository

import com.squamed.core.masterdata.domain.Employee
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface EmployeeRepository : JpaRepository<Employee, UUID> {
  fun findByHospitalIdAndCode(
    hospitalId: UUID,
    code: String,
  ): Employee?

  fun findByHospitalId(
    hospitalId: UUID,
    pageable: Pageable,
  ): Page<Employee>

  fun findByHospitalIdAndIsActive(
    hospitalId: UUID,
    isActive: Boolean,
    pageable: Pageable,
  ): Page<Employee>

  @Query(
    "select e from Employee e where e.hospitalId = :hospitalId and (lower(e.firstName) like lower(:name) or lower(e.lastName) like lower(:name) or lower(e.code) like lower(:code))",
  )
  fun search(
    hospitalId: UUID,
    name: String,
    code: String,
    pageable: Pageable,
  ): Page<Employee>

  @Query(
    "select e from Employee e where e.hospitalId = :hospitalId and (lower(e.firstName) like lower(:name) or lower(e.lastName) like lower(:name) or lower(e.code) like lower(:code)) and e.isActive = :isActive",
  )
  fun search(
    hospitalId: UUID,
    name: String,
    code: String,
    isActive: Boolean,
    pageable: Pageable,
  ): Page<Employee>
}
