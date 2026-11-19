package com.squamed.core.interop.hl7

import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.events.IntegrationErrorService
import com.squamed.core.events.IntegrationReplayer
import com.squamed.core.interop.analyzer.AnalyzerIntakeService
import com.squamed.core.interop.domain.Hl7MessageLog
import com.squamed.core.interop.repository.Hl7MessageLogRepository
import com.squamed.core.patient.api.PatientCreateDto
import com.squamed.core.patient.service.PatientService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.Executors

@Service
class Hl7Service(
  private val hl7MessageLogRepository: Hl7MessageLogRepository,
  private val patientService: PatientService,
  private val integrationErrorService: IntegrationErrorService,
  private val analyzerIntakeService: AnalyzerIntakeService,
  private val tenantResolver: TenantResolver,
  private val objectMapper: ObjectMapper,
  transactionManager: PlatformTransactionManager,
  @Value("\${squamed.hl7.mllp.port:2575}") private val mllpPort: Int,
) : IntegrationReplayer {
  private val logger = KotlinLogging.logger {}

  override val integration: String = "hl7"

  /** DLQ replay (00.09): re-run a stored inbound payload through the normal receive path. */
  override fun replay(raw: String): Boolean = (receive(raw, "replay")["ackCode"] as? String) == "AA"

  private val importTransactions =
    TransactionTemplate(transactionManager).apply {
      setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
    }

  @Volatile
  var mllpRunning: Boolean = false

  @Volatile
  var healthDetail: String? = null

  private var serverSocket: ServerSocket? = null
  private val executor = Executors.newFixedThreadPool(4)

  fun startMllp() {
    try {
      val server = ServerSocket(mllpPort, 16, java.net.InetAddress.getLoopbackAddress())
      serverSocket = server
      mllpRunning = true
      healthDetail = "Listening on 127.0.0.1:$mllpPort"
      logger.info { "HL7 MLLP server listening on 127.0.0.1:$mllpPort" }
      executor.submit { acceptLoop(server) }
    } catch (e: Exception) {
      mllpRunning = false
      healthDetail = "Failed to bind MLLP: ${e.message}"
      logger.warn(e) { "HL7 MLLP server failed to start on port $mllpPort" }
    }
  }

  fun stopMllp() {
    serverSocket?.close()
    serverSocket = null
    mllpRunning = false
    executor.shutdownNow()
  }

  private fun acceptLoop(server: ServerSocket) {
    while (!server.isClosed) {
      try {
        val socket = server.accept()
        executor.submit { handleConnection(socket) }
      } catch (e: Exception) {
        if (server.isClosed) break
        logger.warn(e) { "MLLP accept failed" }
      }
    }
  }

  private fun handleConnection(socket: java.net.Socket) {
    socket.use { s ->
      s.soTimeout = 30000
      val input = s.getInputStream()
      val output = s.getOutputStream()
      val buffer = java.io.ByteArrayOutputStream()
      var started = false
      var done = false
      val reader = input.buffered(8192)
      while (!done) {
        val b = reader.read()
        if (b == -1) break
        when (b.toInt()) {
          0x0b -> {
            buffer.reset()
            started = true
          }

          0x1c -> {
            done = true
            val terminator = reader.read()
            if (terminator != 0x0d) {
              // malformed terminator: treat the 0x1c as body and continue
              buffer.write(b)
              done = false
            }
          }

          else -> {
            if (started) buffer.write(b)
          }
        }
      }
      if (buffer.size() > 0) {
        val raw = buffer.toString("UTF-8")
        val view = processInbound(raw, "MLLP")
        val ack = view["ack"] as? String ?: ""
        val framed = String.format("%c%s%c%c", 0x0b, ack, 0x1c, 0x0d)
        output.write(framed.toByteArray(Charsets.UTF_8))
        output.flush()
      }
    }
  }

  fun parse(raw: String): Map<String, Any?> {
    val message = Hl7Messages.parse(raw)
    require(message.segment("MSH") != null) { "INVALID_HL7_MESSAGE" }
    val ack = Hl7Messages.buildAck(message)
    return mapOf(
      "messageType" to message.messageType,
      "controlId" to message.controlId,
      "sendingApplication" to message.sendingApplication,
      "sendingFacility" to message.sendingFacility,
      "segments" to
        message.segments.map { seg ->
          mapOf(
            "type" to seg.type,
            "fields" to
              seg.fields.mapIndexed { i, f ->
                mapOf("index" to i, "value" to f, "components" to f.split("^"))
              },
          )
        },
      "ack" to ack,
    )
  }

  @Transactional
  fun receive(
    raw: String,
    source: String,
  ): Map<String, Any?> {
    val message =
      try {
        Hl7Messages.parse(raw)
      } catch (e: Exception) {
        // zero silent drops (02.01): unparseable REST payload -> quarantine row + alert + CE ack
        analyzerIntakeService.quarantine(raw, transport = "HL7V2", source = source, reason = "PARSE_FAILED: ${e.message ?: e.javaClass.simpleName}")
        return mapOf(
          "status" to "QUARANTINED",
          "ackCode" to "CE",
          "reason" to (e.message ?: e.javaClass.simpleName),
          "importedPatientId" to null,
          "importError" to null,
          "ack" to Hl7Messages.buildAck(Hl7Message(emptyList()), "CE", e.message),
          "logId" to null,
        )
      }
    var ackCode = "AA"
    var error: String? = null
    var importedPatientId: UUID? = null
    try {
      importedPatientId = importTransactions.execute { importFromAdt(message) }
    } catch (e: Exception) {
      ackCode = "AE"
      error = e.message
      integrationErrorService.record(
        hospitalId = runCatching { tenantResolver.primaryHospitalId() }.getOrNull(),
        integration = "hl7",
        messageType = message.messageType,
        payload = raw,
        error = error ?: e.javaClass.simpleName,
      )
      logger.warn(e) { "HL7 ADT import failed type=${message.messageType}" }
    }
    val ack = Hl7Messages.buildAck(message, ackCode, error)
    val log =
      Hl7MessageLog(
        messageType = message.messageType,
        controlId = message.controlId,
        direction = "INBOUND",
        source = source,
        ackCode = ackCode,
        rawMessage = raw.take(20000),
        parsedJson = objectMapper.writeValueAsString(message.toView()),
      )
    hl7MessageLogRepository.save(log)
    return mapOf(
      "messageType" to message.messageType,
      "controlId" to message.controlId,
      "ackCode" to ackCode,
      "importedPatientId" to importedPatientId,
      "importError" to error,
      "ack" to ack,
      "logId" to log.id,
    )
  }

  private fun processInbound(
    raw: String,
    source: String,
  ): Map<String, Any?> {
    val message =
      try {
        Hl7Messages.parse(raw)
      } catch (e: Exception) {
        // zero silent drops (02.01): malformed MLLP payload -> quarantine + alert + ERR ack
        analyzerIntakeService.quarantine(raw, transport = "HL7V2", source = source, reason = "PARSE_FAILED: ${e.message ?: e.javaClass.simpleName}")
        return mapOf(
          "ackCode" to "CE",
          "importError" to (e.message ?: e.javaClass.simpleName),
          "ack" to Hl7Messages.buildAck(Hl7Message(emptyList()), "CE", e.message),
        )
      }
    var ackCode = "AA"
    var error: String? = null
    try {
      importTransactions.execute { importFromAdt(message) }
    } catch (e: Exception) {
      ackCode = "AE"
      error = e.message
      integrationErrorService.record(
        hospitalId = runCatching { tenantResolver.primaryHospitalId() }.getOrNull(),
        integration = "hl7",
        messageType = message.messageType,
        payload = raw,
        error = error ?: e.javaClass.simpleName,
      )
      logger.warn(e) { "HL7 MLLP import failed type=${message.messageType}" }
    }
    val ack = Hl7Messages.buildAck(message, ackCode, error)
    val log =
      Hl7MessageLog(
        messageType = message.messageType,
        controlId = message.controlId,
        direction = "INBOUND",
        source = source,
        ackCode = ackCode,
        rawMessage = raw.take(20000),
        parsedJson = objectMapper.writeValueAsString(message.toView()),
      )
    try {
      hl7MessageLogRepository.save(log)
    } catch (e: Exception) {
      // zero silent drops (02.01): losing the log row alerts on the integration error queue
      integrationErrorService.record(
        hospitalId = runCatching { tenantResolver.primaryHospitalId() }.getOrNull(),
        integration = "hl7",
        messageType = message.messageType,
        payload = raw.take(20000),
        error = "LOG_WRITE_FAILED: ${e.message ?: e.javaClass.simpleName}",
      )
      logger.warn(e) { "Could not persist MLLP message log - payload recorded on integration error queue" }
    }
    return mapOf(
      "messageType" to message.messageType,
      "controlId" to message.controlId,
      "ackCode" to ackCode,
      "importError" to error,
      "ack" to ack,
    )
  }

  private fun importFromAdt(message: Hl7Message): UUID? {
    val type = message.messageType ?: return null
    if (!type.startsWith("ADT")) return null
    val pid = message.segment("PID") ?: return null
    val mrn = pid.component(3, 0)?.takeIf { it.isNotBlank() }
    val familyName = pid.component(5, 0)
    val givenName = pid.component(5, 1)
    val middleName = pid.component(5, 2)
    val dob = pid.field(7)?.toLocalDateOrNull()
    val gender = pid.field(8)?.takeIf { it.isNotBlank() }
    val phone = pid.field(13)?.substringBefore('^')?.takeIf { it.isNotBlank() }

    require(mrn != null) { "ADT missing patient identifier (PID-3)" }
    require(givenName != null || familyName != null) { "ADT missing patient name (PID-5)" }

    return patientService.findOrCreateFromHl7(
      // ADR-13: MLLP has no principal; ADT imports attribute to the primary hospital (stage 1).
      hospitalId = tenantResolver.primaryHospitalId(),
      mrn = mrn,
      firstName = givenName,
      middleName = middleName,
      lastName = familyName,
      dateOfBirth = dob,
      gender = gender,
      phone = phone,
    )
  }

  fun history(limit: Int): List<Map<String, Any?>> =
    hl7MessageLogRepository.findAllByOrderByCreatedAtDesc().take(limit).map { log ->
      mapOf(
        "id" to log.id,
        "messageType" to log.messageType,
        "controlId" to log.controlId,
        "direction" to log.direction,
        "source" to log.source,
        "ackCode" to log.ackCode,
        "createdAt" to log.createdAt,
      )
    }

  fun health(): Map<String, Any?> =
    mapOf(
      "mllpRunning" to mllpRunning,
      "mllpPort" to mllpPort,
      "detail" to healthDetail,
    )

  private fun String.toLocalDateOrNull(): java.time.LocalDate? =
    try {
      java.time.LocalDate.parse(
        this,
        java.time.format.DateTimeFormatter
          .ofPattern("yyyyMMdd"),
      )
    } catch (e: Exception) {
      null
    }
}
