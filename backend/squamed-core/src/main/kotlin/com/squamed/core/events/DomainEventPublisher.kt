package com.squamed.core.events

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class DomainEventPublisher(
  private val kafkaTemplate: KafkaTemplate<String, String>,
  private val objectMapper: ObjectMapper,
) {
  private val logger = LoggerFactory.getLogger(DomainEventPublisher::class.java)

  fun publish(event: DomainEvent) {
    try {
      val json = objectMapper.writeValueAsString(event)
      kafkaTemplate.send("squamed.domain-events", event.eventType, json)

      val topic = topicFor(event.eventType)
      if (topic != null) {
        kafkaTemplate.send(topic, event.eventType, json)
      }
    } catch (e: Exception) {
      logger.error("Failed to publish domain event type={} entityId={}", event.eventType, event.entityId, e)
    }
  }

  private fun topicFor(eventType: String): String? {
    if (eventType.startsWith("task.") || eventType.startsWith("notification.")) {
      return "squamed.$eventType"
    }
    return null
  }
}
