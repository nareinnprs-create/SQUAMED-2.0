package com.squamed.core.receptionqueue.repository

import com.squamed.core.receptionqueue.domain.ConsultReceipt
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ConsultReceiptRepository : JpaRepository<ConsultReceipt, UUID> {
  // 00.04 tenant sweep: null-safe hospital scope (U11).
  @Query("select r from ConsultReceipt r where (:hid is null or r.hospitalId = :hid) and r.visitId = :visitId")
  fun findByVisitId(
    @Param("hid") hospitalId: UUID?,
    @Param("visitId") visitId: UUID,
  ): List<ConsultReceipt>

  // Hospital-scoped sequence (01.02 lesson: cross-tenant maxes corrupt fresh hospitals).
  @Query("select r from ConsultReceipt r where (:hid is null or r.hospitalId = :hid) order by r.receiptNumber desc limit 1")
  fun findTopForHospital(
    @Param("hid") hospitalId: UUID?,
  ): ConsultReceipt?
}
