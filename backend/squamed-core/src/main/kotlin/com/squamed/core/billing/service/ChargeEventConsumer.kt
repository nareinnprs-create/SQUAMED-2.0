package com.squamed.core.billing.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.EventCursorService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * 03.01: consumes performed billable events from the domain topic into the charge ledger.
 * Own consumer group so the notification bridge and this ledger track independent offsets.
 */
@Component
class ChargeEventConsumer(
  private val objectMapper: ObjectMapper,
  private val chargeCaptureService: ChargeCaptureService,
  private val cursorService: EventCursorService,
) {
  @KafkaListener(topics = ["squamed.domain-events"], groupId = "billing-ledger")
  fun onEvent(message: String) {
    try {
      val event = objectMapper.readValue(message, DomainEvent::class.java)
      val captured = chargeCaptureService.capture(event)
      if (captured != null) {
        logger.info { "charge captured from ${event.eventType} entityId=${event.entityId} chargeId=${captured.id}" }
        cursorService.advance("billing-ledger", event.eventId, event.eventType, event.occurredAt)
      }
    } catch (e: Exception) {
      logger.error(e) { "Failed to capture charge from domain event" }
    }
  }
}
