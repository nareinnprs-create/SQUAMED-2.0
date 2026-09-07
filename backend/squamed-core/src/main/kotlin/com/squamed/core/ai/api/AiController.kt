package com.squamed.core.ai.api

import com.squamed.core.ai.service.AiCommandOutput
import com.squamed.core.ai.service.AiReply
import com.squamed.core.ai.service.AiService
import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class AiChatMessageDto(
  val role: String,
  val content: String,
)

data class AiChatRequest(
  val agentCode: String? = null,
  val messages: List<AiChatMessageDto>,
)

data class AiCommandRequest(
  val query: String,
  val agentCode: String? = null,
)

data class AiModelCreateRequest(
  val name: String,
  val provider: String,
  val modelName: String? = null,
  val baseUrl: String? = null,
  val apiKey: String? = null,
  val temperature: Double? = null,
  val maxTokens: Int? = null,
  val isDefault: Boolean = false,
)

data class AiAgentCreateRequest(
  val code: String,
  val name: String,
  val description: String? = null,
  val systemPrompt: String? = null,
  val modelId: UUID? = null,
  val temperature: Double? = null,
  val enabled: Boolean? = null,
)

@RestController
@RequestMapping("/api/v1/ai")
class AiController(
  private val aiService: AiService,
) {
  @GetMapping("/models")
  @RequiresPermission("ai:read:agent")
  fun models(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(aiService.models().map { it.toMap() })

  @PostMapping("/models")
  @RequiresPermission("ai:manage:agent")
  fun createModel(
    @RequestBody req: AiModelCreateRequest,
  ): ApiResponse<Map<String, Any?>> {
    val model =
      aiService.createModel(
        req.name,
        req.provider,
        req.modelName,
        req.baseUrl,
        req.apiKey,
        req.temperature,
        req.maxTokens,
        req.isDefault,
      )
    return ApiResponse.ok(mapOf("id" to model.id, "name" to model.name, "provider" to model.provider))
  }

  @DeleteMapping("/models/{id}")
  @RequiresPermission("ai:manage:agent")
  fun deleteModel(
    @PathVariable id: UUID,
  ): ApiResponse<Map<String, Any?>> {
    aiService.deleteModel(id)
    return ApiResponse.ok(mapOf("deleted" to true))
  }

  @GetMapping("/agents")
  @RequiresPermission("ai:read:agent")
  fun agents(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(aiService.agents().map { it.toMap() })

  @PostMapping("/agents")
  @RequiresPermission("ai:manage:agent")
  fun createAgent(
    @RequestBody req: AiAgentCreateRequest,
  ): ApiResponse<Map<String, Any?>> {
    val agent =
      aiService.createAgent(
        req.code,
        req.name,
        req.description,
        req.systemPrompt,
        req.modelId,
        req.temperature,
        req.enabled,
      )
    return ApiResponse.ok(mapOf("id" to agent.id, "code" to agent.code, "name" to agent.name))
  }

  @PatchMapping("/agents/{id}")
  @RequiresPermission("ai:manage:agent")
  fun updateAgent(
    @PathVariable id: UUID,
    @RequestBody patch: Map<String, Any?>,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(aiService.updateAgent(id, patch).toMap())

  @GetMapping("/tools")
  @RequiresPermission("ai:read:agent")
  fun tools(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(aiService.tools())

  @PostMapping("/chat")
  @RequiresPermission("ai:chat:agent")
  fun chat(
    @RequestBody req: AiChatRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AiReply> =
    ApiResponse.ok(
      aiService.chat(
        req.agentCode,
        req.messages.map {
          com.squamed.core.ai.service
            .AiChatMessage(it.role, it.content)
        },
        principal.userId,
        principal.hospitalId,
      ),
    )

  @PostMapping("/command")
  @RequiresPermission("ai:chat:agent")
  fun command(
    @RequestBody req: AiCommandRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AiCommandOutput> = ApiResponse.ok(aiService.command(req.query, principal.userId, principal.hospitalId))

  @PostMapping("/chat/stream")
  @RequiresPermission("ai:chat:agent")
  fun chatStream(
    @RequestBody req: AiChatRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
    response: HttpServletResponse,
  ) {
    val userMessage = req.messages.lastOrNull { it.role == "user" }?.content ?: ""
    response.contentType = "text/event-stream; charset=UTF-8"
    response.setHeader("Cache-Control", "no-cache")
    response.setHeader("X-Accel-Buffering", "no")
    try {
      val reply = aiService.generate(req.agentCode, userMessage, principal.userId, principal.hospitalId)
      val chunks = reply.reply.split(Regex("(?<=\\s)|(?=\\s)")).filter { it.isNotBlank() }
      val writer = response.writer
      chunks.forEach { chunk ->
        writer.write("data: {\"type\":\"token\",\"text\":${jsonEscape(chunk)}}\n\n")
        writer.flush()
        try {
          Thread.sleep(18)
        } catch (_: InterruptedException) {
          Thread.currentThread().interrupt()
        }
      }
      writer.write(
        "data: {\"type\":\"done\",\"model\":${jsonEscape(
          reply.modelName,
        )},\"intent\":${jsonEscape(
          reply.intent,
        )},\"latencyMs\":${reply.latencyMs},\"logId\":${jsonEscape(reply.logId.toString())}}\n\n",
      )
      writer.flush()
    } catch (e: Exception) {
      response.writer.write("data: {\"type\":\"error\",\"message\":${jsonEscape(e.message ?: "AI_ERROR")}}\n\n")
      response.writer.flush()
    }
  }

  @GetMapping("/logs")
  @RequiresPermission("ai:read:agent")
  fun logs(): ApiResponse<List<Map<String, Any?>>> =
    ApiResponse.ok(
      aiService.logs().map { log ->
        mapOf(
          "id" to log.id,
          "agentCode" to log.agentCode,
          "modelName" to log.modelName,
          "userId" to log.userId,
          "userMessage" to log.userMessage,
          "assistantMessage" to log.assistantMessage,
          "intent" to log.intent,
          "latencyMs" to log.latencyMs,
          "createdAt" to log.createdAt,
        )
      },
    )

  private fun jsonEscape(s: String): String =
    s
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "")
}

private fun com.squamed.core.ai.domain.ModelConfig.toMap(): Map<String, Any?> =
  mapOf(
    "id" to id,
    "name" to name,
    "provider" to provider,
    "modelName" to modelName,
    "baseUrl" to baseUrl,
    "hasApiKey" to (apiKey != null),
    "temperature" to temperature,
    "maxTokens" to maxTokens,
    "enabled" to enabled,
    "isDefault" to isDefault,
    "createdAt" to createdAt,
  )

private fun com.squamed.core.ai.domain.AgentConfig.toMap(): Map<String, Any?> =
  mapOf(
    "id" to id,
    "code" to code,
    "name" to name,
    "description" to description,
    "systemPrompt" to systemPrompt,
    "modelId" to modelId,
    "temperature" to temperature,
    "enabled" to enabled,
    "createdAt" to createdAt,
  )
