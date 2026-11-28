package com.squamed.core.entitlement.repository

import com.squamed.core.entitlement.domain.EntitlementPlan
import com.squamed.core.entitlement.domain.EntitlementSubscription
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EntitlementPlanRepository : JpaRepository<EntitlementPlan, UUID> {
  fun findByCodeIgnoreCase(code: String): EntitlementPlan?

  fun findByActiveTrueOrderByCodeAsc(): List<EntitlementPlan>
}

interface EntitlementSubscriptionRepository : JpaRepository<EntitlementSubscription, UUID> {
  fun findFirstByHospitalIdAndStatusOrderByCreatedAtDesc(
    hospitalId: UUID,
    status: String,
  ): EntitlementSubscription?

  fun findByHospitalIdAndStatus(
    hospitalId: UUID,
    status: String,
  ): List<EntitlementSubscription>
}
