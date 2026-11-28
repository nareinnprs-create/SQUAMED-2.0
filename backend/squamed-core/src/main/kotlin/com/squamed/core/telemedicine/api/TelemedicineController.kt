package com.squamed.core.telemedicine.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.telemedicine.service.ConsultView
import com.squamed.core.telemedicine.service.TelemedicineService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class ScheduleConsultRequest(
  val patientId: UUID,
  val doctorUserId: UUID,
  val rmpEmployeeId: UUID,
  val consultType: String,
  val mode: String,
  val scheduledAt: Instant,
  val meetingRef: String? = null,
)

data class ConsultEscapeRequest(
  val reason: String,
)

@RestController
@RequestMapping("/api/v1/telemedicine/consults")
class TelemedicineController(
  private val telemedicineService: TelemedicineService,
  private val tenantResolver: TenantResolver,
) {
  @GetMapping
  @RequiresPermission("telemedicine:read:consult")
  fun list(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<ConsultView>> = ApiResponse.ok(telemedicineService.list(tenantResolver.pinOrDefault(principal, null)))

  @GetMapping("/{id}")
  @RequiresPermission("telemedicine:read:consult")
  fun get(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ConsultView> = ApiResponse.ok(telemedicineService.get(id, tenantResolver.pinOrDefault(principal, null)))

  @PostMapping
  @RequiresPermission("telemedicine:manage:consult")
  fun schedule(
    @RequestBody req: ScheduleConsultRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ConsultView> =
    ApiResponse.ok(
      telemedicineService.schedule(
        tenantResolver.pinOrDefault(principal, null),
        req.patientId,
        req.doctorUserId,
        req.rmpEmployeeId,
        req.consultType,
        req.mode,
        req.scheduledAt,
        req.meetingRef,
        principal.userId,
      ),
    )

  @PostMapping("/{id}/start")
  @RequiresPermission("telemedicine:manage:consult")
  fun start(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ConsultView> = ApiResponse.ok(telemedicineService.start(id, tenantResolver.pinOrDefault(principal, null), principal))

  @PostMapping("/{id}/complete")
  @RequiresPermission("telemedicine:manage:consult")
  fun complete(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ConsultView> = ApiResponse.ok(telemedicineService.complete(id, tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/{id}/escape")
  @RequiresPermission("telemedicine:manage:consult")
  fun escape(
    @PathVariable id: UUID,
    @RequestBody req: ConsultEscapeRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ConsultView> = ApiResponse.ok(telemedicineService.escape(id, tenantResolver.pinOrDefault(principal, null), req.reason))

  @PostMapping("/{id}/cancel")
  @RequiresPermission("telemedicine:manage:consult")
  fun cancel(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ConsultView> = ApiResponse.ok(telemedicineService.cancel(id, tenantResolver.pinOrDefault(principal, null)))
}
