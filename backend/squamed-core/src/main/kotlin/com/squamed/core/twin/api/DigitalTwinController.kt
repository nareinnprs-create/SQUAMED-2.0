package com.squamed.core.twin.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.twin.service.DigitalTwinService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class CapacityRequest(
  val additionalAdmissions: Int,
)

@RestController
@RequestMapping("/api/v1/twin")
class DigitalTwinController(
  private val service: DigitalTwinService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping("/snapshot")
  @RequiresPermission("twin:write:twin")
  fun snapshot(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.generateSnapshot(tenantResolver.pinOrDefault(principal, null), principal.userId))

  @GetMapping("/snapshot")
  @RequiresPermission("twin:read:twin")
  fun latest(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any?> = ApiResponse.ok(service.latest(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/capacity")
  @RequiresPermission("twin:read:twin")
  fun capacity(
    @RequestBody dto: CapacityRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      service.capacity(
        tenantResolver.pinOrDefault(principal, null),
        dto.additionalAdmissions,
        principal.userId,
      ),
    )

  @GetMapping("/reconcile")
  @RequiresPermission("twin:read:twin")
  fun reconcile(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.reconcile(tenantResolver.pinOrDefault(principal, null), principal.userId))

  @GetMapping("/replay")
  @RequiresPermission("twin:read:twin")
  fun replay(
    @RequestParam(name = "days", defaultValue = "30") days: Int,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.replay(tenantResolver.pinOrDefault(principal, null), days))
}
