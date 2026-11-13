package com.squamed.core.interop.config

import com.squamed.core.interop.hl7.Hl7Service
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class Hl7ServerLifecycle(
  private val hl7Service: Hl7Service,
) {
  private val logger = KotlinLogging.logger {}

  @EventListener(ApplicationReadyEvent::class)
  fun start() {
    hl7Service.startMllp()
  }

  @PreDestroy
  fun stop() {
    hl7Service.stopMllp()
    logger.info { "HL7 MLLP server stopped" }
  }
}
