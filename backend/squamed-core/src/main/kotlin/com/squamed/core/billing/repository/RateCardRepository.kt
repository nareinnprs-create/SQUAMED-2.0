package com.squamed.core.billing.repository

import com.squamed.core.billing.domain.RateCard
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface RateCardRepository : JpaRepository<RateCard, UUID> {
  @Query("select r from RateCard r where r.hospitalId = :hid and r.payerClass = :payerClass and r.code = :code and r.isActive = true")
  fun findActive(
    @Param("hid") hospitalId: UUID,
    @Param("payerClass") payerClass: String,
    @Param("code") code: String,
  ): List<RateCard>

  @Query("select r from RateCard r where (:hid is null or r.hospitalId = :hid) order by r.payerClass, r.code")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<RateCard>
}
