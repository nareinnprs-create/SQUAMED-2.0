package com.squamed.core.interop.hl7

data class Hl7Segment(
  val type: String,
  val fields: List<String>,
) {
  fun field(index: Int): String? = if (type == "MSH") fields.getOrNull(index - 2) else fields.getOrNull(index - 1)

  fun component(
    index: Int,
    component: Int,
  ): String? = field(index)?.split('^')?.getOrNull(component)

  fun repeat(
    index: Int,
    occurrence: Int,
  ): String? = field(index)?.split('~')?.getOrNull(occurrence)
}

data class Hl7Message(
  val segments: List<Hl7Segment>,
) {
  val messageType: String?
    get() =
      segments
        .firstOrNull { it.type == "MSH" }
        ?.component(9, 0)

  val controlId: String?
    get() = segments.firstOrNull { it.type == "MSH" }?.field(10)

  val sendingApplication: String?
    get() = segments.firstOrNull { it.type == "MSH" }?.field(3)

  val sendingFacility: String?
    get() = segments.firstOrNull { it.type == "MSH" }?.field(4)

  fun segment(type: String): Hl7Segment? = segments.firstOrNull { it.type == type }
}

object Hl7Messages {
  val encodingChars = "^~\\&"

  fun parse(raw: String): Hl7Message {
    val normalized = raw.replace("\r\n", "\r").replace("\n", "\r")
    val segments =
      normalized
        .split("\\r".toRegex())
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { rawSegment ->
          val type = rawSegment.take(3).uppercase()
          val rest = rawSegment.substring(3)
          val fields = (if (rest.startsWith("|")) rest.substring(1) else rest).split("|")
          Hl7Segment(type, fields)
        }
    return Hl7Message(segments)
  }

  fun buildAck(
    original: Hl7Message,
    ackCode: String = "AA",
    text: String? = null,
  ): String {
    val msh = original.segment("MSH")
    val now =
      java.time.ZonedDateTime
        .now(java.time.ZoneOffset.UTC)
        .format(
          java.time.format.DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss"),
        )
    val sendApp = msh?.field(3) ?: "SQUAMED"
    val sendFac = msh?.field(4) ?: "SQUAMED"
    val recvApp = msh?.field(5) ?: "SQUAMED"
    val recvFac = msh?.field(6) ?: "SQUAMED"
    val controlId = msh?.field(10) ?: "NONE"
    val messageCode = if (ackCode == "AA") "ACK" else "ACK"
    val triggerEvent = msh?.component(9, 1) ?: "A01"
    val ackBlock =
      buildList {
        add(
          "MSH|$encodingChars|$sendApp|$sendFac|$recvApp|$recvFac|$now||$messageCode^$triggerEvent^ACK|SQUAMED-$controlId-ACK|P|2.5",
        )
        val msaText =
          text ?: when (ackCode) {
            "AE" -> "Application Error"
            "AR" -> "Application Reject"
            else -> "Message Accepted"
          }
        val msa = if (ackCode == "AA") "MSA|$ackCode|$controlId|$msaText" else "MSA|$ackCode|$controlId|$msaText"
        add(msa)
        if (ackCode != "AA") {
          add("ERR|^^^$text&$msaText&HL70357")
        }
      }
    return ackBlock.joinToString("\r")
  }

  fun serialize(message: Hl7Message): String =
    message.segments.joinToString("\r") { seg ->
      "${seg.type}|${seg.fields.joinToString("|")}"
    }
}

fun Hl7Message.toView(): Map<String, Any?> =
  mapOf(
    "messageType" to messageType,
    "controlId" to controlId,
    "sendingApplication" to sendingApplication,
    "sendingFacility" to sendingFacility,
    "segments" to
      segments.map { seg ->
        mapOf(
          "type" to seg.type,
          "fields" to seg.fields.mapIndexed { i, f -> mapOf("index" to i, "value" to f) },
        )
      },
  )
