package com.squamed.core.audit.repository

import com.squamed.core.audit.domain.AuditLog
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AuditLogRepository : JpaRepository<AuditLog, UUID> {
  fun findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
    entityType: String,
    entityId: String,
  ): List<AuditLog>

  fun findByActorUserIdOrderByOccurredAtDesc(actorUserId: UUID): List<AuditLog>

  fun findAllByOrderByOccurredAtDesc(): List<AuditLog>
}
