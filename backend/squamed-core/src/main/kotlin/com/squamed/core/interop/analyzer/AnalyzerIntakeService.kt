package com.squamed.core.interop.analyzer

import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.events.IntegrationErrorService
import com.squamed.core.interop.domain.AnalyzerMessageLog
import com.squamed.core.interop.hl7.Hl7Messages
import com.squamed.core.interop.hl7.toView
import com.squamed.core.interop.repository.AnalyzerMessageLogRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Analyzer intake lane (chunk-2 02.01): every inbound analyzer message — HL7 v2 via Mirth
 * (ADR-14) or ASTM via the bridge adapter — lands in `interop.analyzer_message_log` as
 * ACCEPTED or QUARANTINED. Zero silent drops: parse failures and even log-write failures
 * raise an integration-error record (the 00.09 DLQ + alert), never a discarded payload.
 */
@Service
class AnalyzerIntakeService(
  private val analyzerMessageLogRepository: AnalyzerMessageLogRepository,
  private val integrationErrorService: IntegrationErrorService,
  private val tenantResolver: TenantResolver,
  private val objectMapper: ObjectMapper,
) {
  private val logger = KotlinLogging.logger {}

  fun intake(
    raw: String,
    transport: String,
    source: String?,
  ): Map<String, Any?> {
    val hospitalId = runCatching { tenantResolver.primaryHospitalId() }.getOrNull()
    var status = "ACCEPTED"
    var ackCode: String? = if (transport == "HL7V2") "AA" else null
    var reason: String? = null
    var messageType: String? = null
    var controlId: String? = null
    var parsedJson: String? = null
    try {
      when (transport) {
        "HL7V2" -> {
          val message = Hl7Messages.parse(raw)
          require(message.segment("MSH") != null) { "HL7_MISSING_MSH" }
          messageType = message.messageType
          controlId = message.controlId
          parsedJson = objectMapper.writeValueAsString(message.toView())
        }

        "ASTM" -> {
          val frames = AstmFrames.parse(raw)
          messageType = frames["messageType"] as? String
          controlId = frames["controlId"] as? String
          parsedJson = objectMapper.writeValueAsString(frames)
        }

        else -> {
          throw IllegalArgumentException("UNSUPPORTED_TRANSPORT: $transport")
        }
      }
    } catch (e: Exception) {
      status = "QUARANTINED"
      ackCode = if (transport == "HL7V2") "CE" else null
      reason = (e.message ?: e.javaClass.simpleName).take(400)
    }
    val log = persist(hospitalId, transport, source, messageType, controlId, status, ackCode, reason, raw, parsedJson)
    if (log.id == null) {
      // log row lost - payload survives on the integration error queue, sender told AE
      return mapOf("status" to "QUARANTINED", "ackCode" to if (transport == "HL7V2") "AE" else null, "reason" to "LOG_WRITE_FAILED", "logId" to null)
    }
    return mapOf(
      "status" to status,
      "ackCode" to ackCode,
      "reason" to reason,
      "messageType" to messageType,
      "controlId" to controlId,
      "logId" to log.id,
    )
  }

  /** Shared quarantine entry for the other lanes (MLLP dev sim, replay). */
  fun quarantine(
    raw: String,
    transport: String,
    source: String,
    reason: String,
  ) {
    persist(
      runCatching { tenantResolver.primaryHospitalId() }.getOrNull(),
      transport,
      source,
      null,
      null,
      "QUARANTINED",
      if (transport ==
        "HL7V2"
      ) {
        "CE"
      } else {
        null
      },
      reason.take(400),
      raw,
      null,
    )
  }

  @Transactional
  fun replay(id: UUID): Map<String, Any?> {
    val log =
      analyzerMessageLogRepository.findById(id).orElseThrow { IllegalArgumentException("ANALYZER_MESSAGE_UNKNOWN") }
    require(log.status == "QUARANTINED") { "ANALYZER_MESSAGE_NOT_QUARANTINED" }
    val result = intake(log.rawMessage ?: "", log.transport, log.source)
    if (result["status"] == "ACCEPTED") {
      log.replayedAt = java.time.Instant.now()
      analyzerMessageLogRepository.save(log)
    }
    return result
  }

  fun history(
    limit: Int,
    status: String?,
  ): List<Map<String, Any?>> =
    analyzerMessageLogRepository
      .findAllByOrderByCreatedAtDesc()
      .take(limit)
      .filter { status == null || it.status == status }
      .map { log ->
        mapOf(
          "id" to log.id,
          "transport" to log.transport,
          "source" to log.source,
          "messageType" to log.messageType,
          "controlId" to log.controlId,
          "status" to log.status,
          "ackCode" to log.ackCode,
          "reason" to log.reason,
          "replayedAt" to log.replayedAt,
          "createdAt" to log.createdAt,
        )
      }

  fun counts(): Map<String, Any?> =
    mapOf(
      "accepted" to analyzerMessageLogRepository.countByStatus("ACCEPTED"),
      "quarantined" to analyzerMessageLogRepository.countByStatus("QUARANTINED"),
    )

  private fun persist(
    hospitalId: UUID?,
    transport: String,
    source: String?,
    messageType: String?,
    controlId: String?,
    status: String,
    ackCode: String?,
    reason: String?,
    raw: String,
    parsedJson: String?,
  ): AnalyzerMessageLog =
    try {
      analyzerMessageLogRepository.save(
        AnalyzerMessageLog(
          hospitalId = hospitalId,
          transport = transport,
          source = source?.take(120),
          messageType = messageType,
          controlId = controlId,
          status = status,
          ackCode = ackCode,
          reason = reason,
          rawMessage = raw.take(20000),
          parsedJson = parsedJson,
        ),
      )
    } catch (e: Exception) {
      // zero silent drops: even losing the log row alerts on the integration error queue
      integrationErrorService.record(
        hospitalId = hospitalId,
        integration = "analyzer",
        messageType = messageType,
        payload = raw.take(20000),
        error = "LOG_WRITE_FAILED: ${e.message ?: e.javaClass.simpleName}",
      )
      logger.error(e) { "analyzer_message_log write failed - payload recorded on integration error queue" }
      AnalyzerMessageLog(transport = transport, source = source, status = status)
    }
}
