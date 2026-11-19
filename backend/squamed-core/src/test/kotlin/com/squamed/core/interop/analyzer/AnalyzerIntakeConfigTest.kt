package com.squamed.core.interop.analyzer

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * chunk-2 02.01: the version-controlled Mirth channel config must stay structurally valid
 * (MLLP source -> SQM analyzer intake API destination), and the ASTM adapter must never
 * silently pass malformed framing.
 */
class AnalyzerIntakeConfigTest {
  private val mapper = ObjectMapper()

  @Test
  fun `mirth channel config is version-controlled and wired to the analyzer intake API`() {
    val json =
      javaClass.classLoader
        .getResourceAsStream("mirth/analyzer-intake.channel.json")!!
        .readBytes()
        .decodeToString()
    val channel = mapper.readTree(json)
    assertEquals("Analyzer Intake - HL7v2 to SQM Analyzer API", channel["metadata"]["name"].asText())
    assertEquals("MLLP", channel["sourceConnector"]["properties"]["protocol"].asText())
    assertEquals("HL7V2", channel["sourceConnector"]["properties"]["dataType"].asText())
    val destination = channel["destinationConnectors"][0]
    assertTrue(destination["properties"]["httpConnectorProperties"]["url"].asText().endsWith("/api/v1/analyzer/messages"))
    assertTrue(destination["responseTransformer"]["failIfResponseCodeOutside200s"].asBoolean())
  }

  @Test
  fun `astm adapter parses well-formed frames with checksums`() {
    val body =
      "H|\\^&|||Analyzer^1.0||CBC-01|P|1||20261119090000\r\n" +
        "P|1||MRN123^1||Doe^John||19700101|M\r\n" +
        "R|1|^^^K|5.1|mmol/L|3.5-5.1|N||F||20261119090500\r\n" +
        "L|1|N"
    val frame = 0x02.toChar() + body + 0x03.toChar()
    val checksum = (frame.sumOf { it.code } and 0xFF).toString(16).uppercase().padStart(2, '0')
    val parsed = AstmFrames.parse(frame + checksum + "\r\n")
    assertEquals("ASTM", parsed["messageType"])
    val records = parsed["records"] as List<*>
    assertEquals(4, records.size)
    assertEquals("R", (records[2] as Map<*, *>)["type"])
  }

  @Test
  fun `astm adapter rejects malformed payloads instead of silently accepting`() {
    assertThrows(IllegalArgumentException::class.java) { AstmFrames.parse("") }
    assertThrows(IllegalArgumentException::class.java) { AstmFrames.parse("P|1||MRN123\r\nR|1|x") } // missing header
    assertThrows(IllegalArgumentException::class.java) { AstmFrames.parse("H|\\^&|||Analyzer\r\nX|1|bogus") } // unknown record type
    assertThrows(IllegalArgumentException::class.java) { AstmFrames.parse("H|\\^&|||Analyzer" + 0x0d.toChar() + 0x0a.toChar() + "R") } // record with no fields
    assertThrows(IllegalArgumentException::class.java) { AstmFrames.parse(0x02.toChar() + "H|\\^&|||Analyzer") } // unterminated frame
    assertThrows(IllegalArgumentException::class.java) { AstmFrames.parse(0x02.toChar() + "H|\\^&|||Analyzer" + 0x03.toChar() + "ZZ") } // bad checksum
  }
}
