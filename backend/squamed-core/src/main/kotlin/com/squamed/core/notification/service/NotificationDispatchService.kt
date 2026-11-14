package com.squamed.core.notification.service

import com.fasterxml.jackson.databind.JsonNode
import com.squamed.core.consent.service.ConsentService
import com.squamed.core.hospitalconfig.service.ConfigDocumentService
import com.squamed.core.notification.api.NotificationDispatchDto
import com.squamed.core.notification.api.NotificationRequestView
import com.squamed.core.notification.domain.Notification
import com.squamed.core.notification.domain.NotificationRequest
import com.squamed.core.notification.repository.NotificationRepository
import com.squamed.core.notification.repository.NotificationRequestRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Outbound dispatch (chunk-00 item 12): per-channel consent check -> DND hours gate -> channel
 * adapter, with every attempt recorded in `notifications.notification_request`. In-app delivery
 * stays event-driven via the 00.09 bridge; `channel=in_app` here writes the row directly.
 */
@Service
class NotificationDispatchService(
  private val requestRepository: NotificationRequestRepository,
  private val notificationRepository: NotificationRepository,
  private val consentService: ConsentService,
  private val configDocumentService: ConfigDocumentService,
  private val adapters: ChannelAdapters,
) {
  companion object {
    private const val SENT = "SENT"
    private const val FAILED = "FAILED"
    private const val BLOCKED_NO_CONSENT = "BLOCKED_NO_CONSENT"
    private const val DEFERRED_DND = "DEFERRED_DND"
    private const val SKIPPED_NO_PROVIDER = "SKIPPED_NO_PROVIDER"
    private const val SKIPPED_NO_SMTP_CONFIG = "SKIPPED_NO_SMTP_CONFIG"
  }

  @Transactional
  fun dispatch(
    dto: NotificationDispatchDto,
    actorId: UUID,
  ): NotificationRequestView {
    val channel = dto.channel?.lowercase() ?: throw IllegalArgumentException("NOTIFICATION_CHANNEL_REQUIRED")
    if (channel !in NotificationRequest.OUTBOUND_CHANNELS) {
      throw IllegalArgumentException("NOTIFICATION_INVALID_CHANNEL: $channel")
    }
    val title = dto.title ?: throw IllegalArgumentException("NOTIFICATION_TITLE_REQUIRED")

    if (channel == "in_app") {
      val recipientId = dto.recipientId ?: throw IllegalArgumentException("NOTIFICATION_RECIPIENT_REQUIRED")
      notificationRepository.save(
        Notification(
          hospitalId = dto.hospitalId,
          recipientId = recipientId,
          channel = "in_app",
          type = dto.type ?: "general",
          title = title,
          body = dto.body,
          payload = dto.payload,
          deliveredAt = Instant.now(),
        ),
      )
      return record(
        dto,
        actorId,
        channel,
        title,
        SENT,
        providerRef = "in_app",
        patientId = dto.patientId,
        recipientId = recipientId,
      )
    }

    // Outbound patient channels: the consent gate is the named chunk item 12 requirement.
    val patientId = dto.patientId ?: throw IllegalArgumentException("NOTIFICATION_PATIENT_REQUIRED")
    val allowed = consentService.channelAllowed(patientId, channel, dto.hospitalId)
    if (!allowed) {
      return record(dto, actorId, channel, title, BLOCKED_NO_CONSENT)
    }
    if (inDndWindow(dto.hospitalId, Instant.now())) {
      return record(dto, actorId, channel, title, DEFERRED_DND)
    }

    val address = dto.recipientAddress ?: throw IllegalArgumentException("NOTIFICATION_RECIPIENT_ADDRESS_REQUIRED")
    val outcome =
      try {
        when (channel) {
          "sms" -> adapters.sendSms(address, "$title: ${dto.body ?: ""}".trim())?.let { SENT to it } ?: (SKIPPED_NO_PROVIDER to null)
          "whatsapp" -> adapters.sendWhatsApp(address, "$title: ${dto.body ?: ""}".trim())?.let { SENT to it } ?: (SKIPPED_NO_PROVIDER to null)
          "email" -> adapters.sendEmail(address, title, dto.body ?: "")?.let { SENT to it } ?: (SKIPPED_NO_SMTP_CONFIG to null)
          "push" -> adapters.sendPush(address, title, dto.body ?: "")?.let { SENT to it } ?: (SKIPPED_NO_PROVIDER to null)
          else -> throw IllegalArgumentException("NOTIFICATION_INVALID_CHANNEL: $channel")
        }
      } catch (e: Exception) {
        FAILED to e.message
      }
    return record(dto, actorId, channel, title, outcome.first, outcome.second, errorMessage = if (outcome.first == FAILED) outcome.second else null)
  }

  @Transactional(readOnly = true)
  fun listRequests(
    patientId: UUID?,
    hospitalId: UUID?,
  ): List<NotificationRequestView> = requestRepository.findRequests(patientId, hospitalId).map { NotificationRequestView.from(it) }

  /** DND hours from the hospital's `dnd-hours` config doc; disabled/absent = never defer. */
  private fun inDndWindow(
    hospitalId: UUID?,
    now: Instant,
  ): Boolean {
    if (hospitalId == null) return false
    val config = configDocumentService.latest(hospitalId, "dnd-hours")?.content ?: return false
    if (!config.path("enabled").asBoolean(false)) return false
    val start = config.path("startHour").asInt(-1)
    val end = config.path("endHour").asInt(-1)
    if (start < 0 || end < 0) return false
    val bypass = config.path("emergencyBypass").asBoolean(false)
    if (bypass) return false
    val hour = now.atZone(java.time.ZoneOffset.UTC).hour
    return if (start == end) {
      true
    } else if (start < end) {
      hour in start until end
    } else {
      hour >= start || hour < end // window wraps midnight
    }
  }

  private fun record(
    dto: NotificationDispatchDto,
    actorId: UUID,
    channel: String,
    title: String,
    status: String,
    providerRef: String? = null,
    patientId: UUID? = null,
    recipientId: UUID? = null,
    errorMessage: String? = null,
  ): NotificationRequestView =
    NotificationRequestView.from(
      requestRepository.save(
        NotificationRequest(
          hospitalId = dto.hospitalId,
          patientId = patientId ?: dto.patientId,
          recipientId = recipientId ?: dto.recipientId,
          recipientAddress = dto.recipientAddress,
          channel = channel,
          type = dto.type ?: "general",
          title = title,
          body = dto.body,
          status = status,
          providerRef = providerRef,
          errorMessage = errorMessage,
          requestedBy = actorId,
        ),
      ),
    )
}
