package com.squamed.core.ai.service

import com.squamed.core.ai.domain.VoiceCommand
import com.squamed.core.ai.repository.VoiceCommandRepository
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

data class VoiceCommandView(
  val id: UUID?,
  val transcript: String,
  val locale: String,
  val screen: String?,
  val intent: String,
  val confidence: BigDecimal,
  val riskClass: String,
  val status: String,
  val voiceApprovalAllowed: Boolean,
  val response: String?,
  val target: String?,
  val searchTerm: String?,
  val escalatedReason: String?,
  val actionDraftId: UUID?,
) {
  companion object {
    fun from(entity: VoiceCommand) =
      VoiceCommandView(
        entity.id,
        entity.transcript,
        entity.locale,
        entity.screen,
        entity.intent,
        entity.confidence,
        entity.riskClass,
        entity.status,
        entity.voiceApprovalAllowed,
        entity.response,
        entity.target,
        entity.searchTerm,
        entity.escalatedReason,
        entity.actionDraftId,
      )
  }
}

// 07.02: voice commands are parsed deterministically and gated by risk class. Low-risk navigation,
// search and task drafts may be approved by voice; clinical orders, discharge and financial actions
// are hard-excluded and require the approval UI (§8 rule 4).
@Service
class AiVoiceService(
  private val commandRepository: VoiceCommandRepository,
  private val actionService: AiActionService,
) {
  companion object {
    private val HIGH_RISK =
      listOf(
        "order",
        "prescribe",
        "medication",
        "medicine",
        "drug",
        "dawa",
        "दवा",
        "discharge",
        "chutti",
        "छुट्टी",
        "payment",
        "refund",
        "invoice",
        "collect cash",
        "discount",
      )

    private val PAGES =
      linkedMapOf(
        "laboratory" to listOf("laboratory", "lab", "प्रयोगशाला"),
        "billing" to listOf("billing", "invoice screen", "बिल"),
        "patients" to listOf("patients", "patient list", "मरीज़"),
        "appointments" to listOf("appointments", "schedule", "अपॉइंटमेंट"),
        "queue" to listOf("queue", "token", "कतार"),
        "pharmacy" to listOf("pharmacy", "फार्मेसी"),
        "emergency" to listOf("emergency", "er", "आपातकाल"),
        "ipd" to listOf("ward", "ipd", "admissions", "वार्ड"),
      )

    private val OPEN_WORDS = listOf("open", "go to", "show", "kholo", "खोलो")
    private val SEARCH_WORDS = listOf("search", "find", "khojo", "खोजो")
    private val TASK_WORDS = listOf("create task", "new task", "remind", "task banao", "काम")
    private val HELP_WORDS = listOf("help", "what can you do", "madad", "मदद")
  }

  @Transactional
  fun command(
    principal: SquamedPrincipal,
    hospitalId: UUID,
    transcript: String,
    locale: String,
    screen: String?,
    patientId: UUID?,
  ): VoiceCommandView {
    if (transcript.isBlank()) throw IllegalArgumentException("VOICE_TRANSCRIPT_REQUIRED")
    val text = transcript.trim()
    val normalized = text.lowercase()
    val row =
      VoiceCommand(
        hospitalId = hospitalId,
        userId = principal.userId,
        transcript = text.take(600),
        locale =
          locale
            .trim()
            .lowercase()
            .ifBlank { "en" }
            .take(10),
        screen = screen?.trim()?.lowercase()?.take(80),
        patientId = patientId,
        intent = "unrecognised",
      ).also { it.createdBy = principal.userId }

    when {
      HIGH_RISK.any { normalized.contains(it) } -> {
        row.intent = "high_risk_action"
        row.confidence = BigDecimal("0.95")
        row.riskClass = "HIGH"
        row.status = "PENDING_APPROVAL"
        row.voiceApprovalAllowed = false
        row.response = "This action is hard-excluded from voice approval. Use the approval UI."
        row.escalatedReason = "high-risk category"
      }

      HELP_WORDS.any { normalized.contains(it) } -> {
        row.intent = "help"
        row.confidence = BigDecimal("0.99")
        row.riskClass = "LOW"
        row.status = "EXECUTED"
        row.voiceApprovalAllowed = true
        row.response = "I can open screens, search patients and draft tasks. Clinical and financial actions need the approval UI."
      }

      OPEN_WORDS.any { normalized.contains(it) } -> {
        val target = PAGES.entries.firstOrNull { (_, words) -> words.any { normalized.contains(it) } }?.key
        if (target == null) {
          escalate(row, "no screen matched")
        } else {
          row.intent = "navigate"
          row.target = target
          row.confidence = BigDecimal("0.95")
          row.riskClass = "LOW"
          row.status = "EXECUTED"
          row.voiceApprovalAllowed = true
          row.response =
            if (row.screen == target) "You are already on $target." else "Opening $target."
        }
      }

      SEARCH_WORDS.any { normalized.contains(it) } -> {
        val term = stripAny(normalized, SEARCH_WORDS)
        if (term.isBlank()) {
          escalate(row, "search term missing")
        } else {
          row.intent = "search"
          row.searchTerm = term.take(200)
          row.confidence = BigDecimal("0.92")
          row.riskClass = "LOW"
          row.status = "EXECUTED"
          row.voiceApprovalAllowed = true
          row.response = "Searching for $term."
        }
      }

      TASK_WORDS.any { normalized.contains(it) } -> {
        val title = stripAny(text, TASK_WORDS)
        if (title.isBlank()) {
          escalate(row, "task title missing")
        } else {
          val draft =
            actionService.propose(
              principal,
              hospitalId,
              "create_task",
              mapOf("title" to title.take(200), "description" to "Created by voice command", "relatedType" to "VOICE"),
            )
          row.intent = "create_task"
          row.confidence = BigDecimal("0.92")
          row.riskClass = "LOW"
          row.status = "PENDING_APPROVAL"
          row.voiceApprovalAllowed = true
          row.response = "Task draft ready. Say approve to create it."
          row.actionDraftId = draft.id
        }
      }

      else -> {
        escalate(row, "intent not recognised")
      }
    }
    return VoiceCommandView.from(commandRepository.save(row))
  }

  @Transactional
  fun approveByVoice(
    id: UUID,
    principal: SquamedPrincipal,
    hospitalId: UUID,
  ): VoiceCommandView {
    val command = find(id, hospitalId)
    if (command.status != "PENDING_APPROVAL") throw IllegalStateException("VOICE_COMMAND_NOT_PENDING")
    if (!command.voiceApprovalAllowed) throw AccessDeniedException("VOICE_HIGH_RISK_APPROVAL_FORBIDDEN")
    val draftId = command.actionDraftId ?: throw IllegalArgumentException("VOICE_ACTION_MISSING")
    val result = actionService.approve(draftId, principal, hospitalId)
    command.status = "EXECUTED"
    command.response = "Approved and executed (${result.resultRef ?: "done"})."
    command.updatedBy = principal.userId
    return VoiceCommandView.from(commandRepository.save(command))
  }

  @Transactional
  fun reject(
    id: UUID,
    principal: SquamedPrincipal,
    hospitalId: UUID,
    reason: String?,
  ): VoiceCommandView {
    val command = find(id, hospitalId)
    if (command.status != "PENDING_APPROVAL") throw IllegalStateException("VOICE_COMMAND_NOT_PENDING")
    command.actionDraftId?.let { actionService.reject(it, principal, hospitalId, reason) }
    command.status = "REJECTED"
    command.response = "Rejected."
    command.escalatedReason = reason?.trim()?.take(300)
    command.updatedBy = principal.userId
    return VoiceCommandView.from(commandRepository.save(command))
  }

  @Transactional(readOnly = true)
  fun list(hospitalId: UUID): List<VoiceCommandView> = commandRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId).map(VoiceCommandView::from)

  private fun escalate(
    row: VoiceCommand,
    reason: String,
  ) {
    row.intent = "unrecognised"
    row.confidence = BigDecimal("0.40")
    row.riskClass = "UNKNOWN"
    row.status = "ESCALATED"
    row.voiceApprovalAllowed = false
    row.response = "I did not catch that. Escalating to a human."
    row.escalatedReason = reason
  }

  private fun stripAny(
    text: String,
    words: List<String>,
  ): String {
    var result = text
    words.forEach { result = result.replace(it, " ", ignoreCase = true) }
    return result.replace(Regex("\\s+"), " ").trim().trim('.', ',', '!', '?')
  }

  private fun find(
    id: UUID,
    hospitalId: UUID,
  ): VoiceCommand = commandRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("VOICE_COMMAND_NOT_FOUND")
}
