package com.squamed.core.audit.api

import com.squamed.core.audit.repository.AuditLogRepository
import com.squamed.core.audit.service.AuditChainService
import com.squamed.core.audit.service.AuditExportService
import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.data.domain.PageRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.Base64
import java.util.UUID

@RestController
@RequestMapping("/api/v1/audit")
class AuditController(
  private val auditLogRepository: AuditLogRepository,
  private val auditChainService: AuditChainService,
  private val auditExportService: AuditExportService,
) {
  @GetMapping("/logs")
  @RequiresPermission("audit:read:audit")
  @Transactional(readOnly = true)
  fun listLogs(
    @RequestParam(name = "entityType", required = false) entityType: String?,
    @RequestParam(name = "entityId", required = false) entityId: String?,
    @RequestParam(name = "actor", required = false) actor: UUID?,
    @RequestParam(name = "limit", defaultValue = "50") limit: Int,
    @RequestParam(name = "cursor", required = false) cursor: String?,
  ): ApiResponse<List<AuditLogView>> {
    val effectiveLimit = if (limit in 1..500) limit else 50
    // limit+1 probe decides hasNext without a count query (append-only streams have no total).
    val pageRequest = PageRequest.of(0, effectiveLimit + 1)
    val rows =
      decodeCursor(cursor)?.let { (cursorAt, cursorId) ->
        auditLogRepository.listLogsAfter(entityType, entityId, actor, cursorAt, cursorId, pageRequest)
      } ?: auditLogRepository.listLogsFirst(entityType, entityId, actor, pageRequest)
    val hasNext = rows.size > effectiveLimit
    val page = if (hasNext) rows.take(effectiveLimit) else rows
    val nextCursor =
      if (hasNext) {
        val last = page.last()
        encodeCursor(last.occurredAt, last.id ?: throw IllegalStateException("AUDIT_ROW_ID_MISSING"))
      } else {
        null
      }
    val meta =
      buildMap {
        put("limit", effectiveLimit)
        put("hasNext", hasNext)
        if (nextCursor != null) put("nextCursor", nextCursor)
      }
    return ApiResponse.ok(page.map { AuditLogView.from(it) }, meta)
  }

  /**
   * ADR-9 on-demand chain verification (auditor "chain-integrity check" in the viewer).
   * Scoped to the caller's tenant by default: the platform-wide sweep is unbounded and returns
   * other tenants' chain keys, so it is only reachable by an explicit `all=true` from a platform
   * admin (no hospital on the principal).
   */
  @PostMapping("/verify")
  @RequiresPermission("audit:read:audit")
  fun verifyChain(
    @RequestParam(name = "all", required = false, defaultValue = "false") all: Boolean,
    @RequestParam(name = "hospitalId", required = false) hospitalId: UUID?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AuditVerifyView> {
    val callerHospital = principal.hospitalId
    if (all) {
      if (callerHospital != null) throw AccessDeniedException("AUDIT_VERIFY_ALL_PLATFORM_ONLY")
      return ApiResponse.ok(AuditVerifyView.from(auditChainService.verifyAllAndAlert()))
    }
    if (hospitalId != null && callerHospital != null && hospitalId != callerHospital) {
      throw AccessDeniedException("AUDIT_VERIFY_TENANT_MISMATCH")
    }
    return ApiResponse.ok(AuditVerifyView.from(auditChainService.verifyAndAlert(hospitalId ?: callerHospital)))
  }

  /** Runs the WORM export immediately (nightly job runs on its own schedule). */
  @PostMapping("/export")
  @RequiresPermission("audit:export:audit")
  fun exportNow(): ApiResponse<AuditExportView> {
    val (exported, objectKey) = auditExportService.exportNow()
    return ApiResponse.ok(AuditExportView(exported = exported, objectKey = objectKey))
  }

  /** Opaque cursor = base64url of "<occurredAt>|<id>" (style guide §Pagination, cursor mode). */
  private fun encodeCursor(
    occurredAt: Instant,
    id: UUID,
  ): String =
    Base64
      .getUrlEncoder()
      .withoutPadding()
      .encodeToString("$occurredAt|$id".toByteArray())

  private fun decodeCursor(cursor: String?): Pair<Instant, UUID>? {
    if (cursor == null) return null
    return runCatching {
      val raw = String(Base64.getUrlDecoder().decode(cursor))
      val sep = raw.indexOf('|')
      require(sep > 0) { "INVALID_CURSOR" }
      Instant.parse(raw.substring(0, sep)) to UUID.fromString(raw.substring(sep + 1))
    }.getOrElse { throw IllegalArgumentException("INVALID_CURSOR") }
  }
}
