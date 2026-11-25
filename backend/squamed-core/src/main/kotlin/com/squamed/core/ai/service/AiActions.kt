package com.squamed.core.ai.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.ai.domain.ActionDraft
import com.squamed.core.ai.repository.ActionDraftRepository
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.identity.security.SquamedPermissionEvaluator
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.ipd.api.PlanDischargeDto
import com.squamed.core.ipd.service.AdmissionService
import com.squamed.core.task.api.TaskCreateDto
import com.squamed.core.task.service.TaskService
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class ActionDraftView(
  val id: UUID?,
  val actionType: String,
  val summary: String,
  val payload: JsonNode,
  val status: String,
  val expiresAt: Instant,
  val decidedAt: Instant?,
  val decidedBy: UUID?,
  val resultRef: String?,
) {
  companion object {
    fun from(entity: ActionDraft) =
      ActionDraftView(
        entity.id,
        entity.actionType,
        entity.summary,
        entity.payload,
        entity.status,
        entity.expiresAt,
        entity.decidedAt,
        entity.decidedBy,
        entity.resultRef,
      )
  }
}

// 05.03: agents/commands only propose; approval executes through the governed platform services
// with the approver's own scopes.
@Service
class AiActionService(
  private val draftRepository: ActionDraftRepository,
  private val agentRepository: com.squamed.core.ai.repository.AgentConfigRepository,
  private val taskService: TaskService,
  private val admissionService: AdmissionService,
  private val permissionEvaluator: SquamedPermissionEvaluator,
  private val eventPublisher: DomainEventPublisher,
  private val objectMapper: ObjectMapper,
) {
  companion object {
    val ACTION_TYPES = setOf("create_task", "plan_discharge")
    private val DRAFT_TTL: Duration = Duration.ofHours(24)
  }

  @Transactional
  fun propose(
    principal: SquamedPrincipal,
    hospitalId: UUID,
    actionType: String,
    payload: Map<String, Any?>,
  ): ActionDraftView {
    val type = actionType.trim().lowercase()
    if (type !in ACTION_TYPES) throw IllegalArgumentException("AI_ACTION_TYPE_INVALID")
    val node = objectMapper.valueToTree<JsonNode>(payload)
    val summary =
      when (type) {
        "create_task" -> {
          val title = payload["title"]?.toString()?.trim()
          if (title.isNullOrBlank()) throw IllegalArgumentException("AI_ACTION_TITLE_REQUIRED")
          "Create task: $title"
        }

        else -> {
          val admissionId = payload["admissionId"]?.toString()?.trim()
          if (admissionId.isNullOrBlank()) throw IllegalArgumentException("AI_ACTION_ADMISSION_REQUIRED")
          "Plan discharge for admission $admissionId"
        }
      }
    val draft =
      draftRepository.save(
        ActionDraft(
          hospitalId = hospitalId,
          proposedBy = principal.userId,
          actionType = type,
          summary = summary,
          payload = node,
          expiresAt = Instant.now().plus(DRAFT_TTL),
        ).also { it.createdBy = principal.userId },
      )
    publish(draft, "ai.action_proposed", principal.userId, mapOf("actionType" to type))
    return ActionDraftView.from(draft)
  }

  @Transactional
  fun approve(
    id: UUID,
    principal: SquamedPrincipal,
    hospitalId: UUID,
  ): ActionDraftView {
    val draft = find(id, hospitalId)
    if (draft.status != "PROPOSED") throw IllegalStateException("AI_ACTION_NOT_PENDING")
    if (Instant.now().isAfter(draft.expiresAt)) {
      draft.status = "EXPIRED"
      draftRepository.save(draft)
      publish(draft, "ai.action_expired", principal.userId, emptyMap())
      throw IllegalStateException("AI_ACTION_EXPIRED")
    }
    val required = if (draft.actionType == "create_task") "task:manage:task" else "ipd:write:admission"
    if (!permissionEvaluator.check(principal, required)) throw AccessDeniedException("AI_SCOPE_DENIED: $required")
    draft.resultRef =
      when (draft.actionType) {
        "create_task" -> {
          val task =
            taskService.createTask(
              TaskCreateDto(
                hospitalId = draft.hospitalId,
                title = draft.payload.path("title").asText(),
                description =
                  draft.payload
                    .path("description")
                    .takeIf { !it.isMissingNode }
                    ?.asText(),
                assigneeId =
                  draft.payload
                    .path("assigneeId")
                    .takeIf { it.isTextual }
                    ?.asText()
                    ?.let(UUID::fromString),
                assignerId = principal.userId,
                priority =
                  draft.payload
                    .path("priority")
                    .takeIf { it.isTextual }
                    ?.asText(),
                relatedType =
                  draft.payload
                    .path("relatedType")
                    .takeIf { it.isTextual }
                    ?.asText(),
                relatedId =
                  draft.payload
                    .path("relatedId")
                    .takeIf { it.isTextual }
                    ?.asText()
                    ?.let(UUID::fromString),
              ),
              principal.userId,
            )
          task.id?.toString()
        }

        else -> {
          val admissionId = UUID.fromString(draft.payload.path("admissionId").asText())
          val expected =
            draft.payload
              .path("expectedDischargeAt")
              .takeIf { it.isTextual }
              ?.asText()
              ?.let(Instant::parse)
              ?: Instant.now().plus(Duration.ofDays(1))
          admissionService.planDischarge(admissionId, PlanDischargeDto(expected), principal.userId)
          admissionId.toString()
        }
      }
    draft.status = "APPROVED"
    draft.decidedAt = Instant.now()
    draft.decidedBy = principal.userId
    draftRepository.save(draft)
    publish(draft, "ai.action_approved", principal.userId, mapOf("resultRef" to draft.resultRef))
    return ActionDraftView.from(draft)
  }

  @Transactional
  fun reject(
    id: UUID,
    principal: SquamedPrincipal,
    hospitalId: UUID,
    reason: String?,
  ): ActionDraftView {
    val draft = find(id, hospitalId)
    if (draft.status != "PROPOSED") throw IllegalStateException("AI_ACTION_NOT_PENDING")
    draft.status = "REJECTED"
    draft.decidedAt = Instant.now()
    draft.decidedBy = principal.userId
    draft.decisionReason = reason?.trim()?.take(400)
    draftRepository.save(draft)
    publish(draft, "ai.action_rejected", principal.userId, mapOf("reason" to draft.decisionReason))
    return ActionDraftView.from(draft)
  }

  @Transactional(readOnly = true)
  fun list(
    hospitalId: UUID,
    status: String?,
  ): List<ActionDraftView> {
    val rows =
      if (status.isNullOrBlank()) {
        draftRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId)
      } else {
        draftRepository.findByHospitalIdAndStatusOrderByCreatedAtDesc(hospitalId, status.uppercase())
      }
    return rows.map(ActionDraftView::from)
  }

  // 05.06: an agent persona may only propose what its role allows; the human approval still runs
  // through the governed platform API with the approver's own scopes.
  @Transactional
  fun proposeAsAgent(
    agentCode: String,
    principal: SquamedPrincipal,
    hospitalId: UUID,
    actionType: String,
    payload: Map<String, Any?>,
  ): ActionDraftView {
    val agent = agentRepository.findByCodeIgnoreCase(agentCode) ?: throw IllegalArgumentException("AI_AGENT_NOT_FOUND")
    if (!agent.enabled) throw IllegalStateException("AI_AGENT_DISABLED")
    agent.scopes
      ?.split(",")
      ?.map { it.trim() }
      ?.filter { it.isNotEmpty() }
      ?.firstOrNull { !permissionEvaluator.check(principal, it) }
      ?.let { throw AccessDeniedException("AI_SCOPE_DENIED: $it") }
    val type = actionType.trim().lowercase()
    val relatedType = payload["relatedType"]?.toString()
    if (!AgentPersonas.isAllowed(agent.code, type, relatedType)) throw IllegalArgumentException("AI_AGENT_ACTION_NOT_ALLOWED")
    return propose(principal, hospitalId, type, payload + mapOf("agentCode" to agent.code))
  }

  private fun find(
    id: UUID,
    hospitalId: UUID,
  ): ActionDraft = draftRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("AI_ACTION_NOT_FOUND")

  private fun publish(
    draft: ActionDraft,
    eventType: String,
    actorId: UUID,
    payload: Map<String, Any?>,
  ) {
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = eventType,
        occurredAt = Instant.now(),
        source = "ai-service",
        entityType = "ai_action_draft",
        entityId = draft.id,
        hospitalId = draft.hospitalId,
        payload = payload + mapOf("actionType" to draft.actionType, "status" to draft.status),
        actorType = "user",
        actorId = actorId,
      ),
    )
  }
}
