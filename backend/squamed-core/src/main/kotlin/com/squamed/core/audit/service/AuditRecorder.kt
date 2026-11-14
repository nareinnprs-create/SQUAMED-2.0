package com.squamed.core.audit.service

import com.squamed.core.audit.domain.AuditLog
import com.squamed.core.audit.repository.AuditLogRepository
import com.squamed.core.identity.repository.SessionRepository
import com.squamed.core.identity.repository.UserAccountRepository
import com.squamed.core.identity.security.SquamedPrincipal
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.jdbc.core.JdbcTemplate
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

/**
 * Audit writer (00.10): REQUIRES_NEW transaction that (1) resolves actor identity incl. the
 * login session + registered device, (2) drains the Hibernate before/after capture buffer,
 * (3) appends the row to the per-tenant hash chain (ADR-9) under a row lock on audit.chain_head,
 * and (4) advances the head. The zero UUID keys the system chain for rows without a hospital.
 */
@Component
class AuditRecorder(
  private val auditLogRepository: AuditLogRepository,
  private val userAccountRepository: UserAccountRepository,
  private val sessionRepository: SessionRepository,
  private val jdbcTemplate: JdbcTemplate,
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
        val chain = allocateChain(resolvedHospital)
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
            chainSeq = chain.seq,
            previousHash = chain.previousHash,
            actorSessionId = principal?.sessionId,
            deviceId = sessionDevice,
            beforeState = captured.firstNotNullOfOrNull { it.before },
            afterState = captured.lastOrNull()?.after,
            occurredAt = Instant.now(),
          )
        row.rowHash = computeHash(chain.seq, chain.key, row)
        auditLogRepository.save(row)
        jdbcTemplate.update(
          "UPDATE audit.chain_head SET last_seq = ?, last_hash = ?, updated_at = NOW() WHERE hospital_id = ?",
          chain.seq,
          row.rowHash,
          chain.key,
        )
      }
      // The audit write's own flush (AuditLog/outbox rows) must not leak into the next request
      // on this pooled thread.
      AuditStateCapture.clear()
    } catch (e: Exception) {
      AuditStateCapture.clear()
      logger.warn(e) { "Failed to persist audit log action=$action entityType=$entityType" }
    }
  }

  /** Data class holding the locked chain head state for one append. */
  private data class Chain(
    val key: UUID,
    val seq: Long,
    val previousHash: String?,
  )

  private fun allocateChain(hospitalId: UUID?): Chain {
    val key = hospitalId ?: SYSTEM_CHAIN
    // Upsert then lock: concurrent writers for one tenant serialize on the head row.
    jdbcTemplate.update(
      "INSERT INTO audit.chain_head (hospital_id, last_seq, last_hash) VALUES (?, 0, NULL) ON CONFLICT DO NOTHING",
      key,
    )
    val head =
      jdbcTemplate
        .query(
          "SELECT last_seq, last_hash FROM audit.chain_head WHERE hospital_id = ? FOR UPDATE",
          { rs, _ -> rs.getLong(1) to rs.getString(2) },
          key,
        ).firstOrNull() ?: (0L to null)
    return Chain(key, head.first + 1, head.second)
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

  private fun computeHash(
    seq: Long,
    chainKey: UUID,
    row: AuditLog,
  ): String {
    // Canonical form — keep byte-stable; changing it invalidates every stored hash.
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

  companion object {
    /** System chain key for audit rows without a hospital (zero UUID). */
    val SYSTEM_CHAIN: UUID = UUID(0L, 0L)
  }
}
