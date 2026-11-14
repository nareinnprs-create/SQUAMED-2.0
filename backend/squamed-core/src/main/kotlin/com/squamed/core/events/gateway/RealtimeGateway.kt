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

  /**
   * Push a frame to one user's sessions. `hospitalId` narrows to sessions opened with that
   * hospital's token (hospital scope); null reaches all of the user's sessions.
   */
  fun sendToUser(
    userId: UUID,
    payload: Any,
    hospitalId: UUID? = null,
  ) {
    val json =
      try {
        objectMapper.writeValueAsString(payload)
      } catch (e: Exception) {
        logger.error("Failed to serialize realtime payload for userId={}", userId, e)
        return
      }
    registry.sessionsFor(userId, hospitalId).forEach { session ->
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
    hospitalId: UUID? = null,
  ) {
    userIds.distinct().forEach { userId -> sendToUser(userId, payload, hospitalId) }
  }

  /** Push a frame to every connected session within the hospital scope (null = all sessions). */
  fun broadcast(
    hospitalId: UUID?,
    payload: Any,
  ) {
    val json =
      try {
        objectMapper.writeValueAsString(payload)
      } catch (e: Exception) {
        logger.error("Failed to serialize realtime broadcast payload", e)
        return
      }
    registry.allSessions(hospitalId).forEach { session ->
      try {
        if (session.isOpen) {
          synchronized(session) {
            session.sendMessage(TextMessage(json))
          }
        }
      } catch (e: Exception) {
        logger.error("Failed to broadcast realtime frame to session={}", session.id, e)
      }
    }
  }
}
