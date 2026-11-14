package com.squamed.core.notification.repository

import com.squamed.core.notification.domain.NotificationRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface NotificationRequestRepository : JpaRepository<NotificationRequest, UUID> {
  /** Tenant-pinned dispatch ledger (stage-1: explicit JPQL, null-safe hospital filter). */
  @Query(
    """
    select r from NotificationRequest r
    where (:patientId is null or r.patientId = :patientId)
      and ((:hospitalId is null and r.hospitalId is null) or r.hospitalId = :hospitalId)
    order by r.createdAt desc
    """,
  )
  fun findRequests(
    @Param("patientId") patientId: UUID?,
    @Param("hospitalId") hospitalId: UUID?,
  ): List<NotificationRequest>
}
