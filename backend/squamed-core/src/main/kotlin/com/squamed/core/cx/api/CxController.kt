package com.squamed.core.cx.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.cx.service.CxService
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class ContactInteractionRequest(
  val patientId: UUID? = null,
  val channel: String,
  val direction: String,
  val subject: String,
  val agentId: UUID? = null,
)

data class CxResolutionRequest(
  val resolution: String,
)

data class IntlCaseRequest(
  val patientId: UUID,
  val country: String,
  val language: String,
  val interpreterNeeded: Boolean = false,
  val coordinatorId: UUID? = null,
  val notes: String? = null,
)

data class FeedbackRequest(
  val patientId: UUID,
  val score: Int,
  val category: String,
  val comment: String? = null,
)

@RestController
@RequestMapping("/api/v1/cx")
class CxController(
  private val service: CxService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping("/interactions")
  @RequiresPermission("cx:write:cx")
  fun logInteraction(
    @RequestBody dto: ContactInteractionRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      service.logInteraction(
        tenantResolver.pinOrDefault(principal, null),
        dto.patientId,
        dto.channel,
        dto.direction,
        dto.subject,
        dto.agentId,
        principal.userId,
      ),
    )

  @GetMapping("/interactions")
  @RequiresPermission("cx:read:cx")
  fun interactions(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.interactions(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/interactions/{id}/close")
  @RequiresPermission("cx:write:cx")
  fun closeInteraction(
    @PathVariable id: UUID,
    @RequestBody dto: CxResolutionRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.closeInteraction(id, tenantResolver.pinOrDefault(principal, null), dto.resolution, principal.userId))

  @PostMapping("/intl-cases")
  @RequiresPermission("cx:write:cx")
  fun openIntlCase(
    @RequestBody dto: IntlCaseRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      service.openIntlCase(
        tenantResolver.pinOrDefault(principal, null),
        dto.patientId,
        dto.country,
        dto.language,
        dto.interpreterNeeded,
        dto.coordinatorId,
        dto.notes,
        principal.userId,
      ),
    )

  @GetMapping("/intl-cases")
  @RequiresPermission("cx:read:cx")
  fun intlCases(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.intlCases(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/intl-cases/{id}/close")
  @RequiresPermission("cx:write:cx")
  fun closeIntlCase(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.closeIntlCase(id, tenantResolver.pinOrDefault(principal, null), principal.userId))

  @PostMapping("/feedback")
  @RequiresPermission("cx:write:cx")
  fun recordFeedback(
    @RequestBody dto: FeedbackRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      service.recordFeedback(
        tenantResolver.pinOrDefault(principal, null),
        dto.patientId,
        dto.score,
        dto.category,
        dto.comment,
        principal.userId,
      ),
    )

  @GetMapping("/feedback")
  @RequiresPermission("cx:read:cx")
  fun feedback(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.feedback(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/feedback/{id}/acknowledge")
  @RequiresPermission("cx:write:cx")
  fun acknowledgeFeedback(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.acknowledgeFeedback(id, tenantResolver.pinOrDefault(principal, null), principal.userId))

  @PostMapping("/feedback/{id}/resolve")
  @RequiresPermission("cx:write:cx")
  fun resolveFeedback(
    @PathVariable id: UUID,
    @RequestBody dto: CxResolutionRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.resolveFeedback(id, tenantResolver.pinOrDefault(principal, null), dto.resolution, principal.userId))

  @GetMapping("/feedback/summary")
  @RequiresPermission("cx:read:cx")
  fun feedbackSummary(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.feedbackSummary(tenantResolver.pinOrDefault(principal, null)))
}
