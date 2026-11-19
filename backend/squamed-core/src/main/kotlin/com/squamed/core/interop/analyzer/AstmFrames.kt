package com.squamed.core.interop.analyzer

/**
 * Minimal ASTM E1381/E1394 record-frame adapter for common analysers (chunk-2 02.01).
 * Handles the wire shapes analysers actually emit: optional STX/ETX(ETB) framing with a
 * mod-256 checksum, records terminated by CR/LF, record types H,P,O,R,C,Q,L as pipe fields.
 * Malformed framing/structure throws IllegalArgumentException so the intake lane quarantines
 * the payload (zero silent drops). Real vendor quirks land with exp:E6 interface specs.
 */
object AstmFrames {
  private const val STX = 0x02.toChar()
  private const val ETX = 0x03.toChar()
  private const val ETB = 0x17.toChar()
  private const val ENQ = 0x05.toChar()
  private const val EOT = 0x04.toChar()
  private const val ACK = 0x06.toChar()
  private const val NAK = 0x15.toChar()

  fun parse(raw: String): Map<String, Any?> {
    val stripped = stripFraming(raw)
    val records =
      stripped
        .split('\r', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    require(records.isNotEmpty()) { "ASTM_EMPTY_PAYLOAD" }
    require(records.first().startsWith("H|")) { "ASTM_MISSING_HEADER" }
    val parsed =
      records.map { line ->
        val type = line.firstOrNull() ?: throw IllegalArgumentException("ASTM_EMPTY_RECORD")
        require(type in "HPORCQL") { "ASTM_UNKNOWN_RECORD_TYPE: $type" }
        require(line.contains('|')) { "ASTM_RECORD_MISSING_FIELDS: $type" }
        mapOf("type" to type.toString(), "fields" to line.split('|').drop(1))
      }
    return mapOf(
      "messageType" to "ASTM",
      "controlId" to (records.first().split('|').getOrNull(4) ?: records.first().hashCode().toString()),
      "records" to parsed,
    )
  }

  /** Removes ENQ/EOT/ACK/NAK session chars and unwraps STX...ETX/ETB + checksum frames. */
  private fun stripFraming(raw: String): String {
    require(raw.any { !it.isWhitespace() }) { "ASTM_EMPTY_PAYLOAD" }
    if (!raw.contains(STX)) return sanitizeSessionChars(raw)
    val out = StringBuilder()
    var i = 0
    while (i < raw.length) {
      when (raw[i]) {
        STX -> {
          val end = raw.indexOfAny(charArrayOf(ETX, ETB), i)
          require(end > i) { "ASTM_UNTERMINATED_FRAME" }
          out.append(raw, i + 1, end)
          // two hex checksum digits follow ETX/ETB; checksum sums STX..ETX inclusive, mod 256
          val checksum = raw.drop(end + 1).take(2)
          require(checksum.length == 2) { "ASTM_MISSING_CHECKSUM" }
          require(checksum.all { it.isDigit() || it in 'A'..'F' }) { "ASTM_CHECKSUM_NOT_HEX: $checksum" }
          val expected =
            (raw.subSequence(i, end + 1).sumOf { it.code } and 0xFF)
              .toString(16)
              .uppercase()
              .padStart(2, '0')
          require(checksum == expected) { "ASTM_CHECKSUM_MISMATCH" }
          i = end + 3
        }

        ENQ, EOT, ACK, NAK -> {
          i++
        }

        else -> {
          out.append(raw[i])
          i++
        }
      }
    }
    return out.toString()
  }

  private fun sanitizeSessionChars(raw: String): String = raw.filter { it !in charArrayOf(ENQ, EOT, ACK, NAK) }
}
