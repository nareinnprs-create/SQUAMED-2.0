package com.squamed.core.emergency.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.emergency.service.EmergencyService
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/emergency")
class EmergencyController(
  private val emergencyService: EmergencyService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping("/registrations")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("emergency:write:emergency")
  @Transactional
  fun register(
    @RequestBody dto: EmergencyRegisterDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<EmergencyRegistrationView> =
    ApiResponse.ok(emergencyService.register(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @GetMapping("/registrations")
  @RequiresPermission("emergency:read:emergency")
  @Transactional(readOnly = true)
  fun listRegistrations(
    @RequestParam(name = "status", required = false) status: String?,
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
  ): ApiResponse<List<EmergencyRegistrationView>> = ApiResponse.ok(emergencyService.listRegistrations(status, patientId))

  @GetMapping("/registrations/{id}")
  @RequiresPermission("emergency:read:emergency")
  @Transactional(readOnly = true)
  fun getRegistration(
    @PathVariable id: UUID,
  ): ApiResponse<EmergencyRegistrationView> = ApiResponse.ok(emergencyService.getRegistration(id))

  @PostMapping("/registrations/{id}/triage")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("emergency:write:emergency")
  @Transactional
  fun triage(
    @PathVariable id: UUID,
    @RequestBody dto: TriageCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<TriageView> = ApiResponse.ok(emergencyService.triage(id, dto, principal.userId))

  @PostMapping("/registrations/{id}/trauma/activate")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("emergency:write:emergency")
  @Transactional
  fun activateTrauma(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<TraumaWorkflowView> = ApiResponse.ok(emergencyService.activateTrauma(id, principal.userId))

  @PutMapping("/registrations/{id}/trauma")
  @RequiresPermission("emergency:write:emergency")
  @Transactional
  fun updateTraumaSurvey(
    @PathVariable id: UUID,
    @RequestBody dto: TraumaSurveyDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<TraumaWorkflowView> = ApiResponse.ok(emergencyService.updateTraumaSurvey(id, dto, principal.userId))

  @PatchMapping("/registrations/{id}/status")
  @RequiresPermission("emergency:write:emergency")
  @Transactional
  fun updateStatus(
    @PathVariable id: UUID,
    @RequestBody dto: EmergencyStatusDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<EmergencyRegistrationView> = ApiResponse.ok(emergencyService.updateStatus(id, dto, principal.userId))
}
