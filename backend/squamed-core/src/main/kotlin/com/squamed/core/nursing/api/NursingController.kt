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
    // 00.04 tenant sweep: list reads are pinned to the caller's hospital (null = admin).
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<NursingAssessmentView>> = ApiResponse.ok(nursingService.listAssessments(patientId, principal.hospitalId))

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
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<NursingCarePlanView>> = ApiResponse.ok(nursingService.listCarePlans(patientId, principal.hospitalId))

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
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<NursingNoteView>> = ApiResponse.ok(nursingService.listNotes(patientId, principal.hospitalId))

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
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<NursingHandoverView>> = ApiResponse.ok(nursingService.listHandovers(wardId, principal.hospitalId))

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
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<VitalsView>> = ApiResponse.ok(nursingService.listVitals(patientId, principal.hospitalId))

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
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<MarView>> = ApiResponse.ok(nursingService.listMar(patientId, principal.hospitalId))

  @PatchMapping("/mar/{id}/status")
  @RequiresPermission("nursing:write:nursing")
  @Transactional
  fun updateMarStatus(
    @PathVariable id: UUID,
    @RequestBody dto: MarStatusDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<MarView> = ApiResponse.ok(nursingService.updateMarStatus(id, dto, principal.userId))

  // 04.02 eMAR: schedule from a prescription, barcode-checked administration, omissions.
  @PostMapping("/mar/schedule")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("nursing:write:nursing")
  @Transactional
  fun scheduleMar(
    @RequestBody dto: MarScheduleDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<MarView>> =
    ApiResponse.ok(nursingService.scheduleMar(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @PostMapping("/mar/{id}/administer")
  @RequiresPermission("nursing:write:nursing")
  @Transactional
  fun administerMar(
    @PathVariable id: UUID,
    @RequestBody dto: MarAdministerDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<MarView> = ApiResponse.ok(nursingService.administerMar(id, dto, principal.userId))

  @PostMapping("/mar/{id}/omit")
  @RequiresPermission("nursing:write:nursing")
  @Transactional
  fun omitMar(
    @PathVariable id: UUID,
    @RequestBody dto: MarOmitDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<MarView> = ApiResponse.ok(nursingService.omitMar(id, dto, principal.userId))

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
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<RiskAssessmentView>> = ApiResponse.ok(nursingService.listRiskAssessments(patientId, principal.hospitalId))

  // 04.03 configurable forms, care-plan templates, risk scales.
  @PostMapping("/assessment-templates")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("nursing:write:nursing")
  @Transactional
  fun upsertAssessmentTemplate(
    @RequestBody dto: AssessmentTemplateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AssessmentTemplateDto> = ApiResponse.ok(nursingService.upsertAssessmentTemplate(dto, tenantResolver.pinOrDefault(principal, dto.hospitalId)))

  @GetMapping("/assessment-templates")
  @RequiresPermission("nursing:read:nursing")
  @Transactional(readOnly = true)
  fun listAssessmentTemplates(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<AssessmentTemplateDto>> =
    ApiResponse.ok(nursingService.listAssessmentTemplates(principal.hospitalId ?: tenantResolver.primaryHospitalId()))

  @PostMapping("/careplan-templates")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("nursing:write:nursing")
  @Transactional
  fun upsertCarePlanTemplate(
    @RequestBody dto: CarePlanTemplateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<CarePlanTemplateDto> = ApiResponse.ok(nursingService.upsertCarePlanTemplate(dto, tenantResolver.pinOrDefault(principal, dto.hospitalId)))

  @GetMapping("/careplan-templates")
  @RequiresPermission("nursing:read:nursing")
  @Transactional(readOnly = true)
  fun listCarePlanTemplates(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<CarePlanTemplateDto>> = ApiResponse.ok(nursingService.listCarePlanTemplates(principal.hospitalId ?: tenantResolver.primaryHospitalId()))

  @PostMapping("/careplans/apply")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("nursing:write:nursing")
  @Transactional
  fun applyCarePlanTemplate(
    @RequestBody dto: CarePlanApplyDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<NursingCarePlanView> =
    ApiResponse.ok(nursingService.applyCarePlanTemplate(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @PostMapping("/risk-scales")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("nursing:write:nursing")
  @Transactional
  fun upsertRiskScale(
    @RequestBody dto: RiskScaleDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<RiskScaleDto> = ApiResponse.ok(nursingService.upsertRiskScale(dto, tenantResolver.pinOrDefault(principal, dto.hospitalId)))

  @GetMapping("/risk-scales")
  @RequiresPermission("nursing:read:nursing")
  @Transactional(readOnly = true)
  fun listRiskScales(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<RiskScaleDto>> = ApiResponse.ok(nursingService.listRiskScales(principal.hospitalId ?: tenantResolver.primaryHospitalId()))
}
