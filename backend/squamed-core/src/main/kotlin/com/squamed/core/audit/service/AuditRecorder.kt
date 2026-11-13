package com.squamed.core.audit.service

import com.squamed.core.audit.domain.AuditLog
import com.squamed.core.audit.repository.AuditLogRepository
import com.squamed.core.identity.repository.UserAccountRepository
import com.squamed.core.identity.security.SquamedPrincipal
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Component
class AuditRecorder(
  private val auditLogRepository: AuditLogRepository,
  private val userAccountRepository: UserAccountRepository,
  transactionManager: PlatformTransactionManager,
) {
  private val transactionTemplate =
    TransactionTemplate(transactionManager).apply {
      setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
    }

  fun record(
    action: String,
    entityType: String,
    entityId: String? = null,
    hospitalId: UUID? = null,
    actorUserId: UUID? = null,
    actorEmail: String? = null,
    ipAddress: String? = null,
    userAgent: String? = null,
    method: String? = null,
    path: String? = null,
    requestPayload: String? = null,
    responseStatus: Int? = null,
    responsePayload: String? = null,
  ) {
    try {
      val principal = SecurityContextHolder.getContext().authentication?.principal as? SquamedPrincipal
      val resolvedActorId = actorUserId ?: principal?.userId
      val resolvedActorEmail = actorEmail ?: resolvedActorId?.let { resolveEmail(it) }
      transactionTemplate.execute {
        auditLogRepository.save(
          AuditLog(
            hospitalId = hospitalId ?: principal?.hospitalId,
            actorUserId = resolvedActorId,
            actorEmail = resolvedActorEmail,
            action = action,
            entityType = entityType,
            entityId = entityId,
            ipAddress = ipAddress?.take(64),
            userAgent = userAgent?.take(400),
            method = method?.take(10),
            path = path?.take(255),
            requestPayload = requestPayload,
            responseStatus = responseStatus,
            responsePayload = responsePayload,
            occurredAt = Instant.now(),
          ),
        )
      }
    } catch (e: Exception) {
      logger.warn(e) { "Failed to persist audit log action=$action entityType=$entityType" }
    }
  }

  private fun resolveEmail(userId: UUID): String? =
    try {
      userAccountRepository.findById(userId).map { it.email }.orElse(null)
    } catch (e: Exception) {
      logger.debug { "Failed to resolve actor email for $userId: ${e.message}" }
      null
    }
}
