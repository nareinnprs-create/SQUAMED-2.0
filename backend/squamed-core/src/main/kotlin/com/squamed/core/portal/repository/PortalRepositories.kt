package com.squamed.core.portal.repository

import com.squamed.core.portal.domain.PortalAccount
import com.squamed.core.portal.domain.PushSubscription
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PortalAccountRepository : JpaRepository<PortalAccount, UUID> {
  fun findByUserId(userId: UUID): PortalAccount?

  fun findByPatientId(patientId: UUID): List<PortalAccount>
}

interface PushSubscriptionRepository : JpaRepository<PushSubscription, UUID> {
  fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<PushSubscription>

  fun findByEndpoint(endpoint: String): PushSubscription?
}
