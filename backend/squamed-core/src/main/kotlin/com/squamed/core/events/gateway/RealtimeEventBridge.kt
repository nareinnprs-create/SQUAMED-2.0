package com.squamed.core.events.gateway

import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.events.DomainEvent
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

/**
 * Owns every `event.received` WebSocket frame: the dispatcher publishes to the Redis channel,
 * this bridge applies the per-user recipient filter + hospital scope and pushes to sessions.
 * Events without recipients broadcast to every session within the event's hospital scope.
 */
@Component
class RealtimeEventBridge(
  private val objectMapper: ObjectMapper,
  private val realtimeGateway: RealtimeGateway,
) : MessageListener {
  private val logger = LoggerFactory.getLogger(RealtimeEventBridge::class.java)

  override fun onMessage(
    message: Message,
    pattern: ByteArray?,
  ) {
    try {
      val event = objectMapper.readValue(String(message.body, StandardCharsets.UTF_8), DomainEvent::class.java)
      val frame = eventFrame(event)
      if (event.recipientIds.isNotEmpty()) {
        event.recipientIds.forEach { realtimeGateway.sendToUser(it, frame, event.hospitalId) }
      } else {
        realtimeGateway.broadcast(event.hospitalId, frame)
      }
    } catch (e: Exception) {
      logger.error("Failed to relay realtime event frame", e)
    }
  }

  private fun eventFrame(event: DomainEvent): Map<String, Any?> =
    mapOf(
      "type" to "event.received",
      "event" to
        mapOf(
          "eventType" to event.eventType,
          "entityType" to event.entityType,
          "entityId" to event.entityId,
          "payload" to event.payload,
        ),
    )
}
