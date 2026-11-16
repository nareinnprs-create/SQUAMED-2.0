package com.squamed.core.analytics.api

import com.squamed.core.analytics.service.AnalyticsService
import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/analytics")
class AnalyticsController(
  private val analyticsService: AnalyticsService,
) {
  @GetMapping("/overview")
  @RequiresPermission("report:read:report")
  fun overview(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<OverviewStats> = ApiResponse.ok(analyticsService.overview(principal.hospitalId))

  @GetMapping("/patients")
  @RequiresPermission("report:read:report")
  fun patients(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PatientsStats> = ApiResponse.ok(analyticsService.patients(principal.hospitalId))

  @GetMapping("/encounters")
  @RequiresPermission("report:read:report")
  fun encounters(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<EncounterStats> = ApiResponse.ok(analyticsService.encounters(principal.hospitalId))

  @GetMapping("/appointments")
  @RequiresPermission("report:read:report")
  fun appointments(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AppointmentStats> = ApiResponse.ok(analyticsService.appointments(principal.hospitalId))

  @GetMapping("/queue")
  @RequiresPermission("report:read:report")
  fun queue(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<QueueStats> = ApiResponse.ok(analyticsService.queue(principal.hospitalId))

  @GetMapping("/lab")
  @RequiresPermission("report:read:report")
  fun lab(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<LabStats> = ApiResponse.ok(analyticsService.lab(principal.hospitalId))

  @GetMapping("/billing")
  @RequiresPermission("report:read:report")
  fun billing(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<BillingStats> = ApiResponse.ok(analyticsService.billing(principal.hospitalId))
}
