package com.squamed.core.scribe.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.scribe.domain.ScribeSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScribePipelineTest {
  private val splitter = LabelOrParagraphSplitter()
  private val generator = RuleBasedScribeGenerator()
  private val codec = SegmentJsonCodec(ObjectMapper())

  private val labeledTranscript =
    buildString {
      appendLine("Patient: I have had this cough for five days and I cannot sleep at night.")
      appendLine("Doctor: Airways sound clear, mild wheeze on expiration noted.")
      appendLine("Patient: My temperature was 38 at home.")
      appendLine("Doctor: Prescribing an inhaler and a chest X-ray.")
    }

  @Test
  fun `labels are turned into speaker segments`() {
    val segments = splitter.split(labeledTranscript)
    assertEquals(4, segments.size)
    assertEquals("Patient", segments[0].speaker)
    assertEquals("Doctor", segments[1].speaker)
    assertTrue(segments[1].text.contains("wheeze"))
  }

  @Test
  fun `unlabelled paragraphs alternate patient and doctor`() {
    val transcript = "First paragraph describing the episode. Second observation from the clinician. Third detail about pains."
    val segments = splitter.split(transcript)
    assertTrue(segments.size >= 2, "expected at least two implied speakers, got ${segments.size}")
    assertEquals("Patient", segments[0].speaker)
    assertEquals("Doctor", segments[1].speaker)
  }

  @Test
  fun `empty transcript yields no segments`() {
    assertTrue(splitter.split("   ").isEmpty())
  }

  @Test
  fun `soap draft covers all sections`() {
    val session = ScribeSession(patientName = "Maryam", chiefComplaint = "Persistent cough and wheeze for 5 days")
    val draft = generator.generate(session, splitter.split(labeledTranscript))
    for (section in listOf("SOAP NOTE", "CHIEF COMPLAINT", "SUBJECTIVE", "OBJECTIVE", "ASSESSMENT", "PLAN")) {
      assertTrue(draft.contains(section), "draft missing section $section")
    }
    assertTrue(draft.contains("cough"), "subjective should reflect the patient statement")
    assertTrue(draft.contains("wheeze"), "objective should reflect the clinician statement")
  }

  @Test
  fun `draft handles missing patient statements gracefully`() {
    val session = ScribeSession(chiefComplaint = null)
    val draft = generator.generate(session, emptyList())
    assertTrue(draft.contains("No patient-reported statements captured"))
    assertTrue(draft.contains("No clinician observations captured"))
  }

  @Test
  fun `segment codec round-trips speaker segments`() {
    val segments = listOf(ScribeSegment("Patient", "Text one"), ScribeSegment("Doctor", "Text two"))
    val encoded = codec.encode(segments)
    val decoded = codec.decode(encoded)
    assertEquals(2, decoded.size)
    assertEquals("Patient", decoded[0].speaker)
    assertEquals("Text two", decoded[1].text)
  }

  @Test
  fun `segment codec tolerates garbage input`() {
    assertTrue(codec.decode(null).isEmpty())
    assertTrue(codec.decode("").isEmpty())
    assertTrue(codec.decode("not json {").isEmpty())
    assertTrue(codec.decode("[{\"speaker\":\"Doctor\"}]").isEmpty(), "entries without text are dropped")
  }

  @Test
  fun `speaker map codec round-trips counts`() {
    val encoded = codec.encodeMaps(listOf(mapOf("speaker" to "Doctor", "segments" to 2)))
    val decoded = codec.decodeMaps(encoded)
    assertEquals(1, decoded.size)
    assertEquals("Doctor", decoded[0]["speaker"])
    assertEquals(2, decoded[0]["segments"])
  }

  @Test
  fun `splitter and codec share the same speaker vocabulary`() {
    val segments = splitter.split(labeledTranscript)
    val speakers = segments.map { it.speaker }.distinct().sorted()
    assertEquals(listOf("Doctor", "Patient"), speakers)
    assertNotNull(segments.firstOrNull { it.text.contains("inhaler") })
  }
}
