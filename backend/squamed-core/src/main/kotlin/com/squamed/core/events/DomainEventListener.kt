package com.squamed.core.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.events.gateway.RealtimeGateway
import com.squamed.core.notification.domain.Notification
import com.squamed.core.notification.repository.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class DomainEventListener(
  private val objectMapper: ObjectMapper,
  private val notificationRepository: NotificationRepository,
  private val realtimeGateway: RealtimeGateway,
) {
  private val logger = LoggerFactory.getLogger(DomainEventListener::class.java)

  @KafkaListener(topics = ["squamed.domain-events"], groupId = "\${spring.kafka.consumer.group-id}")
  fun onEvent(message: String) {
    try {
      val event = objectMapper.readValue(message, DomainEvent::class.java)
      logger.info("Received domain event type={} entityId={}", event.eventType, event.entityId)
      if (event.recipientIds.isNotEmpty()) {
        val title = resolveTitle(event)
        val body = event.payload["body"]?.toString() ?: event.eventType
        event.recipientIds.forEach { recipientId ->
          val notification =
            notificationRepository.save(
              Notification(
                hospitalId = event.hospitalId,
                recipientId = recipientId,
                channel = "in_app",
                type = event.eventType,
                title = title,
                body = body,
                payload = objectMapper.writeValueAsString(event.payload),
                deliveredAt = Instant.now(),
              ),
            )
          realtimeGateway.sendToUser(
            recipientId,
            mapOf(
              "type" to "notification.created",
              "notification" to
                mapOf(
                  "id" to notification.id,
                  "type" to notification.type,
                  "title" to notification.title,
                  "body" to notification.body,
                  "readAt" to notification.readAt,
                  "createdAt" to notification.createdAt,
                ),
            ),
          )
        }
        realtimeGateway.sendToUsers(
          event.recipientIds,
          mapOf(
            "type" to "event.received",
            "event" to
              mapOf(
                "eventType" to event.eventType,
                "entityType" to event.entityType,
                "entityId" to event.entityId,
                "payload" to event.payload,
              ),
          ),
        )
      }
    } catch (e: Exception) {
      logger.error("Failed to process domain event message", e)
    }
  }

  private fun resolveTitle(event: DomainEvent): String {
    val words = event.eventType.split(".")
    val base = if (words.size > 1) words[1] else event.payload["title"]?.toString()
    val candidate = base?.take(80)?.ifBlank { null }
    return candidate ?: event.payload["title"]?.toString()?.take(80) ?: event.eventType.take(80)
  }
}
