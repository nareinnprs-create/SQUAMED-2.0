package com.squamed.core.scribe.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.scribe.domain.ScribeSession
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils

data class ScribeSegment(
  val speaker: String,
  val text: String,
)

interface SegmentSplitter {
  fun split(transcript: String): List<ScribeSegment>
}

@Service
class LabelOrParagraphSplitter : SegmentSplitter {
  private val labelRegex =
    Regex("(?m)^\\s*(Doctor|Doktor|Physician|Nurse|Patient|Family|Speaker\\s*[AB])\\s*[:\\-]\\s*(.+)$", RegexOption.IGNORE_CASE)

  override fun split(transcript: String): List<ScribeSegment> {
    val trimmed = transcript.trim()
    if (trimmed.isEmpty()) return emptyList()
    val matches = labelRegex.findAll(trimmed).toList()
    if (matches.isNotEmpty()) {
      return matches.map { ScribeSegment(it.groupValues[1].trim(), it.groupValues[2].trim()) }
    }
    val paragraphs = trimmed.split(Regex("\\n\\s*\\n|(?<=\\.)\\s+(?=[A-Z\"'])")).map { it.trim() }.filter { it.isNotEmpty() }
    if (paragraphs.isEmpty()) return listOf(ScribeSegment("Patient", trimmed))
    return paragraphs.mapIndexed { index, paragraph ->
      ScribeSegment(if (index % 2 == 0) "Patient" else "Doctor", paragraph)
    }
  }
}

interface ScribeDraftGenerator {
  fun generate(
    session: ScribeSession,
    segments: List<ScribeSegment>,
  ): String
}

@Service
class RuleBasedScribeGenerator : ScribeDraftGenerator {
  override fun generate(
    session: ScribeSession,
    segments: List<ScribeSegment>,
  ): String {
    val patientLines =
      segments
        .filter { it.speaker.equals("Patient", ignoreCase = true) }
        .map { it.text }
    val doctorLines =
      segments
        .filter {
          !it.speaker.equals("Patient", ignoreCase = true)
        }.map { it.text }

    val subjective =
      if (patientLines.isNotEmpty()) {
        "- " + patientLines.joinToString(" ") { it.trim() }
      } else {
        "- No patient-reported statements captured in this recording."
      }
    val objective =
      if (doctorLines.isNotEmpty()) {
        doctorLines.joinToString(" ") { it.trim() }
      } else {
        "- No clinician observations captured in this recording."
      }
    val cc = session.chiefComplaint?.trim() ?: ""
    val assessment =
      if (cc.isNotEmpty()) {
        "Assessment (pending clinician sign-off) related to: $cc."
      } else {
        "Assessment pending clinician sign-off."
      }

    return buildString {
      appendLine("SOAP NOTE — AI DRAFT (unverified, for clinician review)")
      appendLine()
      appendLine("CHIEF COMPLAINT: ${cc.ifEmpty { "—" }}")
      appendLine()
      appendLine("SUBJECTIVE")
      appendLine(subjective)
      appendLine()
      appendLine("OBJECTIVE")
      appendLine(objective)
      appendLine()
      appendLine("ASSESSMENT")
      appendLine(assessment)
      appendLine()
      appendLine("PLAN")
      appendLine("- Investigations: as ordered")
      appendLine("- Medications: reconcile before discharge")
      appendLine("- Follow-up: schedule before close")
    }
  }
}

@Service
class SegmentJsonCodec(
  private val objectMapper: ObjectMapper,
) {
  fun encode(segments: List<ScribeSegment>): String = objectMapper.writeValueAsString(segments.map { mapOf("speaker" to it.speaker, "text" to it.text) })

  fun encodeMaps(entries: List<Map<String, Any?>>): String = objectMapper.writeValueAsString(entries)

  fun decode(json: String?): List<ScribeSegment> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
      val nodes = objectMapper.readTree(json)
      nodes.mapNotNull { node ->
        val speaker = node.path("speaker").asText("")
        val text = node.path("text").asText("")
        if (StringUtils.hasText(text)) ScribeSegment(speaker.ifBlank { "Unknown" }, text) else null
      }
    } catch (_: Exception) {
      emptyList()
    }
  }

  fun decodeMaps(json: String?): List<Map<String, Any?>> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
      val nodes = objectMapper.readTree(json)
      nodes.mapNotNull { node ->
        mapOf("speaker" to node.path("speaker").asText(""), "segments" to node.path("segments").asInt(0))
      }
    } catch (_: Exception) {
      emptyList()
    }
  }
}
