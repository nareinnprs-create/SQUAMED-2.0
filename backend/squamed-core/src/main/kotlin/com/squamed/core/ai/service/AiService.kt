package com.squamed.core.ai.service

import com.squamed.core.ai.domain.AgentConfig
import com.squamed.core.ai.domain.InteractionLog
import com.squamed.core.ai.domain.ModelConfig
import com.squamed.core.ai.repository.AgentConfigRepository
import com.squamed.core.ai.repository.InteractionLogRepository
import com.squamed.core.ai.repository.ModelConfigRepository
import com.squamed.core.ai.repository.ToolConfigRepository
import com.squamed.core.identity.security.SquamedPermissionEvaluator
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.data.domain.PageRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

data class AiReply(
  val reply: String,
  val modelName: String,
  val providerId: String,
  val intent: String,
  val latencyMs: Long,
  val suggestions: List<String>,
  val logId: UUID?,
  val tokensUsed: Int? = null,
  val costUsd: BigDecimal = BigDecimal.ZERO,
  val redacted: Boolean = false,
)

data class AiCommandOutput(
  val intent: String,
  val target: String?,
  val detail: String?,
  val message: String,
  val suggestions: List<String>,
  // 05.03: grounded answers cite what they read; drafts await human approval.
  val citations: List<AiCitation> = emptyList(),
  val draft: ActionDraftView? = null,
)

@Service
class AiService(
  private val modelRepository: ModelConfigRepository,
  private val agentRepository: AgentConfigRepository,
  private val toolRepository: ToolConfigRepository,
  private val logRepository: InteractionLogRepository,
  private val router: AiModelRouter,
  private val intentService: AiIntentService,
  private val governanceService: AiGovernanceService,
  private val redactor: PromptRedactor,
  private val permissionEvaluator: SquamedPermissionEvaluator,
  private val actionService: AiActionService,
) {
  @Transactional(readOnly = true)
  fun models(): List<ModelConfig> = modelRepository.findAllByOrderByCreatedAtAsc()

  @Transactional
  fun createModel(
    name: String,
    provider: String,
    modelName: String?,
    baseUrl: String?,
    apiKey: String?,
    temperature: Double?,
    maxTokens: Int?,
    isDefault: Boolean,
    costPer1kTokens: BigDecimal? = null,
  ): ModelConfig {
    if (isDefault) {
      modelRepository.findAll().forEach { it.isDefault = false }
    }
    return modelRepository.save(
      ModelConfig(
        name = name,
        provider = provider,
        modelName = modelName,
        baseUrl = baseUrl,
        apiKey = apiKey,
        temperature = temperature ?: 0.2,
        maxTokens = maxTokens ?: 512,
        isDefault = isDefault,
        costPer1kTokens = costPer1kTokens ?: BigDecimal.ZERO,
      ),
    )
  }

  @Transactional
  fun deleteModel(id: UUID) {
    val model = findModel(id)
    val usedBy = agentRepository.findAll().count { it.modelId == id }
    if (usedBy > 0) throw IllegalArgumentException("AI_MODEL_IN_USE")
    modelRepository.delete(model)
  }

  @Transactional(readOnly = true)
  fun agents(): List<AgentConfig> = agentRepository.findAllByOrderByCreatedAtAsc()

  @Transactional
  fun createAgent(
    code: String,
    name: String,
    description: String?,
    systemPrompt: String?,
    modelId: UUID?,
    temperature: Double?,
    enabled: Boolean?,
    scopes: String? = null,
  ): AgentConfig {
    if (agentRepository.findByCodeIgnoreCase(code) != null) throw IllegalArgumentException("AI_AGENT_CODE_EXISTS")
    return agentRepository.save(
      AgentConfig(
        code = code,
        name = name,
        description = description,
        systemPrompt = systemPrompt,
        modelId = modelId,
        temperature = temperature ?: 0.2,
        enabled = enabled ?: true,
        scopes = scopes,
      ),
    )
  }

  @Transactional
  fun updateAgent(
    id: UUID,
    patch: Map<String, Any?>,
  ): AgentConfig {
    val agent = findAgent(id)
    patch["name"]?.let { agent.name = it.toString() }
    patch["description"]?.let { agent.description = it.toString() }
    patch["systemPrompt"]?.let { agent.systemPrompt = it.toString() }
    patch["modelId"]?.let { agent.modelId = UUID.fromString(it.toString()) }
    patch["temperature"]?.let { agent.temperature = (it as Number).toDouble() }
    patch["enabled"]?.let { agent.enabled = it as Boolean }
    patch["scopes"]?.let { agent.scopes = it.toString() }
    return agentRepository.save(agent)
  }

  @Transactional(readOnly = true)
  fun tools(): List<Map<String, Any?>> =
    toolRepository.findAllByOrderByToolCodeAsc().map {
      mapOf("toolCode" to it.toolCode, "name" to it.name, "description" to it.description, "enabled" to it.enabled)
    }

  @Transactional(readOnly = true)
  fun logs(hospitalId: UUID?): List<InteractionLog> = logRepository.findTop100ByOrderByCreatedAtDesc(hospitalId, PageRequest.of(0, 100))

  @Transactional
  fun chat(
    agentCode: String?,
    messages: List<AiChatMessage>,
    principal: SquamedPrincipal,
    hospitalId: UUID?,
  ): AiReply {
    val userMessage = messages.lastOrNull { it.role == "user" }?.content ?: ""
    return generate(agentCode, userMessage, principal, hospitalId)
  }

  @Transactional
  fun generate(
    agentCode: String?,
    userMessage: String,
    principal: SquamedPrincipal,
    hospitalId: UUID?,
  ): AiReply {
    val started = Instant.now()
    val hid = hospitalId ?: throw IllegalArgumentException("AI_HOSPITAL_REQUIRED")
    // 05.01: kill switch + hard daily cap are read fresh on every request.
    governanceService.assertAllowed(hid, "chat")
    val agent = resolveAgent(agentCode)
    // 05.07: an agent may only act with scopes its caller also holds.
    agent.scopes
      ?.split(",")
      ?.map { it.trim() }
      ?.filter { it.isNotEmpty() }
      ?.firstOrNull { !permissionEvaluator.check(principal, it) }
      ?.let { throw AccessDeniedException("AI_SCOPE_DENIED: $it") }
    val model = resolveModel(agent.modelId)
    val provider = router.providerFor(model.provider)
    // 05.01: direct identifiers never reach the provider; the redacted text is what we log too.
    val redaction = redactor.redact(userMessage)
    val intent = intentService.parse(redaction.text, hospitalId)
    requireScope(principal, intent)
    val completion =
      provider.complete(
        model = model,
        systemPrompt = agent.systemPrompt,
        messages = listOf(AiChatMessage("user", redaction.text)),
        intent = intent,
        userMessage = redaction.text,
        hospitalId = hospitalId,
      )
    val latency = Instant.now().toEpochMilli() - started.toEpochMilli()
    val tokens = completion.promptTokens + completion.completionTokens
    val cost =
      model.costPer1kTokens
        .multiply(BigDecimal.valueOf(tokens.toLong()))
        .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
    val log =
      logRepository.save(
        InteractionLog(
          hospitalId = hospitalId,
          agentId = agent.id,
          agentCode = agent.code,
          modelName = model.name,
          userId = principal.userId,
          userMessage = redaction.text,
          assistantMessage = completion.text,
          intent = intent.type.name.lowercase(),
          latencyMs = latency,
          tokensUsed = tokens,
          capability = "chat",
          promptVersion = "${agent.code}@${agent.version}",
          promptTokens = completion.promptTokens,
          completionTokens = completion.completionTokens,
          costUsd = cost,
          redacted = redaction.redacted,
        ),
      )
    return AiReply(
      reply = completion.text,
      modelName = model.name,
      providerId = provider.id(),
      intent = intent.type.name.lowercase(),
      latencyMs = latency,
      suggestions = intentService.suggestions(),
      logId = log.id,
      tokensUsed = tokens,
      costUsd = cost,
      redacted = redaction.redacted,
    )
  }

  @Transactional
  fun command(
    query: String,
    principal: SquamedPrincipal,
    hospitalId: UUID?,
  ): AiCommandOutput {
    val hid = hospitalId ?: throw IllegalArgumentException("AI_HOSPITAL_REQUIRED")
    governanceService.assertAllowed(hid, "command")
    val redaction = redactor.redact(query)
    val intent = intentService.parse(redaction.text, hospitalId)
    requireScope(principal, intent)
    val draft =
      when (intent.type) {
        AiIntentType.DRAFT_TASK -> {
          val title = intent.detail ?: throw IllegalArgumentException("AI_ACTION_TITLE_REQUIRED")
          actionService.propose(principal, hid, "create_task", mapOf("title" to title))
        }

        AiIntentType.DRAFT_DISCHARGE -> {
          val admissionId = intent.target ?: throw IllegalArgumentException("AI_ACTION_ADMISSION_REQUIRED")
          actionService.propose(principal, hid, "plan_discharge", mapOf("admissionId" to admissionId))
        }

        else -> {
          null
        }
      }
    // 05.03: record-backed answers cannot exist without citations.
    if (draft == null &&
      intent.type in setOf(AiIntentType.COUNT, AiIntentType.ANALYTICS, AiIntentType.SEARCH_PATIENTS, AiIntentType.WORKFLOW) &&
      intent.citations.isEmpty()
    ) {
      throw IllegalStateException("AI_UNGROUNDED_ANSWER")
    }
    val message =
      when {
        draft != null -> "Draft proposal: ${draft.summary}. Approve to run it through the platform API."
        intent.note != null && intent.detail == null -> intent.note!!
        else -> intentService.render(intent, redaction.text, hospitalId)
      }
    logRepository.save(
      InteractionLog(
        hospitalId = hospitalId,
        userId = principal.userId,
        userMessage = redaction.text,
        assistantMessage = message,
        intent = intent.type.name.lowercase(),
        latencyMs = 0,
        tokensUsed = 0,
        capability = "command",
        redacted = redaction.redacted,
      ),
    )
    return AiCommandOutput(
      intent = intent.type.name.lowercase(),
      target = intent.target,
      detail = (intent.detail ?: intent.note)?.let { it },
      message = message,
      suggestions = intentService.suggestions(),
      citations = intent.citations,
      draft = draft,
    )
  }

  // 05.01 effective-scope enforcement: the assistant may only touch what the caller can read.
  private fun requireScope(
    principal: SquamedPrincipal,
    intent: AiIntent,
  ) {
    val required =
      when (intent.type) {
        AiIntentType.SEARCH_PATIENTS -> {
          "patient:read:patient"
        }

        AiIntentType.ANALYTICS -> {
          "billing:read:invoice"
        }

        AiIntentType.WORKFLOW -> {
          "workflow:read:workflow"
        }

        AiIntentType.COUNT -> {
          val label = intent.entityLabel ?: ""
          when {
            label.startsWith("patients") -> "patient:read:patient"
            label.startsWith("encounters") -> "encounter:read:encounter"
            label.startsWith("appointments") -> "appointment:read:appointment"
            label.startsWith("queue") -> "queue:read:queue"
            label.startsWith("lab") -> "laboratory:read:lab"
            label.startsWith("invoices") -> "billing:read:invoice"
            label.startsWith("pending approvals") || label.startsWith("workflow") -> "workflow:read:workflow"
            label.startsWith("open tasks") -> "task:manage:task"
            else -> null
          }
        }

        else -> {
          null
        }
      }
    if (required != null && !permissionEvaluator.check(principal, required)) {
      throw AccessDeniedException("AI_SCOPE_DENIED: $required")
    }
  }

  private fun resolveAgent(agentCode: String?): AgentConfig {
    agentCode?.let { code ->
      agentRepository.findByCodeIgnoreCase(code)?.let { return it }
    }
    return agentRepository.findByCodeIgnoreCase("ops")
      ?: throw IllegalArgumentException("AI_AGENT_NOT_FOUND")
  }

  private fun resolveModel(agentModelId: UUID?): ModelConfig {
    agentModelId?.let { id ->
      modelRepository.findById(id).orElse(null)?.let { if (it.enabled) return it }
    }
    return modelRepository.findByEnabledAndIsDefaultTrue(true)
      ?: modelRepository.findAll().firstOrNull { it.enabled }
      ?: throw IllegalArgumentException("AI_MODEL_NOT_FOUND")
  }

  @Transactional(readOnly = true)
  fun findModel(id: UUID): ModelConfig = modelRepository.findById(id).orElseThrow { IllegalArgumentException("AI_MODEL_NOT_FOUND") }

  @Transactional(readOnly = true)
  fun findAgent(id: UUID): AgentConfig = agentRepository.findById(id).orElseThrow { IllegalArgumentException("AI_AGENT_NOT_FOUND") }
}
