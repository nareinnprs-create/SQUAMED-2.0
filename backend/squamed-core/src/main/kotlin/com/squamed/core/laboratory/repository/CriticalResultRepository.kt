package com.squamed.core.laboratory.repository

import com.squamed.core.laboratory.domain.CriticalResult
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface CriticalResultRepository : JpaRepository<CriticalResult, UUID> {
  @Query("select c from CriticalResult c where (:hid is null or c.hospitalId = :hid) order by c.createdAt desc")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<CriticalResult>

  fun findAllByLabOrderIdOrderByCreatedAtDesc(labOrderId: UUID): List<CriticalResult>

  fun existsByLabOrderIdAndStatus(
    labOrderId: UUID,
    status: String,
  ): Boolean
}
