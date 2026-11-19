package com.squamed.core.pharmacy.repository

import com.squamed.core.pharmacy.domain.StockLedger
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface StockLedgerRepository : JpaRepository<StockLedger, UUID> {
  fun findByStockIdOrderByOccurredAtDesc(stockId: UUID): List<StockLedger>

  // 00.04 tenant sweep: null-safe hospital scope (U9) — admin callers pass null.
  @Query("select l from StockLedger l where (:hid is null or l.hospitalId = :hid) order by l.occurredAt desc")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<StockLedger>
}
