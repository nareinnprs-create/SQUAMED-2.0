package com.squamed.core.events.gateway

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig(
  private val realtimeWebSocketHandler: RealtimeWebSocketHandler,
  @Value("\${squamed.cors.allowed-origins}") private val allowedOrigins: String,
) : WebSocketConfigurer {
  override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
    registry
      .addHandler(realtimeWebSocketHandler, "/ws")
      .setAllowedOrigins(*allowedOrigins.split(",").toTypedArray())
  }
}
