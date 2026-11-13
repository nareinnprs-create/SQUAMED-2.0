package com.squamed.core.ai.service

import com.squamed.core.ai.domain.AgentConfig
import com.squamed.core.ai.domain.InteractionLog
import com.squamed.core.ai.domain.ModelConfig
import com.squamed.core.ai.repository.AgentConfigRepository
import com.squamed.core.ai.repository.InteractionLogRepository
import com.squamed.core.ai.repository.ModelConfigRepository
import com.squamed.core.ai.repository.ToolConfigRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
)

data class AiCommandOutput(
  val intent: String,
  val target: String?,
  val detail: String?,
  val message: String,
  val suggestions: List<String>,
)

@Service
class AiService(
  private val modelRepository: ModelConfigRepository,
  private val agentRepository: AgentConfigRepository,
  private val toolRepository: ToolConfigRepository,
  private val logRepository: InteractionLogRepository,
  private val router: AiModelRouter,
  private val intentService: AiIntentService,
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
    return agentRepository.save(agent)
  }

  @Transactional(readOnly = true)
  fun tools(): List<Map<String, Any?>> =
    toolRepository.findAllByOrderByToolCodeAsc().map {
      mapOf("toolCode" to it.toolCode, "name" to it.name, "description" to it.description, "enabled" to it.enabled)
    }

  @Transactional(readOnly = true)
  fun logs(): List<InteractionLog> = logRepository.findTop100ByOrderByCreatedAtDesc()

  @Transactional
  fun chat(
    agentCode: String?,
    messages: List<AiChatMessage>,
    userId: UUID?,
    hospitalId: UUID?,
  ): AiReply {
    val userMessage = messages.lastOrNull { it.role == "user" }?.content ?: ""
    val reply = generate(agentCode, userMessage, userId, hospitalId)
    return reply.copy(providerId = reply.providerId)
  }

  @Transactional
  fun generate(
    agentCode: String?,
    userMessage: String,
    userId: UUID?,
    hospitalId: UUID?,
  ): AiReply {
    val started = Instant.now()
    val agent = resolveAgent(agentCode)
    val model = resolveModel(agent.modelId)
    val provider = router.providerFor(model.provider)
    val intent = intentService.parse(userMessage, hospitalId)
    val replyText =
      provider.complete(
        model = model,
        systemPrompt = agent.systemPrompt,
        messages = listOf(AiChatMessage("user", userMessage)),
        intent = intent,
        userMessage = userMessage,
        hospitalId = hospitalId,
      )
    val latency = Instant.now().toEpochMilli() - started.toEpochMilli()
    val log =
      logRepository.save(
        InteractionLog(
          hospitalId = hospitalId,
          agentId = agent.id,
          agentCode = agent.code,
          modelName = model.name,
          userId = userId,
          userMessage = userMessage,
          assistantMessage = replyText,
          intent = intent.type.name.lowercase(),
          latencyMs = latency,
          tokensUsed = null,
        ),
      )
    return AiReply(
      reply = replyText,
      modelName = model.name,
      providerId = provider.id(),
      intent = intent.type.name.lowercase(),
      latencyMs = latency,
      suggestions = intentService.suggestions(),
      logId = log.id,
    )
  }

  @Transactional
  fun command(
    query: String,
    userId: UUID?,
    hospitalId: UUID?,
  ): AiCommandOutput {
    val intent = intentService.parse(query, hospitalId)
    val page = if (intent.type == AiIntentType.NAVIGATE) intentService.pageSlug(intent.target) else null
    return AiCommandOutput(
      intent = intent.type.name.lowercase(),
      target = intent.target,
      detail = (intent.detail ?: intent.note)?.let { it },
      message = intentService.render(intent, query, hospitalId),
      suggestions = intentService.suggestions(),
    )
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
