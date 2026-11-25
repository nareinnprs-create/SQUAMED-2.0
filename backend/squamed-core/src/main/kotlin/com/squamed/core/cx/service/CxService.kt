package com.squamed.core.cx.service

import com.squamed.core.cx.domain.ContactInteraction
import com.squamed.core.cx.domain.IntlCase
import com.squamed.core.cx.domain.PatientFeedback
import com.squamed.core.cx.repository.ContactInteractionRepository
import com.squamed.core.cx.repository.IntlCaseRepository
import com.squamed.core.cx.repository.PatientFeedbackRepository
import com.squamed.core.task.api.TaskCreateDto
import com.squamed.core.task.service.TaskService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

// 07.07 contact centre + international desk + patient experience. Agent-assist is a deterministic,
// template-governed suggested reply; a human still sends it.
@Service
class CxService(
  private val interactionRepository: ContactInteractionRepository,
  private val intlCaseRepository: IntlCaseRepository,
  private val feedbackRepository: PatientFeedbackRepository,
  private val taskService: TaskService,
) {
  companion object {
    val CHANNELS = setOf("PHONE", "CHAT", "EMAIL", "WALK_IN")
    val DIRECTIONS = setOf("INBOUND", "OUTBOUND")
  }

  @Transactional
  fun logInteraction(
    hospitalId: UUID,
    patientId: UUID?,
    channel: String,
    direction: String,
    subject: String,
    agentId: UUID?,
    actorId: UUID?,
  ): ContactInteraction {
    val ch = channel.trim().uppercase()
    val dir = direction.trim().uppercase()
    if (ch !in CHANNELS) throw IllegalArgumentException("CX_CHANNEL_INVALID")
    if (dir !in DIRECTIONS) throw IllegalArgumentException("CX_DIRECTION_INVALID")
    if (subject.isBlank()) throw IllegalArgumentException("CX_SUBJECT_REQUIRED")
    return interactionRepository.save(
      ContactInteraction(
        hospitalId = hospitalId,
        patientId = patientId,
        channel = ch,
        direction = dir,
        subject = subject.trim(),
        agentId = agentId,
        aiSuggestedReply = suggestedReply(subject),
      ).also { it.createdBy = actorId },
    )
  }

  @Transactional
  fun closeInteraction(
    id: UUID,
    hospitalId: UUID,
    resolution: String,
    actorId: UUID?,
  ): ContactInteraction {
    if (resolution.isBlank()) throw IllegalArgumentException("CX_RESOLUTION_REQUIRED")
    val interaction = interactionRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("CX_INTERACTION_NOT_FOUND")
    if (interaction.status == "CLOSED") throw IllegalStateException("CX_INTERACTION_ALREADY_CLOSED")
    interaction.status = "CLOSED"
    interaction.resolution = resolution.trim()
    interaction.closedAt = Instant.now()
    interaction.updatedBy = actorId
    return interactionRepository.save(interaction)
  }

  @Transactional(readOnly = true)
  fun interactions(hospitalId: UUID): List<ContactInteraction> = interactionRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId)

  @Transactional
  fun openIntlCase(
    hospitalId: UUID,
    patientId: UUID,
    country: String,
    language: String,
    interpreterNeeded: Boolean,
    coordinatorId: UUID?,
    notes: String?,
    actorId: UUID?,
  ): IntlCase {
    if (country.isBlank() || language.isBlank()) throw IllegalArgumentException("CX_INTL_FIELDS_REQUIRED")
    return intlCaseRepository.save(
      IntlCase(
        hospitalId = hospitalId,
        patientId = patientId,
        country = country.trim(),
        language = language.trim(),
        interpreterNeeded = interpreterNeeded,
        coordinatorId = coordinatorId,
        notes = notes?.trim(),
      ).also { it.createdBy = actorId },
    )
  }

  @Transactional
  fun closeIntlCase(
    id: UUID,
    hospitalId: UUID,
    actorId: UUID?,
  ): IntlCase {
    val case = intlCaseRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("CX_INTL_NOT_FOUND")
    if (case.status == "CLOSED") throw IllegalStateException("CX_INTL_ALREADY_CLOSED")
    case.status = "CLOSED"
    case.closedAt = Instant.now()
    case.updatedBy = actorId
    return intlCaseRepository.save(case)
  }

  @Transactional(readOnly = true)
  fun intlCases(hospitalId: UUID): List<IntlCase> = intlCaseRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId)

  @Transactional
  fun recordFeedback(
    hospitalId: UUID,
    patientId: UUID,
    score: Int,
    category: String,
    comment: String?,
    actorId: UUID?,
  ): PatientFeedback {
    if (score !in 0..10) throw IllegalArgumentException("CX_FEEDBACK_SCORE_INVALID")
    if (category.isBlank()) throw IllegalArgumentException("CX_FEEDBACK_CATEGORY_REQUIRED")
    val saved =
      feedbackRepository.save(
        PatientFeedback(
          hospitalId = hospitalId,
          patientId = patientId,
          score = score,
          category = category.trim(),
          comment = comment?.trim(),
        ).also { it.createdBy = actorId },
      )
    if (score <= 6) {
      val task =
        taskService.createTask(
          TaskCreateDto(
            hospitalId = hospitalId,
            title = "Patient feedback follow-up: score $score (${saved.category})",
            description = saved.comment,
            assignerId = actorId,
            priority = "high",
            relatedType = "PATIENT_FEEDBACK",
            relatedId = saved.id,
          ),
          checkNotNull(actorId),
        )
      saved.followupTaskId = task.id
      feedbackRepository.save(saved)
    }
    return saved
  }

  @Transactional
  fun acknowledgeFeedback(
    id: UUID,
    hospitalId: UUID,
    actorId: UUID?,
  ): PatientFeedback {
    val feedback = findFeedback(id, hospitalId)
    if (feedback.status != "NEW") throw IllegalStateException("CX_FEEDBACK_NOT_NEW")
    feedback.status = "ACKNOWLEDGED"
    feedback.updatedBy = actorId
    return feedbackRepository.save(feedback)
  }

  @Transactional
  fun resolveFeedback(
    id: UUID,
    hospitalId: UUID,
    resolution: String,
    actorId: UUID?,
  ): PatientFeedback {
    if (resolution.isBlank()) throw IllegalArgumentException("CX_RESOLUTION_REQUIRED")
    val feedback = findFeedback(id, hospitalId)
    if (feedback.status == "RESOLVED") throw IllegalStateException("CX_FEEDBACK_ALREADY_RESOLVED")
    feedback.status = "RESOLVED"
    feedback.resolution = resolution.trim()
    feedback.resolvedAt = Instant.now()
    feedback.updatedBy = actorId
    return feedbackRepository.save(feedback)
  }

  @Transactional(readOnly = true)
  fun feedback(hospitalId: UUID): List<PatientFeedback> = feedbackRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId)

  @Transactional(readOnly = true)
  fun feedbackSummary(hospitalId: UUID): Map<String, Any?> {
    val rows = feedbackRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId)
    val total = rows.size
    val promoters = rows.count { it.score >= 9 }
    val passives = rows.count { it.score in 7..8 }
    val detractors = rows.count { it.score <= 6 }
    val nps =
      if (total == 0) {
        BigDecimal.ZERO
      } else {
        BigDecimal
          .valueOf((promoters - detractors).toLong() * 100)
          .divide(BigDecimal.valueOf(total.toLong()), 2, RoundingMode.HALF_UP)
      }
    return mapOf(
      "total" to total,
      "promoters" to promoters,
      "passives" to passives,
      "detractors" to detractors,
      "nps" to nps,
    )
  }

  private fun suggestedReply(subject: String): String {
    val s = subject.lowercase()
    return when {
      listOf("bill", "invoice", "payment", "refund").any { s.contains(it) } -> {
        "I can see your billing query. I will check the invoice and confirm the amount due; no payment is taken without your approval."
      }

      listOf("appointment", "book", "reschedule", "slot").any { s.contains(it) } -> {
        "I can help with your appointment. I will check the next available slots and confirm before booking."
      }

      listOf("report", "result", "scan").any { s.contains(it) } -> {
        "I will check whether your report is released and share the collection options."
      }

      listOf("complaint", "rude", "wait", "delay").any { s.contains(it) } -> {
        "I am sorry about the experience. I have logged your concern and a patient-experience officer will follow up."
      }

      else -> {
        "Thank you for contacting us. I have logged your query and will follow up shortly."
      }
    }
  }

  private fun findFeedback(
    id: UUID,
    hospitalId: UUID,
  ): PatientFeedback = feedbackRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("CX_FEEDBACK_NOT_FOUND")
}
