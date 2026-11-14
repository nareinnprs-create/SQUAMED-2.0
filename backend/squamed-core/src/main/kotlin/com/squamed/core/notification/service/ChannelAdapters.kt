package com.squamed.core.notification.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Outbound channel adapters (chunk-00 item 12): email via SMTP (Spring Mail), SMS + WhatsApp via
 * generic HTTP provider endpoints, push as the chunk's named stub. Unconfigured providers return
 * `null` from `send` and the dispatch service records the honest outcome
 * (SKIPPED_NO_PROVIDER / SKIPPED_NO_SMTP_CONFIG) — E2/E3 credentials land as config, not code.
 */
@Component
class ChannelAdapters(
  private val mailSender: JavaMailSender?,
  @Value("\${squamed.channels.sms.url:}") private val smsUrl: String,
  @Value("\${squamed.channels.sms.api-key:}") private val smsApiKey: String,
  @Value("\${squamed.channels.whatsapp.url:}") private val whatsappUrl: String,
  @Value("\${squamed.channels.whatsapp.token:}") private val whatsappToken: String,
  @Value("\${squamed.channels.push.url:}") private val pushUrl: String,
) {
  private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

  /** @return provider reference, or null when the provider is not configured. */
  fun sendSms(
    to: String,
    message: String,
  ): String? {
    if (smsUrl.isBlank()) return null
    val response =
      http.send(
        postJson(
          smsUrl,
          """{"to":${jsonString(to)},"message":${jsonString(message)}}""",
          mapOf("Authorization" to "Bearer $smsApiKey"),
        ),
        HttpResponse.BodyHandlers.ofString(),
      )
    require(response.statusCode() in 200..299) { "SMS_PROVIDER_ERROR: ${response.statusCode()}" }
    return response.body().take(255)
  }

  /** @return provider reference, or null when WABA is not configured (E3). */
  fun sendWhatsApp(
    to: String,
    message: String,
  ): String? {
    if (whatsappUrl.isBlank()) return null
    val response =
      http.send(
        postJson(
          whatsappUrl,
          """{"to":${jsonString(to)},"message":${jsonString(message)}}""",
          mapOf("Authorization" to "Bearer $whatsappToken"),
        ),
        HttpResponse.BodyHandlers.ofString(),
      )
    require(response.statusCode() in 200..299) { "WHATSAPP_PROVIDER_ERROR: ${response.statusCode()}" }
    return response.body().take(255)
  }

  /** @return provider reference, or null when no SMTP host is configured. */
  fun sendEmail(
    to: String,
    subject: String,
    body: String,
  ): String? {
    val sender = mailSender ?: return null
    val mime = sender.createMimeMessage()
    MimeMessageHelper(mime, false, "UTF-8").apply {
      setTo(to)
      setSubject(subject)
      setText(body, false)
    }
    sender.send(mime)
    return "smtp"
  }

  /** Push: the chunk names this a stub — requests are shaped for an FCM-like endpoint; unconfigured is honest-skip.
   * shortcut: push stays request-shaped only (chunk-00 item 12 "push stub"), upgrade when a push provider is chosen. */
  fun sendPush(
    to: String,
    title: String,
    body: String,
  ): String? {
    if (pushUrl.isBlank()) return null
    val response =
      http.send(
        postJson(
          pushUrl,
          """{"to":${jsonString(to)},"title":${jsonString(title)},"body":${jsonString(body)}}""",
          mapOf(),
        ),
        HttpResponse.BodyHandlers.ofString(),
      )
    require(response.statusCode() in 200..299) { "PUSH_PROVIDER_ERROR: ${response.statusCode()}" }
    return response.body().take(255)
  }

  private fun postJson(
    url: String,
    json: String,
    headers: Map<String, String>,
  ): HttpRequest =
    HttpRequest
      .newBuilder()
      .uri(URI.create(url))
      .timeout(Duration.ofSeconds(10))
      .header("Content-Type", "application/json")
      .apply { headers.filter { it.value.isNotBlank() }.forEach { (k, v) -> header(k, v) } }
      .POST(HttpRequest.BodyPublishers.ofString(json))
      .build()

  private fun jsonString(value: String): String =
    "\"" +
      value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r") + "\""
}
