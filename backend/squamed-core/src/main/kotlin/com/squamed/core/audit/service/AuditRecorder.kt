package com.squamed.core.audit.service

import com.squamed.core.audit.domain.AuditLog
import com.squamed.core.audit.repository.AuditLogRepository
import com.squamed.core.identity.repository.SessionRepository
import com.squamed.core.identity.repository.UserAccountRepository
import com.squamed.core.identity.security.SquamedPrincipal
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

/** 01.12 fail-closed audit: see AuditWriteException on record(). */

/**
 * Audit writer (00.10): REQUIRES_NEW transaction that (1) resolves actor identity incl. the
 * login session + registered device, (2) drains the Hibernate before/after capture buffer and
 * (3) inserts the audit row durably. Hash-chain linkage is asynchronous (ADR-15): the
 * AuditChainBatcher assigns the per-tenant seq/hash under the chain-head lock in batches, so a
 * request never queues on the serialized append (~26.5 ms/commit when one hospital shares one
 * chain). The zero UUID keys the system chain for rows without a hospital.
 */
@Component
class AuditRecorder(
  private val auditLogRepository: AuditLogRepository,
  private val userAccountRepository: UserAccountRepository,
  private val sessionRepository: SessionRepository,
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
      val resolvedHospital = hospitalId ?: principal?.hospitalId
      val captured = AuditStateCapture.drain(entityType, entityId)
      transactionTemplate.execute {
        val sessionDevice = principal?.sessionId?.let { resolveDevice(it) }
        // Inserted unchained: the batcher assigns chainSeq/previousHash/rowHash (ADR-15).
        val row =
          AuditLog(
            hospitalId = resolvedHospital,
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
            actorSessionId = principal?.sessionId,
            deviceId = sessionDevice,
            beforeState = captured.firstNotNullOfOrNull { it.before },
            afterState = captured.lastOrNull()?.after,
            occurredAt = Instant.now(),
          )
        auditLogRepository.save(row)
      }
    } catch (e: Exception) {
      // 01.12 dual-review hardening: fail closed — an unprovable audit trail fails the request
      // (and rolls back the unaudited business write) instead of being swallowed.
      logger.warn(e) { "Failed to persist audit log action=$action entityType=$entityType" }
      throw AuditWriteException(action, e)
    } finally {
      // The audit write's own flush (AuditLog/outbox rows) must not leak into the next request
      // on this pooled thread.
      AuditStateCapture.clear()
    }
  }

  private fun resolveDevice(sessionId: UUID): UUID? =
    try {
      sessionRepository.findById(sessionId).map { it.deviceId }.orElse(null)
    } catch (e: Exception) {
      logger.debug { "Failed to resolve device for session $sessionId: ${e.message}" }
      null
    }

  private fun resolveEmail(userId: UUID): String? =
    try {
      userAccountRepository.findById(userId).map { it.email }.orElse(null)
    } catch (e: Exception) {
      logger.debug { "Failed to resolve actor email for $userId: ${e.message}" }
      null
    }

  companion object {
    /** System chain key for audit rows without a hospital (zero UUID). */
    val SYSTEM_CHAIN: UUID = UUID(0L, 0L)

    /** Canonical row hash — shared by the async batcher (ADR-15) and the verifier. Keep byte-stable. */
    internal fun computeHash(
      seq: Long,
      chainKey: UUID,
      row: AuditLog,
    ): String {
      val canonical =
        listOf(
          seq,
          chainKey,
          row.actorUserId,
          row.action,
          row.entityType,
          row.entityId,
          row.occurredAt.toEpochMilli(),
          row.method,
          row.path,
          row.requestPayload,
          row.responseStatus,
          row.responsePayload,
          row.beforeState,
          row.afterState,
          row.previousHash,
        ).joinToString("|") { it?.toString() ?: "" }
      val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8))
      return digest.joinToString("") { "%02x".format(it) }
    }
  }
}
