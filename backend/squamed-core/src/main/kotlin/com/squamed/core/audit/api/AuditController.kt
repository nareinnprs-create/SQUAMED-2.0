package com.squamed.core.audit.api

import com.squamed.core.audit.repository.AuditLogRepository
import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.RequiresPermission
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/audit")
class AuditController(
  private val auditLogRepository: AuditLogRepository,
) {
  @GetMapping("/logs")
  @RequiresPermission("audit:read:audit")
  @Transactional(readOnly = true)
  fun listLogs(
    @RequestParam(name = "entityType", required = false) entityType: String?,
    @RequestParam(name = "entityId", required = false) entityId: String?,
    @RequestParam(name = "actor", required = false) actor: UUID?,
    @RequestParam(name = "limit", defaultValue = "50") limit: Int,
  ): ApiResponse<List<AuditLogView>> {
    val effectiveLimit = if (limit in 1..500) limit else 50
    val logs =
      when {
        entityType != null && entityId != null -> {
          auditLogRepository.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(entityType, entityId)
        }

        actor != null -> {
          auditLogRepository.findByActorUserIdOrderByOccurredAtDesc(actor)
        }

        else -> {
          auditLogRepository.findAllByOrderByOccurredAtDesc()
        }
      }
    return ApiResponse.ok(logs.take(effectiveLimit).map { AuditLogView.from(it) })
  }
}
