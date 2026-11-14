package com.squamed.core.audit.api

import com.squamed.core.audit.domain.AuditLog
import com.squamed.core.audit.service.AuditChainService
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
  val chainSeq: Long? = null,
  val previousHash: String? = null,
  val rowHash: String? = null,
  val actorSessionId: UUID? = null,
  val deviceId: UUID? = null,
  val beforeState: String? = null,
  val afterState: String? = null,
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
        chainSeq = entity.chainSeq,
        previousHash = entity.previousHash,
        rowHash = entity.rowHash,
        actorSessionId = entity.actorSessionId,
        deviceId = entity.deviceId,
        beforeState = entity.beforeState,
        afterState = entity.afterState,
      )
  }
}

data class AuditVerifyView(
  val ok: Boolean,
  val checked: Int,
  val breachedTenants: Int,
  val legacyRows: Int,
  val breaches: List<BreachView>,
  val verifiedAt: Instant,
) {
  data class BreachView(
    val hospitalId: UUID,
    val seq: Long?,
    val reason: String,
  )

  companion object {
    fun from(result: AuditChainService.VerifyResult) =
      AuditVerifyView(
        ok = result.ok,
        checked = result.checked,
        breachedTenants = result.breachedTenants,
        legacyRows = result.legacyRows,
        breaches = result.breaches.map { BreachView(it.hospitalId, it.seq, it.reason) },
        verifiedAt = result.verifiedAt,
      )
  }
}

data class AuditExportView(
  val exported: Int,
  val objectKey: String?,
)
