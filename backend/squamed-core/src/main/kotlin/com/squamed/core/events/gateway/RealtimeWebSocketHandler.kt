package com.squamed.core.events.gateway

import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.identity.security.JwtService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.springframework.web.util.UriComponentsBuilder

@Component
class RealtimeWebSocketHandler(
  private val jwtService: JwtService,
  private val registry: RealtimeSessionRegistry,
  private val objectMapper: ObjectMapper,
) : TextWebSocketHandler() {
  private val logger = LoggerFactory.getLogger(RealtimeWebSocketHandler::class.java)

  override fun supportsPartialMessages(): Boolean = false

  override fun afterConnectionEstablished(session: WebSocketSession) {
    val token =
      session.uri?.let {
        UriComponentsBuilder
          .fromUri(it)
          .build()
          .queryParams
          .getFirst("token")
      }
    val claims = token?.let { jwtService.parseAccessToken(it) }
    val userId = claims?.userId
    if (userId == null) {
      session.close(CloseStatus.POLICY_VIOLATION)
      return
    }
    // Hospital scope (ADR-13): the session only receives events for the hospital in its token.
    claims.hospitalId?.let { session.attributes[RealtimeSessionRegistry.HOSPITAL_ATTR] = it }
    registry.add(userId, session)
    try {
      val welcome =
        objectMapper.writeValueAsString(
          mapOf("type" to "connected", "userId" to userId),
        )
      session.sendMessage(TextMessage(welcome))
    } catch (e: Exception) {
      logger.debug("Failed to send welcome frame to session={}", session.id, e)
    }
  }

  override fun handleTextMessage(
    session: WebSocketSession,
    message: TextMessage,
  ) {
    logger.debug("WebSocket message from client session={} payload={}", session.id, message.payload)
  }

  override fun afterConnectionClosed(
    session: WebSocketSession,
    status: CloseStatus,
  ) {
    registry.remove(session)
  }

  override fun handleTransportError(
    session: WebSocketSession,
    exception: Throwable,
  ) {
    logger.debug("WebSocket transport error session={} error={}", session.id, exception.message)
    registry.remove(session)
    try {
      session.close(CloseStatus.SERVER_ERROR)
    } catch (_: Exception) {
    }
  }
}
