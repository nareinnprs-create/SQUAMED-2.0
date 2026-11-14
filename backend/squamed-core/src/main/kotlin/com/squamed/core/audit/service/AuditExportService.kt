package com.squamed.core.audit.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.audit.repository.AuditLogRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.ObjectLockRetentionMode
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRetentionRequest
import java.net.URI
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * ADR-9 WORM export (00.10): nightly job streams each tenant's un-exported audit rows to object
 * storage as JSONL (MinIO dev / S3 prod bucket `squamed-audit-worm`), then records the export
 * cursor. Objects get a 10-year Object-Lock retention when the bucket supports it; buckets
 * without object-lock (dev default) skip retention with a warning so export still lands.
 */
@Service
class AuditExportService(
  private val auditLogRepository: AuditLogRepository,
  private val jdbcTemplate: JdbcTemplate,
  private val objectMapper: ObjectMapper,
  @Value("\${squamed.audit-worm.enabled:true}") private val enabled: Boolean,
  @Value("\${squamed.audit-worm.endpoint:http://localhost:9000}") private val endpoint: String,
  @Value("\${squamed.audit-worm.region:us-east-1}") private val region: String,
  @Value("\${squamed.audit-worm.access-key:squamed}") private val accessKey: String,
  @Value("\${squamed.audit-worm.secret-key:squamed_dev_password}") private val secretKey: String,
  @Value("\${squamed.audit-worm.bucket:squamed-audit-worm}") private val bucket: String,
  @Value("\${squamed.audit-worm.path-style:true}") private val pathStyle: Boolean,
  @Value("\${squamed.audit-worm.retention-years:10}") private val retentionYears: Long,
) {
  private val logger = KotlinLogging.logger {}
  private lateinit var s3: S3Client

  data class ExportResult(
    val exported: Int,
    val objectKey: String?,
    val retentionApplied: Boolean,
  )

  @PostConstruct
  fun init() {
    if (!enabled) {
      logger.warn { "Audit WORM export disabled (squamed.audit-worm.enabled=false)" }
      return
    }
    val builder =
      S3Client
        .builder()
        .region(Region.of(region))
        .endpointOverride(URI.create(endpoint))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
    if (pathStyle) builder.serviceConfiguration { it.pathStyleAccessEnabled(true) }
    s3 = builder.build()
    val exists =
      try {
        s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build())
        true
      } catch (e: Exception) {
        false
      }
    if (!exists) {
      // objectLockEnabled: WORM at the bucket level (MinIO + S3 both honor it at create time).
      s3.createBucket(
        CreateBucketRequest
          .builder()
          .bucket(bucket)
          .objectLockEnabledForBucket(true)
          .build(),
      )
      logger.info { "Created audit WORM bucket $bucket (object lock enabled)" }
    }
  }

  /** Nightly export (03:30), after the 03:00 chain verification. */
  @Scheduled(cron = "\${squamed.audit.export-cron:0 30 3 * * *}")
  fun exportNightly() {
    if (!enabled) return
    val (exported, objectKey) = exportNow()
    logger.info { "Nightly audit export: $exported rows, object $objectKey" }
  }

  /** Runs the export immediately (used by the admin endpoint + demo). */
  fun exportNow(): Pair<Int, String?> {
    var total = 0
    var lastKey: String? = null
    val chainKeys = jdbcTemplate.queryForList("SELECT hospital_id FROM audit.chain_head", UUID::class.java)
    for (key in chainKeys) {
      val hospitalId = if (key == AuditRecorder.SYSTEM_CHAIN) null else key
      val lastSeq = queryCursor(key)
      val rows = auditLogRepository.findChainedSince(hospitalId, lastSeq)
      if (rows.isEmpty()) continue
      val jsonl = rows.joinToString("\n") { objectMapper.writeValueAsString(toExportRow(it)) }
      val exportKey = "audit/$key/${LocalDate.now(ZoneOffset.UTC)}/seq-$lastSeq-${rows.last().chainSeq}.jsonl"
      putWorm(jsonl, key, exportKey)
      advanceCursor(key, rows.last().chainSeq!!, exportKey)
      total += rows.size
      lastKey = exportKey
    }
    return total to lastKey
  }

  private fun queryCursor(key: UUID): Long =
    jdbcTemplate
      .query(
        "SELECT last_seq FROM audit.export_cursor WHERE hospital_id = ?",
        { rs, _ -> rs.getLong(1) },
        key,
      ).firstOrNull() ?: 0L

  private fun advanceCursor(
    key: UUID,
    seq: Long,
    objectKey: String,
  ) {
    jdbcTemplate.update(
      """
      INSERT INTO audit.export_cursor (hospital_id, last_seq, object_key, exported_at)
      VALUES (?, ?, ?, NOW())
      ON CONFLICT (hospital_id) DO UPDATE SET last_seq = EXCLUDED.last_seq, object_key = EXCLUDED.object_key, exported_at = NOW()
      """.trimIndent(),
      key,
      seq,
      objectKey,
    )
  }

  private fun putWorm(
    jsonl: String,
    chainKey: UUID,
    exportKey: String,
  ) {
    s3.putObject(
      PutObjectRequest
        .builder()
        .bucket(bucket)
        .key(exportKey)
        .contentType("application/x-ndjson")
        .build(),
      RequestBody.fromBytes(jsonl.toByteArray(Charsets.UTF_8)),
    )
    try {
      // WORM: hold the object for the retention period (ADR-9: 10 years).
      val retainUntil =
        java.time.Instant
          .now()
          .plusSeconds(retentionYears * 365L * 24 * 3600)
      s3.putObjectRetention(
        PutObjectRetentionRequest
          .builder()
          .bucket(bucket)
          .key(exportKey)
          .retention { it.mode(ObjectLockRetentionMode.COMPLIANCE).retainUntilDate(retainUntil) }
          .build(),
      )
    } catch (e: Exception) {
      logger.warn { "Object-lock retention not applied to $bucket/$exportKey (bucket without object lock?): ${e.message}" }
    }
  }

  /** Export row: the full audit record incl. chain fields, actor identity and before/after. */
  private fun toExportRow(row: com.squamed.core.audit.domain.AuditLog): Map<String, Any?> =
    mapOf(
      "id" to row.id,
      "hospitalId" to row.hospitalId,
      "actorUserId" to row.actorUserId,
      "actorEmail" to row.actorEmail,
      "action" to row.action,
      "entityType" to row.entityType,
      "entityId" to row.entityId,
      "ipAddress" to row.ipAddress,
      "userAgent" to row.userAgent,
      "method" to row.method,
      "path" to row.path,
      "requestPayload" to row.requestPayload,
      "responseStatus" to row.responseStatus,
      "responsePayload" to row.responsePayload,
      "chainSeq" to row.chainSeq,
      "previousHash" to row.previousHash,
      "rowHash" to row.rowHash,
      "actorSessionId" to row.actorSessionId,
      "deviceId" to row.deviceId,
      "beforeState" to row.beforeState,
      "afterState" to row.afterState,
      "occurredAt" to row.occurredAt.toString(),
    )
}
