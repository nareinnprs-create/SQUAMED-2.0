package com.squamed.core.events.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.events.EventCatalog
import com.squamed.core.events.EventOpsService
import com.squamed.core.identity.security.RequiresPermission
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class OutboxReplayRequest(
  val eventId: UUID? = null,
)

/** Event fabric operations: catalog (public), outbox/replay/DLQ/cursors (events:admin). */
@RestController
class EventAdminController(
  private val eventOpsService: EventOpsService,
) {
  @GetMapping("/api/v1/events/catalog")
  fun catalog(): ApiResponse<List<Map<String, Any?>>> =
    ApiResponse.ok(
      EventCatalog.all().map { entry ->
        mapOf(
          "name" to entry.name,
          "wireName" to entry.wireName,
          "status" to entry.status.name,
          "description" to entry.description,
        )
      },
    )

  @GetMapping("/api/v1/admin/events/outbox")
  @RequiresPermission("events:admin")
  fun outbox(
    @RequestParam(name = "status", required = false) status: String?,
    @RequestParam(name = "limit", defaultValue = "50") limit: Int,
  ): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(eventOpsService.outbox(status, limit))

  @PostMapping("/api/v1/admin/events/outbox/replay")
  @RequiresPermission("events:admin")
  @Transactional
  fun replayOutbox(
    @RequestBody(required = false) request: OutboxReplayRequest?,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(eventOpsService.replayOutbox(request?.eventId))

  @GetMapping("/api/v1/admin/events/errors")
  @RequiresPermission("events:admin")
  fun integrationErrors(
    @RequestParam(name = "integration", required = false) integration: String?,
    @RequestParam(name = "status", required = false) status: String?,
    @RequestParam(name = "limit", defaultValue = "50") limit: Int,
  ): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(eventOpsService.integrationErrors(integration, status, limit))

  @PostMapping("/api/v1/admin/events/errors/{id}/replay")
  @RequiresPermission("events:admin")
  @Transactional
  fun replayIntegrationError(
    @PathVariable id: UUID,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(eventOpsService.replayIntegrationError(id))

  @GetMapping("/api/v1/admin/events/cursors")
  @RequiresPermission("events:admin")
  fun cursors(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(eventOpsService.cursors())
}
