package com.squamed.core.events.gateway

import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.events.DomainEvent
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.StatusCode
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

/**
 * Owns every `event.received` WebSocket frame: the dispatcher publishes to the Redis channel,
 * this bridge applies the per-user recipient filter + hospital scope and pushes to sessions.
 * Events without recipients broadcast to every session within the event's hospital scope.
 * The relay is the WS leg of the trace pipeline (09-INFRASTRUCTURE.md §3); no parent context
 * survives the Redis pub/sub hop, so each relay starts a fresh root span.
 */
@Component
class RealtimeEventBridge(
  private val objectMapper: ObjectMapper,
  private val realtimeGateway: RealtimeGateway,
  private val meterRegistry: MeterRegistry,
  openTelemetry: OpenTelemetry,
) : MessageListener {
  private val logger = LoggerFactory.getLogger(RealtimeEventBridge::class.java)
  private val tracer = openTelemetry.getTracer("squamed-realtime")

  override fun onMessage(
    message: Message,
    pattern: ByteArray?,
  ) {
    val span = tracer.spanBuilder("squamed.realtime.relay").startSpan()
    val scope = span.makeCurrent()
    try {
      val event = objectMapper.readValue(String(message.body, StandardCharsets.UTF_8), DomainEvent::class.java)
      span.setAttribute("squamed.event.type", event.eventType)
      val frame = eventFrame(event)
      if (event.recipientIds.isNotEmpty()) {
        event.recipientIds.forEach { realtimeGateway.sendToUser(it, frame, event.hospitalId) }
      } else {
        realtimeGateway.broadcast(event.hospitalId, frame)
      }
      meterRegistry
        .counter("squamed.ws.relayed", "type", event.eventType)
        .increment()
    } catch (e: Exception) {
      span.recordException(e)
      span.setStatus(StatusCode.ERROR, e.message ?: e.javaClass.simpleName)
      logger.error("Failed to relay realtime event frame", e)
    } finally {
      scope.close()
      span.end()
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
