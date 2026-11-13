package com.squamed.core.events.gateway

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import java.util.UUID

@Service
class RealtimeGateway(
  private val registry: RealtimeSessionRegistry,
  private val objectMapper: ObjectMapper,
) {
  private val logger = LoggerFactory.getLogger(RealtimeGateway::class.java)

  fun sendToUser(
    userId: UUID,
    payload: Any,
  ) {
    val json =
      try {
        objectMapper.writeValueAsString(payload)
      } catch (e: Exception) {
        logger.error("Failed to serialize realtime payload for userId={}", userId, e)
        return
      }
    registry.sessionsFor(userId).forEach { session ->
      try {
        if (session.isOpen) {
          synchronized(session) {
            session.sendMessage(TextMessage(json))
          }
        }
      } catch (e: Exception) {
        logger.error("Failed to send realtime frame to userId={} session={}", userId, session.id, e)
      }
    }
  }

  fun sendToUsers(
    userIds: List<UUID>,
    payload: Any,
  ) {
    userIds.distinct().forEach { userId -> sendToUser(userId, payload) }
  }
}
