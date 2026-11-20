package com.squamed.core.radiology.repository

import com.squamed.core.radiology.domain.RadOrder
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface RadOrderRepository : JpaRepository<RadOrder, UUID> {
  // 00.04 tenant sweep: null-safe hospital scope (admin callers pass null).
  @Query("select o from RadOrder o where (:hid is null or o.hospitalId = :hid) and (:status is null or o.status = :status)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
    @Param("status") status: String?,
  ): List<RadOrder>

  @Query("select o from RadOrder o where (:hid is null or o.hospitalId = :hid) and o.modality = :modality")
  fun findMwlRows(
    @Param("hid") hospitalId: UUID?,
    @Param("modality") modality: String,
  ): List<RadOrder>

  fun findByPatientId(patientId: UUID): List<RadOrder>
}
