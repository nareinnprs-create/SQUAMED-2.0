package com.squamed.core.kitchen.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.kitchen.service.KitchenService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

data class MenuItemRequest(
  val dietType: String,
  val meal: String,
  val name: String,
  val allergens: String? = null,
)

data class ProductionPlanRequest(
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) val productionDate: LocalDate,
  val meal: String,
)

data class TrayRequest(
  val dietOrderId: UUID,
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) val productionDate: LocalDate,
  val meal: String,
)

@RestController
@RequestMapping("/api/v1/kitchen")
class KitchenController(
  private val kitchenService: KitchenService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping("/menu-items")
  @RequiresPermission("kitchen:write:kitchen")
  fun createMenuItem(
    @RequestBody dto: MenuItemRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      kitchenService.createMenuItem(
        tenantResolver.pinOrDefault(principal, null),
        dto.dietType,
        dto.meal,
        dto.name,
        dto.allergens,
        principal.userId,
      ),
    )

  @GetMapping("/menu-items")
  @RequiresPermission("kitchen:read:kitchen")
  fun menuItems(
    @RequestParam(name = "meal", required = false) meal: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(kitchenService.menuItems(tenantResolver.pinOrDefault(principal, null), meal))

  @PostMapping("/production/plan")
  @RequiresPermission("kitchen:write:kitchen")
  fun generateProductionPlan(
    @RequestBody dto: ProductionPlanRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      kitchenService.generateProductionPlan(
        tenantResolver.pinOrDefault(principal, null),
        dto.productionDate,
        dto.meal,
        principal.userId,
      ),
    )

  @GetMapping("/production")
  @RequiresPermission("kitchen:read:kitchen")
  fun productionPlan(
    @RequestParam(name = "productionDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) productionDate: LocalDate,
    @RequestParam(name = "meal") meal: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(kitchenService.productionPlan(tenantResolver.pinOrDefault(principal, null), productionDate, meal))

  @PostMapping("/production/{id}/start")
  @RequiresPermission("kitchen:write:kitchen")
  fun startProduction(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(kitchenService.startProduction(id, tenantResolver.pinOrDefault(principal, null), principal.userId))

  @PostMapping("/production/{id}/ready")
  @RequiresPermission("kitchen:write:kitchen")
  fun markProductionReady(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(kitchenService.markProductionReady(id, tenantResolver.pinOrDefault(principal, null), principal.userId))

  @PostMapping("/trays")
  @RequiresPermission("kitchen:write:kitchen")
  fun prepareTray(
    @RequestBody dto: TrayRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      kitchenService.prepareTray(
        tenantResolver.pinOrDefault(principal, null),
        dto.dietOrderId,
        dto.productionDate,
        dto.meal,
        principal.userId,
      ),
    )

  @GetMapping("/trays")
  @RequiresPermission("kitchen:read:kitchen")
  fun trays(
    @RequestParam(name = "productionDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) productionDate: LocalDate,
    @RequestParam(name = "meal") meal: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(kitchenService.trays(tenantResolver.pinOrDefault(principal, null), productionDate, meal))

  @PostMapping("/trays/{id}/deliver")
  @RequiresPermission("kitchen:write:kitchen")
  fun deliverTray(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(kitchenService.deliverTray(id, tenantResolver.pinOrDefault(principal, null), principal.userId))
}
