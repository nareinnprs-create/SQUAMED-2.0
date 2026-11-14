package com.squamed.core.nursing.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.nursing.service.NursingService
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
@RequestMapping("/api/v1/nursing")
class NursingController(
  private val nursingService: NursingService,
  private val tenantResolver: TenantResolver,
) {
  // ADR-13: hospital users are pinned to their tenant; admins may choose via dto.hospitalId.
  @PostMapping("/assessments")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("nursing:write:nursing")
  @Transactional
  fun createAssessment(
    @RequestBody dto: NursingAssessmentCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<NursingAssessmentView> =
    ApiResponse.ok(nursingService.createAssessment(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @GetMapping("/assessments")
  @RequiresPermission("nursing:read:nursing")
  @Transactional(readOnly = true)
  fun listAssessments(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
  ): ApiResponse<List<NursingAssessmentView>> = ApiResponse.ok(nursingService.listAssessments(patientId))

  @PostMapping("/careplans")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("nursing:write:nursing")
  @Transactional
  fun createCarePlan(
    @RequestBody dto: NursingCarePlanCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<NursingCarePlanView> =
    ApiResponse.ok(nursingService.createCarePlan(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @GetMapping("/careplans")
  @RequiresPermission("nursing:read:nursing")
  @Transactional(readOnly = true)
  fun listCarePlans(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
  ): ApiResponse<List<NursingCarePlanView>> = ApiResponse.ok(nursingService.listCarePlans(patientId))

  @PatchMapping("/careplans/{id}/status")
  @RequiresPermission("nursing:write:nursing")
  @Transactional
  fun updateCarePlanStatus(
    @PathVariable id: UUID,
    @RequestBody dto: NursingCarePlanStatusDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<NursingCarePlanView> = ApiResponse.ok(nursingService.updateCarePlanStatus(id, dto, principal.userId))

  @PostMapping("/notes")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("nursing:write:nursing")
  @Transactional
  fun createNote(
    @RequestBody dto: NursingNoteCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<NursingNoteView> =
    ApiResponse.ok(nursingService.createNote(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @GetMapping("/notes")
  @RequiresPermission("nursing:read:nursing")
  @Transactional(readOnly = true)
  fun listNotes(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
  ): ApiResponse<List<NursingNoteView>> = ApiResponse.ok(nursingService.listNotes(patientId))

  @PostMapping("/handovers")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("nursing:write:nursing")
  @Transactional
  fun createHandover(
    @RequestBody dto: NursingHandoverCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<NursingHandoverView> =
    ApiResponse.ok(nursingService.createHandover(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @GetMapping("/handovers")
  @RequiresPermission("nursing:read:nursing")
  @Transactional(readOnly = true)
  fun listHandovers(
    @RequestParam(name = "wardId", required = false) wardId: UUID?,
  ): ApiResponse<List<NursingHandoverView>> = ApiResponse.ok(nursingService.listHandovers(wardId))

  @PostMapping("/vitals")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("nursing:write:nursing")
  @Transactional
  fun recordVitals(
    @RequestBody dto: VitalsCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<VitalsView> =
    ApiResponse.ok(nursingService.recordVitals(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @GetMapping("/vitals")
  @RequiresPermission("nursing:read:nursing")
  @Transactional(readOnly = true)
  fun listVitals(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
  ): ApiResponse<List<VitalsView>> = ApiResponse.ok(nursingService.listVitals(patientId))

  @PostMapping("/mar")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("nursing:write:nursing")
  @Transactional
  fun createMar(
    @RequestBody dto: MarCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<MarView> = ApiResponse.ok(nursingService.createMar(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @GetMapping("/mar")
  @RequiresPermission("nursing:read:nursing")
  @Transactional(readOnly = true)
  fun listMar(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
  ): ApiResponse<List<MarView>> = ApiResponse.ok(nursingService.listMar(patientId))

  @PatchMapping("/mar/{id}/status")
  @RequiresPermission("nursing:write:nursing")
  @Transactional
  fun updateMarStatus(
    @PathVariable id: UUID,
    @RequestBody dto: MarStatusDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<MarView> = ApiResponse.ok(nursingService.updateMarStatus(id, dto, principal.userId))

  @PostMapping("/risk-assessments")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("nursing:write:nursing")
  @Transactional
  fun createRiskAssessment(
    @RequestBody dto: RiskAssessmentCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<RiskAssessmentView> =
    ApiResponse.ok(nursingService.createRiskAssessment(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @GetMapping("/risk-assessments")
  @RequiresPermission("nursing:read:nursing")
  @Transactional(readOnly = true)
  fun listRiskAssessments(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
  ): ApiResponse<List<RiskAssessmentView>> = ApiResponse.ok(nursingService.listRiskAssessments(patientId))
}
