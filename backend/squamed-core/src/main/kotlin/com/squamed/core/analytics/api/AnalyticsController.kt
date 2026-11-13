package com.squamed.core.analytics.api

import com.squamed.core.analytics.service.AnalyticsService
import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.RequiresPermission
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
  fun overview(): ApiResponse<OverviewStats> = ApiResponse.ok(analyticsService.overview())

  @GetMapping("/patients")
  @RequiresPermission("report:read:report")
  fun patients(): ApiResponse<PatientsStats> = ApiResponse.ok(analyticsService.patients())

  @GetMapping("/encounters")
  @RequiresPermission("report:read:report")
  fun encounters(): ApiResponse<EncounterStats> = ApiResponse.ok(analyticsService.encounters())

  @GetMapping("/appointments")
  @RequiresPermission("report:read:report")
  fun appointments(): ApiResponse<AppointmentStats> = ApiResponse.ok(analyticsService.appointments())

  @GetMapping("/queue")
  @RequiresPermission("report:read:report")
  fun queue(): ApiResponse<QueueStats> = ApiResponse.ok(analyticsService.queue())

  @GetMapping("/lab")
  @RequiresPermission("report:read:report")
  fun lab(): ApiResponse<LabStats> = ApiResponse.ok(analyticsService.lab())

  @GetMapping("/billing")
  @RequiresPermission("report:read:report")
  fun billing(): ApiResponse<BillingStats> = ApiResponse.ok(analyticsService.billing())
}
