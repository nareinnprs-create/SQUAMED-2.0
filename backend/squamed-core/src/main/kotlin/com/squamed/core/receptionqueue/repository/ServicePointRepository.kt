package com.squamed.core.receptionqueue.repository

import com.squamed.core.receptionqueue.domain.ServicePoint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ServicePointRepository : JpaRepository<ServicePoint, UUID> {
  // 00.04 tenant sweep: null-safe hospital scope (U10) — admin callers pass null.
  @Query("select sp from ServicePoint sp where (:hid is null or sp.hospitalId = :hid) and sp.queueType = :queueType")
  fun findByQueueType(
    @Param("hid") hospitalId: UUID?,
    @Param("queueType") queueType: String,
  ): List<ServicePoint>

  @Query("select sp from ServicePoint sp where (:hid is null or sp.hospitalId = :hid)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<ServicePoint>

  fun findByStatus(status: String): List<ServicePoint>

  fun findFirstByHospitalIdAndCode(
    hid: UUID,
    code: String,
  ): ServicePoint?
}
