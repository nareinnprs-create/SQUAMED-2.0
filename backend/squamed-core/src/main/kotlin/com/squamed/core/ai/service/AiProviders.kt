package com.squamed.core.ai.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.ai.domain.ModelConfig
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

data class AiChatMessage(
  val role: String,
  val content: String,
)

data class AiCompletion(
  val text: String,
  val promptTokens: Int,
  val completionTokens: Int,
)

fun estimateTokens(text: String): Int =
  kotlin.math
    .ceil(text.length / 4.0)
    .toInt()
    .coerceAtLeast(1)

interface AiProvider {
  fun id(): String

  fun name(): String

  fun supports(providerCode: String): Boolean

  fun complete(
    model: ModelConfig,
    systemPrompt: String?,
    messages: List<AiChatMessage>,
    intent: AiIntent,
    userMessage: String,
    hospitalId: UUID?,
  ): AiCompletion
}

@Component
class EchoAiProvider(
  private val intentService: AiIntentService,
) : AiProvider {
  override fun id() = "echo"

  override fun name() = "SQUAMED Local Assistant (offline rule-based)"

  override fun supports(providerCode: String) = providerCode.equals("ECHO", ignoreCase = true)

  override fun complete(
    model: ModelConfig,
    systemPrompt: String?,
    messages: List<AiChatMessage>,
    intent: AiIntent,
    userMessage: String,
    hospitalId: UUID?,
  ): AiCompletion {
    val text = intentService.render(intent, userMessage, hospitalId)
    return AiCompletion(
      text = text,
      promptTokens = estimateTokens((systemPrompt ?: "") + messages.joinToString(" ") { it.content }),
      completionTokens = estimateTokens(text),
    )
  }
}

@Component
class OpenAiCompatibleProvider(
  private val objectMapper: ObjectMapper,
) : AiProvider {
  override fun id() = "openai"

  override fun name() = "OpenAI-compatible Chat Completions"

  override fun supports(providerCode: String) = providerCode.equals("OPENAI", ignoreCase = true) || providerCode.equals("LOCAL", ignoreCase = true)

  override fun complete(
    model: ModelConfig,
    systemPrompt: String?,
    messages: List<AiChatMessage>,
    intent: AiIntent,
    userMessage: String,
    hospitalId: UUID?,
  ): AiCompletion {
    val baseUrl = model.baseUrl?.trim()?.removeSuffix("/") ?: "https://api.openai.com/v1"
    val apiKey =
      model.apiKey
        ?: System.getenv("SQUAMED_AI_API_KEY")
        ?: throw IllegalArgumentException("AI_API_KEY_NOT_CONFIGURED")
    val url = "$baseUrl/chat/completions"
    val body =
      objectMapper.createObjectNode().apply {
        put("model", model.modelName ?: model.name)
        put("temperature", model.temperature)
        put("max_tokens", model.maxTokens)
        val arr = putArray("messages")
        if (!systemPrompt.isNullOrBlank()) {
          arr.addObject().put("role", "system").put("content", systemPrompt)
        }
        messages.takeLast(12).forEach { m ->
          arr.addObject().put("role", m.role).put("content", m.content)
        }
      }
    val request =
      HttpRequest
        .newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(60))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer $apiKey")
        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        .build()
    val response =
      HttpClient
        .newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
        .send(request, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() !in 200..299) {
      throw IllegalStateException("AI_PROVIDER_ERROR status=${response.statusCode()} body=${response.body().take(500)}")
    }
    val root: JsonNode = objectMapper.readTree(response.body())
    val text =
      root
        .path("choices")
        .firstOrNull()
        ?.path("message")
        ?.path("content")
        ?.asText()
        ?: root
          .path("choices")
          .firstOrNull()
          ?.path("text")
          ?.asText()
        ?: throw IllegalStateException("AI_PROVIDER_EMPTY_RESPONSE")
    // 05.01: real usage when the endpoint reports it, else a character-based estimate.
    val usage = root.path("usage")
    val promptTokens = usage.path("prompt_tokens").asInt(0)
    val completionTokens = usage.path("completion_tokens").asInt(0)
    return AiCompletion(
      text = text,
      promptTokens = if (promptTokens > 0) promptTokens else estimateTokens((systemPrompt ?: "") + messages.joinToString(" ") { it.content }),
      completionTokens = if (completionTokens > 0) completionTokens else estimateTokens(text),
    )
  }
}

@Component
class AiModelRouter(
  private val providers: List<AiProvider>,
) {
  fun providerFor(providerCode: String): AiProvider =
    providers.firstOrNull { it.supports(providerCode) }
      ?: throw IllegalArgumentException("UNSUPPORTED_AI_PROVIDER: $providerCode")

  fun providerById(id: String): AiProvider = providers.firstOrNull { it.id() == id } ?: providers.first()
}
