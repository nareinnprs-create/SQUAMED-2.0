package com.squamed.core.audit.service

import com.squamed.core.audit.domain.AuditLog
import com.squamed.core.audit.repository.AuditLogRepository
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * ADR-9 chain verifier (00.10): recomputes every row hash in chain_seq order per tenant and
 * checks linkage (previous_hash == prior row_hash). Breaches publish `audit.chain_broken`
 * through the event fabric so the notification bridge pages admins (chunk-00 item 8 lists
 * "audit alerts" in the v1 event set). Legacy rows (pre-V49, no hash) are reported once and
 * are not tamper breaches.
 */
@Service
class AuditChainService(
  private val auditLogRepository: AuditLogRepository,
  private val jdbcTemplate: JdbcTemplate,
  private val eventPublisher: DomainEventPublisher,
) {
  private val logger = KotlinLogging.logger {}

  data class Breach(
    val hospitalId: UUID,
    val seq: Long?,
    val reason: String,
  )

  data class VerifyResult(
    val checked: Int,
    val breachedTenants: Int,
    val legacyRows: Int,
    val breaches: List<Breach>,
    val verifiedAt: Instant = Instant.now(),
  ) {
    val ok: Boolean get() = breaches.isEmpty()
  }

  @Transactional(readOnly = true)
  fun verify(): VerifyResult {
    val chainKeys = jdbcTemplate.queryForList("SELECT hospital_id FROM audit.chain_head", UUID::class.java)
    var checked = 0
    var legacy = 0L
    val breaches = mutableListOf<Breach>()
    for (key in chainKeys) {
      val hospitalId = if (key == AuditRecorder.SYSTEM_CHAIN) null else key
      val rows = auditLogRepository.findChainedByHospital(hospitalId)
      var previousHash: String? = null
      for (row in rows) {
        checked++
        if (previousHash != null && row.previousHash != previousHash) {
          breaches += Breach(key, row.chainSeq, "LINK_BROKEN at seq ${row.chainSeq}: expected ${previousHash?.take(8)}, got ${row.previousHash?.take(8)}")
          break
        }
        if (row.rowHash == null) {
          breaches += Breach(key, row.chainSeq, "MISSING_HASH at seq ${row.chainSeq}")
          break
        }
        if (row.rowHash != computeHash(row)) {
          breaches += Breach(key, row.chainSeq, "HASH_MISMATCH at seq ${row.chainSeq}: stored ${row.rowHash?.take(8)}")
          break
        }
        previousHash = row.rowHash
      }
      val unchained = auditLogRepository.countByHospitalIdAndChainSeqIsNull(hospitalId)
      legacy += unchained
    }
    return VerifyResult(checked = checked, breachedTenants = breaches.map { it.hospitalId }.distinct().size, legacyRows = legacy.toInt(), breaches = breaches)
  }

  /** Nightly verification (ADR-9: breach pages admins via the event fabric + in-app channel). */
  @Scheduled(cron = "\${squamed.audit.verify-cron:0 0 3 * * *}")
  fun verifyNightly() {
    verifyAndAlert()
  }

  /** On-demand verify used by the endpoint + nightly job: publishes alerts when breached. */
  fun verifyAndAlert(): VerifyResult {
    val result = verify()
    if (!result.ok) {
      logger.error { "Audit chain verification FAILED: ${result.breaches.size} breach(es), ${result.checked} rows checked" }
      publishAlerts(result)
    } else {
      logger.info { "Audit chain verification OK: ${result.checked} rows checked, ${result.legacyRows} legacy rows" }
    }
    return result
  }

  /** Publishes one audit.chain_broken event per breached tenant (fresh tx via the outbox). */
  fun publishAlerts(result: VerifyResult) {
    val byTenant = result.breaches.groupBy { it.hospitalId }
    byTenant.forEach { (chainKey, breaches) ->
      try {
        eventPublisher.publish(
          DomainEvent(
            eventId = UUID.randomUUID(),
            eventType = "audit.chain_broken",
            occurredAt = Instant.now(),
            source = "audit",
            entityType = "AuditLog",
            entityId = null,
            hospitalId = if (chainKey == AuditRecorder.SYSTEM_CHAIN) null else chainKey,
            payload =
              mapOf(
                "reasons" to breaches.map { it.reason },
                "checked" to result.checked,
              ),
            recipientIds = emptyList(),
            actorType = "system",
          ),
        )
      } catch (e: Exception) {
        logger.error(e) { "Failed to publish audit.chain_broken for tenant $chainKey" }
      }
    }
  }

  /** Recomputes the stored-hash comparison using the recorder's canonical form. */
  private fun computeHash(row: AuditLog): String {
    val canonical =
      listOf(
        row.chainSeq,
        row.hospitalId ?: AuditRecorder.SYSTEM_CHAIN,
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
    val digest =
      java.security.MessageDigest
        .getInstance("SHA-256")
        .digest(canonical.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }
}
