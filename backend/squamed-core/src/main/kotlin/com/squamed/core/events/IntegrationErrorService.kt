package com.squamed.core.events

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.util.UUID

/** Implemented by each integration service that can re-run a stored inbound payload. */
interface IntegrationReplayer {
  /** Integration name matching events.integration_error.integration, e.g. "hl7". */
  val integration: String

  /** Re-run the payload; true = processed cleanly (AA-level ack). */
  fun replay(raw: String): Boolean
}

@Service
class IntegrationErrorService(
  private val repository: IntegrationErrorRepository,
  private val meterRegistry: MeterRegistry,
) {
  init {
    // Inbound dead-letter backlog gauge (alerts.yml: high backlog escalates; DLQ depth alert
    // covers the outbound outbox, this one the inbound integration errors).
    meterRegistry.gauge("squamed.integration.errors", repository) { it.countByStatus("OPEN").toDouble() }
  }

  fun record(
    hospitalId: UUID?,
    integration: String,
    direction: String = "INBOUND",
    messageType: String? = null,
    payload: String?,
    error: String,
  ): IntegrationError =
    repository.save(
      IntegrationError(
        hospitalId = hospitalId,
        integration = integration,
        direction = direction,
        messageType = messageType,
        payload = payload?.take(20000),
        error = error.take(2000),
        // replay_count counts processing attempts; the original failure is attempt 1.
        replayCount = 1,
      ),
    )

  fun list(
    integration: String?,
    status: String?,
    limit: Int,
  ): List<IntegrationError> {
    val page = PageRequest.of(0, limit.coerceIn(1, 200), Sort.by(Sort.Direction.DESC, "createdAt"))
    return when {
      integration != null && status != null -> repository.findByIntegrationAndStatusOrderByCreatedAtDesc(integration, status, page)
      integration != null -> repository.findByIntegrationOrderByCreatedAtDesc(integration, page)
      status != null -> repository.findByStatusOrderByCreatedAtDesc(status, page)
      else -> repository.findAll(page).content
    }
  }
}
