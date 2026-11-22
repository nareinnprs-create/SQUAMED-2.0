package com.squamed.core.billing.repository

import com.squamed.core.billing.domain.ReconStatement
import com.squamed.core.billing.domain.Refund
import com.squamed.core.billing.domain.ShiftCashup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ShiftCashupRepository : JpaRepository<ShiftCashup, UUID> {
  @Query("select c from ShiftCashup c where (:hid is null or c.hospitalId = :hid) and c.cashierId = :cashierId and c.status = 'OPEN'")
  fun findOpenForCashier(
    @Param("hid") hospitalId: UUID?,
    @Param("cashierId") cashierId: UUID,
  ): List<ShiftCashup>

  @Query("select c from ShiftCashup c where (:hid is null or c.hospitalId = :hid) order by c.openedAt desc")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<ShiftCashup>
}

interface ReconStatementRepository : JpaRepository<ReconStatement, UUID> {
  @Query("select r from ReconStatement r where (:hid is null or r.hospitalId = :hid) order by r.periodEnd desc")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<ReconStatement>
}

interface RefundRepository : JpaRepository<Refund, UUID> {
  fun findByInvoiceId(invoiceId: UUID): List<Refund>

  @Query("select r from Refund r where (:hid is null or r.hospitalId = :hid) order by r.issuedAt desc")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<Refund>
}
