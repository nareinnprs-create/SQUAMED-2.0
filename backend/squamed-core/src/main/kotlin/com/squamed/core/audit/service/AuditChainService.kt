package com.squamed.core.audit.service

import com.squamed.core.audit.domain.AuditLog
import com.squamed.core.audit.repository.AuditLogRepository
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
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
 * "audit alerts" in the v1 event set). Rows without a chain_seq are pending async linkage
 * (ADR-15 batcher drains them within ~1s) or legacy pre-V49 rows — neither is a tamper breach.
 */
@Service
class AuditChainService(
  private val auditLogRepository: AuditLogRepository,
  private val jdbcTemplate: JdbcTemplate,
  private val eventPublisher: DomainEventPublisher,
  private val meterRegistry: MeterRegistry,
) {
  private val logger = KotlinLogging.logger {}

  // Gauges (registered once; suppliers run at scrape time) feed the AuditChainBreached /
  // AuditVerifyStale alerts in alerts.yml — see docs/runbooks/oncall.md.
  private var lastResult: VerifyResult? = null
  private var lastVerifiedAt: Instant? = null

  init {
    meterRegistry.gauge("squamed.audit.chain.breaches", this) { it.lastResult?.breachedTenants?.toDouble() ?: 0.0 }
    meterRegistry.gauge(
      "squamed.audit.chain.seconds.since.verify",
      this,
    ) { it.lastVerifiedAt?.let { at -> (Instant.now().epochSecond - at.epochSecond).toDouble() } ?: 0.0 }
  }

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

  // Scoped verification. A tenant asks about its own chain; a platform admin with no hospital
  // verifies the platform chain. The full sweep is verifyAll(), used by the nightly job and by an
  // explicit ?all=true - it is unbounded, so it must not be what a normal call triggers.
  @Transactional(readOnly = true)
  fun verify(hospitalId: UUID? = null): VerifyResult = verifyChains(listOf(hospitalId ?: AuditRecorder.SYSTEM_CHAIN))

  @Transactional(readOnly = true)
  fun verifyAll(): VerifyResult = verifyChains(jdbcTemplate.queryForList("SELECT hospital_id FROM audit.chain_head", UUID::class.java))

  private fun verifyChains(chainKeys: List<UUID>): VerifyResult {
    var checked = 0
    var legacy = 0L
    val breaches = mutableListOf<Breach>()
    for (key in chainKeys) {
      val chainHospitalId = if (key == AuditRecorder.SYSTEM_CHAIN) null else key
      val rows = auditLogRepository.findChainedByHospital(chainHospitalId)
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
        if (row.rowHash != AuditRecorder.computeHash(row.chainSeq!!, row.hospitalId ?: AuditRecorder.SYSTEM_CHAIN, row)) {
          breaches += Breach(key, row.chainSeq, "HASH_MISMATCH at seq ${row.chainSeq}: stored ${row.rowHash?.take(8)}")
          break
        }
        previousHash = row.rowHash
      }
      val unchained = auditLogRepository.countByHospitalIdAndChainSeqIsNull(chainHospitalId)
      legacy += unchained
    }
    return VerifyResult(checked = checked, breachedTenants = breaches.map { it.hospitalId }.distinct().size, legacyRows = legacy.toInt(), breaches = breaches)
  }

  /** Nightly verification (ADR-9: breach pages admins via the event fabric + in-app channel). */
  @Scheduled(cron = "\${squamed.audit.verify-cron:0 0 3 * * *}")
  fun verifyNightly() {
    alert(verifyAll(), platform = true)
  }

  /** Scoped on-demand check: bounded by one tenant, so it always completes. */
  fun verifyAndAlert(hospitalId: UUID? = null): VerifyResult = alert(verify(hospitalId), platform = false)

  /** Full sweep on demand. Same cost as the nightly job; the caller must ask for it explicitly. */
  fun verifyAllAndAlert(): VerifyResult = alert(verifyAll(), platform = true)

  private fun alert(
    result: VerifyResult,
    platform: Boolean,
  ): VerifyResult {
    // The freshness gauges describe the platform sweep; a tenant-scoped check must not reset them.
    if (platform) {
      lastResult = result
      lastVerifiedAt = result.verifiedAt
    }
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
}
