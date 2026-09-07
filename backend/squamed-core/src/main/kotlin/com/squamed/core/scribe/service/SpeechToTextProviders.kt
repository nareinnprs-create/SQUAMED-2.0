package com.squamed.core.scribe.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

interface SpeechToTextProvider {
  fun providerId(): String

  fun transcribe(
    audioBytes: ByteArray,
    contentType: String?,
  ): String
}

@Service
@ConditionalOnProperty(name = ["squamed.ai.stt.provider"], havingValue = "echo", matchIfMissing = true)
class EchoSpeechToTextProvider : SpeechToTextProvider {
  private val logger = KotlinLogging.logger {}

  override fun providerId(): String = "echo"

  override fun transcribe(
    audioBytes: ByteArray,
    contentType: String?,
  ): String {
    if (audioBytes.isEmpty()) {
      throw IllegalArgumentException("SCRIBE_AUDIO_EMPTY")
    }
    val seconds = (audioBytes.size / 16000).coerceAtLeast(1)
    logger.info { "Echo STT simulated request: ${audioBytes.size} bytes (${seconds}s)" }
    return buildString {
      append("Patient: I have been unwell for about $seconds seconds of this consultation.")
      append(" ")
      append("Doctor: Baseline vitals were taken and appear stable.")
    }
  }
}

@Service
@ConditionalOnProperty(name = ["squamed.ai.stt.provider"], havingValue = "openai")
class OpenAiWhisperProvider(
  @Value("\${squamed.ai.openai.api-key:}") private val apiKey: String,
  @Value("\${squamed.ai.openai.base-url:https://api.openai.com/v1}") private val baseUrl: String,
  @Value("\${squamed.ai.openai.whisper-model:whisper-1}") private val whisperModel: String,
) : SpeechToTextProvider {
  private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

  override fun providerId(): String = "openai-whisper"

  override fun transcribe(
    audioBytes: ByteArray,
    contentType: String?,
  ): String {
    if (apiKey.isBlank()) {
      throw IllegalStateException("AI_STT_PROVIDER_MISCONFIGURED: OPENAI API key not configured")
    }
    val boundary = "----SQUAMED-BOUNDARY-${System.currentTimeMillis()}"
    val extension =
      when (contentType?.lowercase()) {
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/m4a" -> "m4a"
        else -> "webm"
      }
    val body = buildByteArrayRequestBody(audioBytes, boundary, extension)
    val request =
      HttpRequest
        .newBuilder(URI.create("$baseUrl/audio/transcriptions"))
        .header("Authorization", "Bearer $apiKey")
        .header("Content-Type", "multipart/form-data; boundary=$boundary")
        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
        .timeout(Duration.ofSeconds(60))
        .build()
    val response = http.send(request, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() !in 200..299) {
      throw IllegalStateException("AI_STT_TRANSCRIPTION_FAILED status=${response.statusCode()}")
    }
    return response.body()
  }

  private fun buildByteArrayRequestBody(
    audioBytes: ByteArray,
    boundary: String,
    extension: String,
  ): ByteArray {
    val preamble =
      (
        "--$boundary\r\n" +
          "Content-Disposition: form-data; name=\"file\"; filename=\"recording.$extension\"\r\n" +
          "Content-Type: application/octet-stream\r\n\r\n"
      ).toByteArray()
    val afterFile = "\r\n".toByteArray()
    val modelPart =
      (
        "--$boundary\r\n" +
          "Content-Disposition: form-data; name=\"model\"\r\n\r\n" +
          "$whisperModel\r\n"
      ).toByteArray()
    val formatPart =
      (
        "--$boundary\r\n" +
          "Content-Disposition: form-data; name=\"response_format\"\r\n\r\n" +
          "text\r\n"
      ).toByteArray()
    val trailer = "--$boundary--\r\n".toByteArray()
    return preamble + audioBytes + afterFile + modelPart + formatPart + trailer
  }
}
