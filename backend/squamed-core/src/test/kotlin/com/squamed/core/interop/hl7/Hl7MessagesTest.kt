package com.squamed.core.interop.hl7

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Hl7MessagesTest {
  private val validAdt =
    buildString {
      append("MSH|^~\\&|E2E|HOSP|SQUAMED|MIR|20260906120000||ADT^A04^ADT_A04|CTRL-1001|P|2.5\r")
      append("EVN|A04|20260906120000\r")
      append("PID|1||MRN-9001||Smith^John||19800101|M")
    }

  @Test
  fun `parses ADT message type and control id`() {
    val message = Hl7Messages.parse(validAdt)
    assertEquals("ADT", message.messageType)
    assertEquals("CTRL-1001", message.controlId)
    assertEquals("E2E", message.sendingApplication)
    assertEquals("HOSP", message.sendingFacility)
  }

  @Test
  fun `MSH field indexing matches HL7 spec`() {
    val message = Hl7Messages.parse(validAdt)
    val msh = checkNotNull(message.segment("MSH")) { "message must contain MSH" }
    // MSH-2 is the encoding characters (position after the trigger bar is index 0)
    assertEquals("^~\\&", msh.field(2))
    // MSH-9 is trigger type (fields[7] because MSH shifts by one)
    assertEquals("ADT^A04^ADT_A04", msh.field(9))
    assertEquals("ADT", msh.component(9, 0))
    assertEquals("A04", msh.component(9, 1))
    // MSH-10 control id
    assertEquals("CTRL-1001", msh.field(10))
    // MSH-12 version
    assertEquals("2.5", msh.field(12))
  }

  @Test
  fun `non-MSH field indexing matches HL7 spec`() {
    val message = Hl7Messages.parse(validAdt)
    val pid = checkNotNull(message.segment("PID")) { "message must contain PID" }
    // PID-3 patient identifier (fields[2] because non-MSH start after the segment name bar)
    assertEquals("MRN-9001", pid.field(3))
    // PID-5 patient name with name components
    assertEquals("Smith^John", pid.field(5))
    assertEquals("Smith", pid.component(5, 0))
    assertEquals("John", pid.component(5, 1))
    // PID-7 date of birth
    assertEquals("19800101", pid.field(7))
    // PID-8 gender
    assertEquals("M", pid.field(8))
  }

  @Test
  fun `ack success contains MSA AA`() {
    val ack = Hl7Messages.buildAck(Hl7Messages.parse(validAdt))
    assertTrue(ack.startsWith("MSH|"), "ACK must start with MSH")
    assertTrue(ack.contains("^A04^ACK"), "ACK trigger event should mirror MSH-9")
    assertTrue(ack.contains("|AA|CTRL-1001|"), "MSA AA must carry the original control id")
    assertTrue(ack.contains("Message Accepted"))
  }

  @Test
  fun `ack error contains MSA AE and ERR segment`() {
    val ack = Hl7Messages.buildAck(Hl7Messages.parse(validAdt), ackCode = "AE", text = "Patient date of birth required")
    assertTrue(ack.contains("|AE|CTRL-1001|"))
    assertTrue(ack.contains("ERR|"))
    assertTrue(ack.contains("Patient date of birth required"))
  }

  @Test
  fun `serialize round-trips a parsed message`() {
    val message = Hl7Messages.parse(validAdt)
    val serialized = Hl7Messages.serialize(message)
    val reparsed = Hl7Messages.parse(serialized)
    assertEquals(message.controlId, reparsed.controlId)
    assertEquals(3, reparsed.segments.size)
    val pid = checkNotNull(reparsed.segment("PID")) { "reparsed must contain PID" }
    assertEquals("MRN-9001", pid.field(3))
  }

  @Test
  fun `handles LF and CRLF newline variants`() {
    val withLf = validAdt.replace("\r", "\n")
    val message = Hl7Messages.parse(withLf)
    assertEquals("ADT", message.messageType)
    assertEquals(3, message.segments.size)
  }
}
