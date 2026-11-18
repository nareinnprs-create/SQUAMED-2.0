package com.squamed.core.downtime.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.downtime.service.DowntimeService
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/downtime")
class DowntimeController(
  private val downtimeService: DowntimeService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping("/windows")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun openWindow(
    @RequestBody dto: DowntimeOpenDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DowntimeLogView> =
    ApiResponse.ok(
      downtimeService.openWindow(
        dto.reason ?: throw IllegalArgumentException("DOWNTIME_REASON_REQUIRED"),
        tenantResolver.pinOrDefault(principal, dto.hospitalId),
      ),
    )

  @GetMapping("/windows")
  @RequiresPermission("queue:read:queue")
  @Transactional(readOnly = true)
  fun listWindows(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<DowntimeLogView>> = ApiResponse.ok(downtimeService.listWindows(principal.hospitalId))

  @PatchMapping("/windows/{id}/close")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun closeWindow(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DowntimeLogView> = ApiResponse.ok(downtimeService.closeWindow(id, principal.hospitalId))

  // 01.09 CS-09 re-entry: paper tokens return with their original numbers; paper check-ins re-stamp visits.
  @PostMapping("/reentries")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun reenter(
    @RequestBody dto: DowntimeReentryDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DowntimeReentryResult> = ApiResponse.ok(downtimeService.reenter(dto, tenantResolver.pinOrDefault(principal, dto.hospitalId)))
}
