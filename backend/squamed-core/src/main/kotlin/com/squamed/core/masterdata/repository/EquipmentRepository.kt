package com.squamed.core.masterdata.repository

import com.squamed.core.masterdata.domain.Equipment
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface EquipmentRepository : JpaRepository<Equipment, UUID> {
  fun findByHospitalIdAndCode(
    hospitalId: UUID,
    code: String,
  ): Equipment?

  fun findByHospitalId(
    hospitalId: UUID,
    pageable: Pageable,
  ): Page<Equipment>

  fun findByHospitalIdAndIsActive(
    hospitalId: UUID,
    isActive: Boolean,
    pageable: Pageable,
  ): Page<Equipment>

  @Query("select e from Equipment e where e.hospitalId = :hospitalId and (lower(e.name) like lower(:name) or lower(e.code) like lower(:code))")
  fun search(
    hospitalId: UUID,
    name: String,
    code: String,
    pageable: Pageable,
  ): Page<Equipment>

  @Query(
    "select e from Equipment e where e.hospitalId = :hospitalId and (lower(e.name) like lower(:name) or lower(e.code) like lower(:code)) and e.isActive = :isActive",
  )
  fun search(
    hospitalId: UUID,
    name: String,
    code: String,
    isActive: Boolean,
    pageable: Pageable,
  ): Page<Equipment>
}
