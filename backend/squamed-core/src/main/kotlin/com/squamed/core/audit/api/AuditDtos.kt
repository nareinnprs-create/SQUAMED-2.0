package com.squamed.core.audit.api

import com.squamed.core.audit.domain.AuditLog
import java.time.Instant
import java.util.UUID

data class AuditLogView(
  val id: UUID?,
  val actorUserId: UUID?,
  val actorEmail: String?,
  val action: String,
  val entityType: String,
  val entityId: String?,
  val method: String?,
  val path: String?,
  val responseStatus: Int?,
  val occurredAt: Instant?,
  val ipAddress: String?,
) {
  companion object {
    fun from(entity: AuditLog) =
      AuditLogView(
        id = entity.id,
        actorUserId = entity.actorUserId,
        actorEmail = entity.actorEmail,
        action = entity.action,
        entityType = entity.entityType,
        entityId = entity.entityId,
        method = entity.method,
        path = entity.path,
        responseStatus = entity.responseStatus,
        occurredAt = entity.occurredAt,
        ipAddress = entity.ipAddress,
      )
  }
}
