package com.squamed.core.medsafety.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.medsafety.service.MedicationSafetyService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// 02.07 medication safety engine: interaction+allergy+duplicate+dose+renal checks (§5 rule 4),
// invoked by eRx prescribing and pharmacy dispensing; overrides audited + KPI-tracked.
@RestController
@RequestMapping("/api/v1/medication-safety")
class MedSafetyController(
  private val medicationSafetyService: MedicationSafetyService,
) {
  @PostMapping("/check")
  @RequiresPermission("medsafety:check:medication")
  @Transactional(readOnly = true)
  fun check(
    @RequestBody dto: MedSafetyCheckDto,
  ): ApiResponse<MedSafetyResultView> = ApiResponse.ok(medicationSafetyService.check(dto))

  @PostMapping("/overrides")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("medsafety:override:medication")
  @Transactional
  fun recordOverride(
    @RequestBody dto: MedSafetyOverrideDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<MedSafetyOverrideView> = ApiResponse.ok(medicationSafetyService.recordOverride(dto, principal.userId, principal.hospitalId))

  @GetMapping("/override-kpis")
  @RequiresPermission("medsafety:check:medication")
  @Transactional(readOnly = true)
  fun overrideKpis(
    @RequestParam(defaultValue = "30") days: Int,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<OverrideKpiRowView>> = ApiResponse.ok(medicationSafetyService.overrideKpis(days, principal.hospitalId))
}
