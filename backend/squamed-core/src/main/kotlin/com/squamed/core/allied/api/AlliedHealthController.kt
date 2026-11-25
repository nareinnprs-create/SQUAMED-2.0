package com.squamed.core.allied.api

import com.squamed.core.allied.service.AlliedHealthService
import com.squamed.core.allied.service.ReferralDetailView
import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class TherapyReferralRequest(
  val patientId: UUID,
  val discipline: String,
  val reason: String,
  val therapistId: UUID? = null,
  val carePlanId: UUID? = null,
  val sessionsPlanned: Int = 4,
)

data class TherapyScheduleRequest(
  val scheduledAt: Instant,
  val therapistId: UUID,
  val appointmentId: UUID? = null,
)

data class TherapySessionRequest(
  val sessionAt: Instant,
  val durationMinutes: Int,
  val notes: String? = null,
  val outcome: String? = null,
)

data class TherapyCompleteRequest(
  val outcome: String,
)

@RestController
@RequestMapping("/api/v1/allied")
class AlliedHealthController(
  private val service: AlliedHealthService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping("/referrals")
  @RequiresPermission("allied:write:therapy")
  fun refer(
    @RequestBody dto: TherapyReferralRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      service.refer(
        tenantResolver.pinOrDefault(principal, null),
        dto.patientId,
        dto.discipline,
        dto.reason,
        dto.therapistId,
        dto.carePlanId,
        dto.sessionsPlanned,
        principal.userId,
      ),
    )

  @GetMapping("/referrals")
  @RequiresPermission("allied:read:therapy")
  fun referrals(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.referrals(tenantResolver.pinOrDefault(principal, null)))

  @GetMapping("/referrals/{id}")
  @RequiresPermission("allied:read:therapy")
  fun detail(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ReferralDetailView> = ApiResponse.ok(service.detail(id, tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/referrals/{id}/schedule")
  @RequiresPermission("allied:write:therapy")
  fun schedule(
    @PathVariable id: UUID,
    @RequestBody dto: TherapyScheduleRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      service.schedule(id, tenantResolver.pinOrDefault(principal, null), dto.scheduledAt, dto.therapistId, dto.appointmentId, principal.userId),
    )

  @PostMapping("/referrals/{id}/start")
  @RequiresPermission("allied:write:therapy")
  fun start(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.start(id, tenantResolver.pinOrDefault(principal, null), principal.userId))

  @PostMapping("/referrals/{id}/sessions")
  @RequiresPermission("allied:write:therapy")
  fun addSession(
    @PathVariable id: UUID,
    @RequestBody dto: TherapySessionRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      service.addSession(
        id,
        tenantResolver.pinOrDefault(principal, null),
        dto.sessionAt,
        dto.durationMinutes,
        dto.notes,
        dto.outcome,
        principal.userId,
      ),
    )

  @PostMapping("/referrals/{id}/sessions/{sessionId}/sign")
  @RequiresPermission("allied:write:therapy")
  fun signSession(
    @PathVariable id: UUID,
    @PathVariable sessionId: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.signSession(id, sessionId, tenantResolver.pinOrDefault(principal, null), principal.userId))

  @PostMapping("/referrals/{id}/complete")
  @RequiresPermission("allied:write:therapy")
  fun complete(
    @PathVariable id: UUID,
    @RequestBody dto: TherapyCompleteRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.complete(id, tenantResolver.pinOrDefault(principal, null), dto.outcome, principal.userId))
}
