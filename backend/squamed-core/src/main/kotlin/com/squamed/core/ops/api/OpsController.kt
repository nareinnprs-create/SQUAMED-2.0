package com.squamed.core.ops.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.ops.service.CssdService
import com.squamed.core.ops.service.EdService
import com.squamed.core.ops.service.FacilitiesService
import com.squamed.core.ops.service.ProcurementService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/ops")
class OpsController(
  private val procurementService: ProcurementService,
  private val cssdService: CssdService,
  private val facilitiesService: FacilitiesService,
  private val edService: EdService,
  private val tenantResolver: TenantResolver,
) {
  // ---- procurement ----
  @PostMapping("/procurement/requisitions")
  @RequiresPermission("ops:write:procurement")
  fun createRequisition(
    @RequestBody dto: RequisitionCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<RequisitionView> = ApiResponse.ok(procurementService.createRequisition(tenantResolver.pinOrDefault(principal, null), dto, principal.userId))

  @PostMapping("/procurement/requisitions/{id}/status")
  @RequiresPermission("ops:write:procurement")
  fun transitionRequisition(
    @PathVariable id: UUID,
    @RequestBody dto: OpsStatusDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<RequisitionView> =
    ApiResponse.ok(procurementService.transitionRequisition(id, tenantResolver.pinOrDefault(principal, null), dto.status, principal.userId))

  @PostMapping("/procurement/requisitions/{id}/order")
  @RequiresPermission("ops:write:procurement")
  fun order(
    @PathVariable id: UUID,
    @RequestBody dto: OrderCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<OrderView> = ApiResponse.ok(procurementService.order(id, tenantResolver.pinOrDefault(principal, null), dto.vendorId, principal.userId))

  @PostMapping("/procurement/orders/{id}/receive")
  @RequiresPermission("ops:write:procurement")
  fun receive(
    @PathVariable id: UUID,
    @RequestBody dto: ReceiptCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ReceiptView> = ApiResponse.ok(procurementService.receive(id, tenantResolver.pinOrDefault(principal, null), dto, principal.userId))

  @GetMapping("/procurement/requisitions")
  @RequiresPermission("ops:read:ops")
  fun requisitions(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<RequisitionView>> = ApiResponse.ok(procurementService.requisitions(tenantResolver.pinOrDefault(principal, null)))

  @GetMapping("/procurement/orders")
  @RequiresPermission("ops:read:ops")
  fun orders(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<OrderView>> = ApiResponse.ok(procurementService.orders(tenantResolver.pinOrDefault(principal, null)))

  @GetMapping("/procurement/stock")
  @RequiresPermission("ops:read:ops")
  fun stock(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<StockView>> = ApiResponse.ok(procurementService.stock(tenantResolver.pinOrDefault(principal, null)))

  @GetMapping("/procurement/vendors/performance")
  @RequiresPermission("ops:read:ops")
  fun vendorPerformance(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<VendorPerformanceView>> = ApiResponse.ok(procurementService.vendorPerformance(tenantResolver.pinOrDefault(principal, null)))

  // ---- CSSD ----
  @PostMapping("/cssd/trays")
  @RequiresPermission("ops:write:cssd")
  fun createTray(
    @RequestBody dto: TrayCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<TrayView> = ApiResponse.ok(cssdService.createTray(tenantResolver.pinOrDefault(principal, null), dto, principal.userId))

  @GetMapping("/cssd/trays")
  @RequiresPermission("ops:read:ops")
  fun trays(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<TrayView>> = ApiResponse.ok(cssdService.trays(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/cssd/trays/{id}/cycles")
  @RequiresPermission("ops:write:cssd")
  fun startCycle(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<CycleView> = ApiResponse.ok(cssdService.startCycle(id, tenantResolver.pinOrDefault(principal, null), principal.userId))

  @PostMapping("/cssd/cycles/{id}/complete")
  @RequiresPermission("ops:write:cssd")
  fun completeCycle(
    @PathVariable id: UUID,
    @RequestBody dto: CycleCompleteDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<CycleView> = ApiResponse.ok(cssdService.completeCycle(id, tenantResolver.pinOrDefault(principal, null), dto.result, principal.userId))

  @PostMapping("/cssd/trays/{id}/issue")
  @RequiresPermission("ops:write:cssd")
  fun issueTray(
    @PathVariable id: UUID,
    @RequestBody dto: IssueCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<IssueView> = ApiResponse.ok(cssdService.issue(id, tenantResolver.pinOrDefault(principal, null), dto.surgeryScheduleId, principal.userId))

  @PostMapping("/cssd/trays/{id}/return")
  @RequiresPermission("ops:write:cssd")
  fun returnTray(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<IssueView> = ApiResponse.ok(cssdService.returnTray(id, tenantResolver.pinOrDefault(principal, null), principal.userId))

  @GetMapping("/cssd/traceability")
  @RequiresPermission("ops:read:ops")
  fun traceability(
    @RequestParam(name = "trayId") trayId: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<TraceabilityView> = ApiResponse.ok(cssdService.traceability(trayId, tenantResolver.pinOrDefault(principal, null)))

  // ---- facilities ----
  @PostMapping("/facilities/assets")
  @RequiresPermission("ops:write:facilities")
  fun createAsset(
    @RequestBody dto: AssetCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AssetView> = ApiResponse.ok(facilitiesService.createAsset(tenantResolver.pinOrDefault(principal, null), dto, principal.userId))

  @GetMapping("/facilities/assets")
  @RequiresPermission("ops:read:ops")
  fun assets(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<AssetView>> = ApiResponse.ok(facilitiesService.assets(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/facilities/work-orders")
  @RequiresPermission("ops:write:facilities")
  fun openWorkOrder(
    @RequestBody dto: WorkOrderCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<WorkOrderView> = ApiResponse.ok(facilitiesService.openWorkOrder(tenantResolver.pinOrDefault(principal, null), dto, principal.userId))

  @PostMapping("/facilities/work-orders/{id}/complete")
  @RequiresPermission("ops:write:facilities")
  fun completeWorkOrder(
    @PathVariable id: UUID,
    @RequestBody dto: WorkOrderCompleteDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<WorkOrderView> =
    ApiResponse.ok(facilitiesService.completeWorkOrder(id, tenantResolver.pinOrDefault(principal, null), dto.downtimeMinutes, principal.userId))

  @GetMapping("/facilities/work-orders")
  @RequiresPermission("ops:read:ops")
  fun workOrders(
    @RequestParam(name = "status", required = false) status: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<WorkOrderView>> = ApiResponse.ok(facilitiesService.workOrders(tenantResolver.pinOrDefault(principal, null), status))

  // ---- ED ----
  @PostMapping("/ed/encounters")
  @RequiresPermission("ops:write:ed")
  fun createEdEncounter(
    @RequestBody dto: EdEncounterCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<EdEncounterView> = ApiResponse.ok(edService.create(tenantResolver.pinOrDefault(principal, null), dto, principal.userId))

  @PatchMapping("/ed/encounters/{id}/status")
  @RequiresPermission("ops:write:ed")
  fun transitionEdEncounter(
    @PathVariable id: UUID,
    @RequestBody dto: OpsStatusDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<EdEncounterView> = ApiResponse.ok(edService.transition(id, tenantResolver.pinOrDefault(principal, null), dto.status, principal.userId))

  @GetMapping("/ed/encounters")
  @RequiresPermission("ops:read:ops")
  fun edEncounters(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<EdEncounterView>> = ApiResponse.ok(edService.list(tenantResolver.pinOrDefault(principal, null)))

  @GetMapping("/ed/dashboard")
  @RequiresPermission("ops:read:ops")
  fun edDashboard(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<EdDashboardView> = ApiResponse.ok(edService.dashboard(tenantResolver.pinOrDefault(principal, null)))
}
