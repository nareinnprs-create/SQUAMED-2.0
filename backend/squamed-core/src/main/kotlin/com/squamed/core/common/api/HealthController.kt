package com.squamed.core.common.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
class HealthController {
  @GetMapping("/health")
  fun health(): ResponseEntity<Map<String, Any>> =
    ResponseEntity.ok(
      mapOf(
        "status" to "ok",
        "service" to "squamed-core",
        "timestamp" to Instant.now().toString(),
        "version" to "0.1.0",
      ),
    )

  @GetMapping("/")
  fun root(): ResponseEntity<Map<String, Any>> =
    ResponseEntity.ok(
      mapOf(
        "name" to "squamed-core",
        "docs" to "/swagger-ui.html",
        "health" to "/health",
      ),
    )
}
