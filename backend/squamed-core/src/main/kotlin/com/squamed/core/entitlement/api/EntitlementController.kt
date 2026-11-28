package com.squamed.core.entitlement.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.entitlement.service.BrandView
import com.squamed.core.entitlement.service.EntitlementService
import com.squamed.core.entitlement.service.EntitlementView
import com.squamed.core.entitlement.service.PlanView
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

data class SubscribeRequest(
  val planCode: String,
  val validTo: LocalDate? = null,
)

@RestController
@RequestMapping("/api/v1/entitlements")
class EntitlementController(
  private val entitlementService: EntitlementService,
  private val tenantResolver: TenantResolver,
) {
  @GetMapping
  @RequiresPermission("entitlement:read:plan")
  fun current(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<EntitlementView> = ApiResponse.ok(entitlementService.current(tenantResolver.pinOrDefault(principal, null)))

  @GetMapping("/plans")
  @RequiresPermission("entitlement:read:plan")
  fun plans(): ApiResponse<List<PlanView>> = ApiResponse.ok(entitlementService.plans())

  @PostMapping("/subscribe")
  @RequiresPermission("entitlement:manage:plan")
  fun subscribe(
    @RequestBody req: SubscribeRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<EntitlementView> =
    ApiResponse.ok(
      entitlementService.subscribe(
        tenantResolver.pinOrDefault(principal, null),
        req.planCode,
        req.validTo,
        principal.userId,
      ),
    )

  // The receipt header/footer a tenant prints. Logo assets have no store yet, so identity only.
  @GetMapping("/brand")
  @RequiresPermission("entitlement:read:plan")
  fun brand(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<BrandView> = ApiResponse.ok(entitlementService.brand(tenantResolver.pinOrDefault(principal, null)))
}
