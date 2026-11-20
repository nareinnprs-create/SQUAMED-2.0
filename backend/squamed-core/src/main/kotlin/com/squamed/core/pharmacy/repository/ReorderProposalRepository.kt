package com.squamed.core.pharmacy.repository

import com.squamed.core.pharmacy.domain.ReorderProposal
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ReorderProposalRepository : JpaRepository<ReorderProposal, UUID> {
  // 00.04 tenant sweep: null-safe hospital scope (admin callers pass null).
  @Query("select p from ReorderProposal p where (:hid is null or p.hospitalId = :hid) and (:status is null or p.status = :status)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
    @Param("status") status: String?,
  ): List<ReorderProposal>

  fun findByStockIdAndStatus(
    stockId: UUID,
    status: String,
  ): List<ReorderProposal>
}
