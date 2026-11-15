package com.squamed.core.audit.service

import com.squamed.core.audit.domain.AuditLog
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * ADR-15 async chain linkage (00.10): audit rows insert synchronously (AuditRecorder) but are
 * hash-chained here in batches — one chain-head lock + one commit per batch instead of one per
 * row. A 1,000-user burst on one hospital no longer queues every write behind the serialized
 * append (~26.5 ms/commit). Unlinked rows are durable but tamper-evident only after linkage;
 * the squamed.audit.chain.pending gauge + AuditChainPendingBacklog alert watch that window.
 */
@Component
class AuditChainBatcher(
  private val jdbcTemplate: JdbcTemplate,
  transactionManager: PlatformTransactionManager,
  meterRegistry: MeterRegistry,
) {
  private val transactionTemplate = TransactionTemplate(transactionManager)
  private var pendingRows = 0L

  init {
    meterRegistry.gauge("squamed.audit.chain.pending", this) { it.pendingRows.toDouble() }
  }

  @Scheduled(fixedDelay = 250, initialDelay = 2_000)
  fun linkPending() {
    pendingRows =
      jdbcTemplate
        .queryForObject("SELECT count(*) FROM audit.audit_log WHERE chain_seq IS NULL", Long::class.java) ?: 0
    if (pendingRows == 0L) return
    val keys =
      jdbcTemplate.queryForList(
        "SELECT DISTINCT hospital_id FROM audit.audit_log WHERE chain_seq IS NULL",
        UUID::class.java,
      )
    for (key in keys) linkChain(key)
  }

  /** Links one chain's pending rows: head lock first, then pending rows, then a single commit. */
  fun linkChain(hospitalId: UUID?) {
    val key = hospitalId ?: AuditRecorder.SYSTEM_CHAIN
    try {
      transactionTemplate.execute {
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
            ).first()
        val rows = readPending(hospitalId)
        if (rows.isEmpty()) return@execute
        var seq = head.first
        var previousHash = head.second
        val links = mutableListOf<Array<Any?>>()
        for (row in rows) {
          seq += 1
          row.chainSeq = seq
          row.previousHash = previousHash
          row.rowHash = AuditRecorder.computeHash(seq, key, row)
          previousHash = row.rowHash
          links += arrayOf(row.chainSeq, row.previousHash, row.rowHash, row.id)
        }
        jdbcTemplate.batchUpdate(
          "UPDATE audit.audit_log SET chain_seq = ?, previous_hash = ?, row_hash = ? WHERE id = ?",
          links,
        )
        jdbcTemplate.update(
          "UPDATE audit.chain_head SET last_seq = ?, last_hash = ?, updated_at = NOW() WHERE hospital_id = ?",
          seq,
          previousHash,
          key,
        )
        logger.debug { "audit chain batch: chain=$key rows=${rows.size} head_seq=$seq" }
      }
    } catch (e: Exception) {
      logger.error(e) { "audit chain linkage failed for chain $key — rows stay pending" }
    }
  }

  private fun readPending(hospitalId: UUID?): List<AuditLog> {
    // Two branches instead of `IS NOT DISTINCT FROM ?`: both predicate forms match the V52
    // partial index (IS NOT DISTINCT FROM is not btree-indexable) and the NULL branch takes
    // no bind parameter.
    val where =
      if (hospitalId == null) {
        "chain_seq IS NULL AND hospital_id IS NULL"
      } else {
        "chain_seq IS NULL AND hospital_id = ?"
      }
    val sql =
      """
      SELECT id, hospital_id, actor_user_id, actor_email, action, entity_type, entity_id,
             method, path, request_payload, response_status, response_payload,
             before_state, after_state, occurred_at
      FROM audit.audit_log
      WHERE $where
      ORDER BY occurred_at, id
      LIMIT 1000
      """.trimIndent()
    val mapper =
      RowMapper<AuditLog> { rs: ResultSet, _: Int ->
        AuditLog(
          hospitalId = rs.getObject("hospital_id", UUID::class.java),
          actorUserId = rs.getObject("actor_user_id", UUID::class.java),
          actorEmail = rs.getString("actor_email"),
          action = rs.getString("action"),
          entityType = rs.getString("entity_type"),
          entityId = rs.getString("entity_id"),
          method = rs.getString("method"),
          path = rs.getString("path"),
          requestPayload = rs.getString("request_payload"),
          responseStatus = (rs.getObject("response_status") as Number?)?.toInt(),
          responsePayload = rs.getString("response_payload"),
          beforeState = rs.getString("before_state"),
          afterState = rs.getString("after_state"),
          occurredAt = rs.getTimestamp("occurred_at").toInstant(),
        ).apply { id = rs.getObject("id", UUID::class.java) }
      }
    return if (hospitalId == null) jdbcTemplate.query(sql, mapper) else jdbcTemplate.query(sql, mapper, hospitalId)
  }
}
