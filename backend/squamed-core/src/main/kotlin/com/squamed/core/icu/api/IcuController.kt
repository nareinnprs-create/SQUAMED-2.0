package com.squamed.core.icu.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.icu.service.IcuService
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/icu")
class IcuController(
  private val icuService: IcuService,
  private val tenantResolver: TenantResolver,
) {
  // ADR-13: hospital users are pinned to their tenant; admins may choose via dto.hospitalId.
  @PostMapping("/vitals")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("icu:write:icu")
  @Transactional
  fun recordVitals(
    @RequestBody dto: IcuVitalsCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<IcuVitalsView> =
    ApiResponse.ok(icuService.recordVitals(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @GetMapping("/vitals")
  @RequiresPermission("icu:read:icu")
  @Transactional(readOnly = true)
  fun listVitals(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
    // 00.04 tenant sweep: list reads are pinned to the caller's hospital (null = admin).
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<IcuVitalsView>> = ApiResponse.ok(icuService.listVitals(patientId, principal.hospitalId))

  @PostMapping("/fluid-balance")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("icu:write:icu")
  @Transactional
  fun createFluidBalance(
    @RequestBody dto: FluidBalanceCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<FluidBalanceView> =
    ApiResponse.ok(icuService.createFluidBalance(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @GetMapping("/fluid-balance")
  @RequiresPermission("icu:read:icu")
  @Transactional(readOnly = true)
  fun listFluidBalance(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<FluidBalanceView>> = ApiResponse.ok(icuService.listFluidBalance(patientId, principal.hospitalId))

  @PostMapping("/ventilation")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("icu:write:icu")
  @Transactional
  fun createVentilation(
    @RequestBody dto: VentilationCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<VentilationView> =
    ApiResponse.ok(icuService.createVentilation(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @GetMapping("/ventilation")
  @RequiresPermission("icu:read:icu")
  @Transactional(readOnly = true)
  fun listVentilation(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<VentilationView>> = ApiResponse.ok(icuService.listVentilation(patientId, principal.hospitalId))

  @PostMapping("/infusions")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("icu:write:icu")
  @Transactional
  fun createInfusion(
    @RequestBody dto: InfusionCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<InfusionView> =
    ApiResponse.ok(icuService.createInfusion(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @GetMapping("/infusions")
  @RequiresPermission("icu:read:icu")
  @Transactional(readOnly = true)
  fun listInfusions(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<InfusionView>> = ApiResponse.ok(icuService.listInfusions(patientId, principal.hospitalId))

  @PatchMapping("/infusions/{id}/status")
  @RequiresPermission("icu:write:icu")
  @Transactional
  fun updateInfusionStatus(
    @PathVariable id: UUID,
    @RequestBody dto: InfusionStatusDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<InfusionView> = ApiResponse.ok(icuService.updateInfusionStatus(id, dto, principal.userId))

  @PostMapping("/alerts")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("icu:write:icu")
  @Transactional
  fun createAlert(
    @RequestBody dto: CriticalAlertCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<CriticalAlertView> =
    ApiResponse.ok(icuService.createAlert(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @GetMapping("/alerts")
  @RequiresPermission("icu:read:icu")
  @Transactional(readOnly = true)
  fun listAlerts(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
    @RequestParam(name = "status", required = false) status: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<CriticalAlertView>> = ApiResponse.ok(icuService.listAlerts(patientId, status, principal.hospitalId))

  // 04.05 flowsheet read model, fluid-balance totals, SOFA severity score.
  @GetMapping("/patients/{patientId}/flowsheet")
  @RequiresPermission("icu:read:icu")
  @Transactional(readOnly = true)
  fun flowsheet(
    @PathVariable patientId: UUID,
    @RequestParam(name = "hours", required = false) hours: Int?,
  ): ApiResponse<List<FlowsheetRowView>> = ApiResponse.ok(icuService.flowsheet(patientId, hours ?: 12))

  @GetMapping("/patients/{patientId}/fluid-balance/totals")
  @RequiresPermission("icu:read:icu")
  @Transactional(readOnly = true)
  fun fluidBalanceTotals(
    @PathVariable patientId: UUID,
    @RequestParam(name = "hours", required = false) hours: Int?,
  ): ApiResponse<FluidBalanceTotalsView> = ApiResponse.ok(icuService.fluidBalanceTotals(patientId, hours ?: 24))

  @PostMapping("/severity-scores")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("icu:write:icu")
  @Transactional
  fun recordSeverityScore(
    @RequestBody dto: SeverityScoreRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<SeverityScoreView> =
    ApiResponse.ok(icuService.recordSeverityScore(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @GetMapping("/patients/{patientId}/severity-scores")
  @RequiresPermission("icu:read:icu")
  @Transactional(readOnly = true)
  fun listSeverityScores(
    @PathVariable patientId: UUID,
  ): ApiResponse<List<SeverityScoreView>> = ApiResponse.ok(icuService.listSeverityScores(patientId))

  @PatchMapping("/alerts/{id}/acknowledge")
  @RequiresPermission("icu:write:icu")
  @Transactional
  fun acknowledgeAlert(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<CriticalAlertView> = ApiResponse.ok(icuService.acknowledgeAlert(id, principal.userId))

  @PatchMapping("/alerts/{id}/status")
  @RequiresPermission("icu:write:icu")
  @Transactional
  fun updateAlertStatus(
    @PathVariable id: UUID,
    @RequestBody dto: CriticalAlertStatusDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<CriticalAlertView> = ApiResponse.ok(icuService.updateAlertStatus(id, dto, principal.userId))
}
