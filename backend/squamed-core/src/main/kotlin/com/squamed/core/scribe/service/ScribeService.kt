package com.squamed.core.scribe.service

import com.squamed.core.scribe.domain.ScribeSession
import com.squamed.core.scribe.repository.ScribeSessionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ScribeService(
  private val repository: ScribeSessionRepository,
  private val splitter: SegmentSplitter,
  private val generator: ScribeDraftGenerator,
  private val segmentCodec: SegmentJsonCodec,
  private val sttProvider: SpeechToTextProvider,
) {
  private val logger = KotlinLogging.logger {}

  companion object {
    private val STAGES =
      linkedSetOf(
        "CREATED",
        "TRANSCRIBING",
        "TRANSCRIBED",
        "SPEAKERS_RESOLVED",
        "DRAFTED",
        "SIGNED",
      )
  }

  @Transactional(readOnly = true)
  fun list(): List<ScribeSession> = repository.findAllByOrderByCreatedAtDesc()

  @Transactional(readOnly = true)
  fun find(id: UUID): ScribeSession = repository.findById(id).orElseThrow { IllegalArgumentException("SCRIBE_SESSION_NOT_FOUND") }

  @Transactional
  fun create(
    patientId: UUID?,
    encounterId: UUID?,
    patientName: String?,
    chiefComplaint: String?,
  ): ScribeSession =
    repository.save(
      ScribeSession(
        patientId = patientId,
        encounterId = encounterId,
        patientName = patientName,
        chiefComplaint = chiefComplaint,
        stage = "CREATED",
      ),
    )

  @Transactional
  fun startRecording(id: UUID): ScribeSession {
    val session = find(id)
    requireStage(session, "CREATED", "TRANSCRIBING")
    session.stage = "TRANSCRIBING"
    return repository.save(session)
  }

  @Transactional
  fun processAudio(
    id: UUID,
    audioBytes: ByteArray,
    contentType: String?,
  ): ScribeSession {
    val session = find(id)
    if (session.stage !in setOf("CREATED", "TRANSCRIBING")) {
      requireStage(session, null, "TRANSCRIBED")
    }
    session.stage = "TRANSCRIBING"
    val transcribed = sttProvider.transcribe(audioBytes, contentType)
    val text = transcribed.trim().ifEmpty { null }
    if (text != null) {
      applyTranscript(session, text, null)
    } else {
      session.stage = "TRANSCRIBED"
    }
    return repository.save(session)
  }

  @Transactional
  fun stopRecording(
    id: UUID,
    transcriptText: String?,
    durationSeconds: Int?,
  ): ScribeSession {
    val session = find(id)
    session.durationSeconds = durationSeconds ?: 0
    val text = transcriptText?.trim()?.let { if (it.isEmpty()) null else it }
    if (text != null) {
      applyTranscript(session, text, null)
    } else {
      session.stage = "TRANSCRIBED"
    }
    return repository.save(session)
  }

  @Transactional
  fun setTranscript(
    id: UUID,
    text: String,
    segments: List<ScribeSegment>?,
  ): ScribeSession {
    val session = find(id)
    applyTranscript(session, text.trim(), segments)
    return repository.save(session)
  }

  @Transactional
  fun generateDraft(id: UUID): ScribeSession {
    val session = find(id)
    requireStage(session, null, "DRAFTED")
    if (session.stage == "TRANSCRIBING") {
      session.stage = "TRANSCRIBED"
    }
    val segments =
      segmentCodec
        .decode(session.segmentsJson)
        .ifEmpty { splitter.split(session.transcript ?: "") }
    val draft = generator.generate(session, segments)
    session.draft = draft
    session.stage = "DRAFTED"
    return repository.save(session)
  }

  @Transactional
  fun updateDraft(
    id: UUID,
    draft: String,
  ): ScribeSession {
    val session = find(id)
    session.draft = draft
    if (session.stage in setOf("CREATED", "TRANSCRIBED", "SPEAKERS_RESOLVED")) {
      session.stage = "DRAFTED"
    }
    return repository.save(session)
  }

  @Transactional
  fun sign(id: UUID): ScribeSession {
    val session = find(id)
    if (session.stage !in setOf("DRAFTED", "TRANSCRIBED", "SPEAKERS_RESOLVED")) {
      requireStage(session, null, "SIGNED")
    }
    if (session.draft.isNullOrBlank()) {
      throw IllegalArgumentException("SCRIBE_DRAFT_REQUIRED")
    }
    session.status = "SIGNED"
    session.stage = "SIGNED"
    session.signedAt = Instant.now()
    return repository.save(session)
  }

  @Transactional
  fun delete(id: UUID) {
    repository.deleteById(id)
  }

  @Transactional(readOnly = true)
  fun pipeline(): Map<String, Any?> =
    mapOf(
      "stages" to STAGES.toList(),
      "currentStage" to null,
      "sessions" to list().size,
    )

  private fun applyTranscript(
    session: ScribeSession,
    text: String,
    segments: List<ScribeSegment>?,
  ) {
    session.transcript = text
    val resolved = segments ?: splitter.split(text)
    if (resolved.isNotEmpty()) {
      session.segmentsJson = segmentCodec.encode(resolved)
      session.speakerMapJson =
        segmentCodec.encodeMaps(
          resolved.map { it.speaker }.distinct().map { speaker ->
            mapOf("speaker" to speaker, "segments" to resolved.count { it.speaker == speaker })
          },
        )
      session.stage = "SPEAKERS_RESOLVED"
    } else {
      session.stage = "TRANSCRIBED"
    }
  }

  private fun requireStage(
    session: ScribeSession,
    from: String?,
    to: String,
  ) {
    if (from != null && session.stage != from) {
      throw IllegalArgumentException("SCRIBE_INVALID_STAGE_TRANSITION from=${session.stage} expected=$from")
    }
    if (STAGES.indexOf(session.stage) > STAGES.indexOf(to)) {
      throw IllegalArgumentException("SCRIBE_STAGE_ALREADY_PASSED stage=${session.stage} target=$to")
    }
  }
}
