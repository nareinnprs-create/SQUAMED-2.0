package com.squamed.core.scribe.service

import com.squamed.core.common.tenant.requireSameHospital
import com.squamed.core.consent.repository.ConsentRepository
import com.squamed.core.scribe.domain.DraftCorrection
import com.squamed.core.scribe.domain.ScribeSession
import com.squamed.core.scribe.repository.DraftCorrectionRepository
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
  private val consentRepository: ConsentRepository,
  private val correctionRepository: DraftCorrectionRepository,
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
  fun list(hospitalId: UUID?): List<ScribeSession> = repository.findAllByOrderByCreatedAtDesc(hospitalId)

  @Transactional(readOnly = true)
  fun find(
    id: UUID,
    hospitalId: UUID?,
  ): ScribeSession {
    val session = repository.findById(id).orElseThrow { IllegalArgumentException("SCRIBE_SESSION_NOT_FOUND") }
    // 00.04 tenant sweep: session reads (full transcript) are gated on the row's hospital.
    requireSameHospital(hospitalId, session.hospitalId)
    return session
  }

  @Transactional
  fun create(
    patientId: UUID?,
    encounterId: UUID?,
    patientName: String?,
    chiefComplaint: String?,
    hospitalId: UUID?,
    consentId: UUID? = null,
  ): ScribeSession {
    // 05.05: recording a patient conversation needs a granted treatment consent, recorded on the session.
    if (patientId != null) {
      val consent =
        consentId?.let { consentRepository.findById(it).orElse(null) }
          ?: throw IllegalArgumentException("SCRIBE_CONSENT_REQUIRED")
      if (consent.patientId != patientId || consent.status != "granted" || consent.purpose !in setOf("treatment", "data_sharing")) {
        throw IllegalArgumentException("SCRIBE_CONSENT_REQUIRED")
      }
      if (consent.expiresAt != null && consent.expiresAt!!.isBefore(Instant.now())) {
        throw IllegalArgumentException("SCRIBE_CONSENT_EXPIRED")
      }
    }
    return repository.save(
      ScribeSession(
        hospitalId = hospitalId,
        patientId = patientId,
        encounterId = encounterId,
        patientName = patientName,
        chiefComplaint = chiefComplaint,
        stage = "CREATED",
        consentId = consentId,
      ),
    )
  }

  @Transactional
  fun startRecording(id: UUID): ScribeSession {
    val session = find(id, null)
    requireStage(session, "CREATED", "TRANSCRIBING")
    session.recordingStartedAt = Instant.now()
    session.stage = "TRANSCRIBING"
    return repository.save(session)
  }

  @Transactional
  fun processAudio(
    id: UUID,
    audioBytes: ByteArray,
    contentType: String?,
  ): ScribeSession {
    val session = find(id, null)
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
    val session = find(id, null)
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
    val session = find(id, null)
    applyTranscript(session, text.trim(), segments)
    return repository.save(session)
  }

  @Transactional
  fun generateDraft(id: UUID): ScribeSession {
    val session = find(id, null)
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
    actorId: UUID? = null,
  ): ScribeSession {
    val session = find(id, null)
    val previous = session.draft
    // 05.05: a clinician edit of the AI draft is eval data, not a silent overwrite.
    if (previous != null && previous != draft) {
      correctionRepository.save(
        DraftCorrection(
          hospitalId = session.hospitalId,
          sessionId = id,
          originalDraft = previous,
          editedDraft = draft,
          charsChanged = changedChars(previous, draft),
        ).also { it.createdBy = actorId },
      )
    }
    session.draft = draft
    if (session.stage in setOf("CREATED", "TRANSCRIBED", "SPEAKERS_RESOLVED")) {
      session.stage = "DRAFTED"
    }
    return repository.save(session)
  }

  @Transactional(readOnly = true)
  fun corrections(
    id: UUID,
    hospitalId: UUID?,
  ): List<DraftCorrection> {
    val session = find(id, hospitalId)
    return correctionRepository.findBySessionIdOrderByCreatedAtDesc(session.id!!)
  }

  // Characters between the shared prefix and shared suffix: a cheap, explainable edit distance.
  private fun changedChars(
    before: String,
    after: String,
  ): Int {
    var prefix = 0
    while (prefix < before.length && prefix < after.length && before[prefix] == after[prefix]) prefix++
    var suffix = 0
    while (suffix < before.length - prefix && suffix < after.length - prefix &&
      before[before.length - 1 - suffix] == after[after.length - 1 - suffix]
    ) {
      suffix++
    }
    return maxOf(before.length - prefix - suffix, after.length - prefix - suffix, 0)
  }

  @Transactional
  fun sign(id: UUID): ScribeSession {
    val session = find(id, null)
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
      "sessions" to list(null).size,
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
