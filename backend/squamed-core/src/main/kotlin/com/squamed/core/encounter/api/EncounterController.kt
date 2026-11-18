package com.squamed.core.encounter.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.encounter.service.EncounterService
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
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
@RequestMapping("/api/v1/encounters")
class EncounterController(
  private val encounterService: EncounterService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("encounter:write:encounter")
  @Transactional
  fun startEncounter(
    @RequestBody dto: StartEncounterDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<EncounterView> {
    val encounter =
      encounterService.startEncounter(
        patientId = dto.patientId,
        visitId = dto.visitId,
        doctorId = dto.doctorId,
        departmentId = dto.departmentId,
        reason = dto.reason,
        chiefComplaint = dto.chiefComplaint,
        hospitalId = tenantResolver.pinOrDefault(principal, dto.hospitalId),
        principal = principal,
      )
    return ApiResponse.ok(encounter)
  }

  @GetMapping("/{id}")
  @RequiresPermission("encounter:read:encounter")
  @Transactional(readOnly = true)
  fun getEncounterDetail(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<EncounterDetailView> = ApiResponse.ok(encounterService.getEncounterDetail(id, principal.hospitalId))

  @PatchMapping("/{id}/complete")
  @RequiresPermission("encounter:write:encounter")
  @Transactional
  fun completeEncounter(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<EncounterView> = ApiResponse.ok(encounterService.completeEncounter(id, principal.hospitalId, principal))

  @PostMapping("/{id}/vitals")
  @RequiresPermission("clinical:write:vital")
  @Transactional
  fun recordVitals(
    @PathVariable id: UUID,
    @RequestBody dto: VitalSignRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<VitalSignView> = ApiResponse.ok(encounterService.recordVitals(id, dto, principal.hospitalId))

  @GetMapping("/patients/{patientId}/vitals")
  @RequiresPermission("encounter:read:encounter")
  @Transactional(readOnly = true)
  fun recentVitals(
    @PathVariable patientId: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<VitalSignView>> = ApiResponse.ok(encounterService.recentVitals(patientId, principal.hospitalId))

  @PostMapping("/{id}/notes")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("clinical:write:note")
  @Transactional
  fun addNote(
    @PathVariable id: UUID,
    @RequestBody dto: ClinicalNoteRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ClinicalNoteView> = ApiResponse.ok(encounterService.addNote(id, dto, principal, principal.hospitalId))

  @GetMapping("/{id}/notes")
  @RequiresPermission("clinical:read:note")
  @Transactional(readOnly = true)
  fun listNotes(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<ClinicalNoteView>> = ApiResponse.ok(encounterService.listNotes(id, principal.hospitalId))

  @PostMapping("/notes/{noteId}/sign")
  @RequiresPermission("clinical:write:note")
  @Transactional
  fun signNote(
    @PathVariable noteId: UUID,
    @RequestBody body: SignNoteRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ClinicalNoteView> = ApiResponse.ok(encounterService.signNote(noteId, body, principal, principal.hospitalId))

  // SAFETY L51: amendments are new note rows linked to the signed original (the legal record is never rewritten).
  @PostMapping("/notes/{noteId}/amendments")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("clinical:write:note")
  @Transactional
  fun addAmendment(
    @PathVariable noteId: UUID,
    @RequestBody dto: ClinicalNoteRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ClinicalNoteView> = ApiResponse.ok(encounterService.addAmendment(noteId, dto, principal, principal.hospitalId))

  // 01.04: template picker + coded-diagnosis catalog (global reads ride clinical:read:note).
  @GetMapping("/note-templates")
  @RequiresPermission("clinical:read:note")
  @Transactional(readOnly = true)
  fun noteTemplates(): ApiResponse<List<NoteTemplateView>> = ApiResponse.ok(encounterService.noteTemplates())

  @GetMapping("/diagnosis-catalog")
  @RequiresPermission("clinical:read:note")
  @Transactional(readOnly = true)
  fun diagnosisCatalog(): ApiResponse<List<DiagnosisCatalogEntryView>> = ApiResponse.ok(encounterService.diagnosisCatalog())

  // 01.04 procedures: chart documentation rides the note permissions (no new permission codes).
  @PostMapping("/{id}/procedures")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("clinical:write:note")
  @Transactional
  fun addProcedure(
    @PathVariable id: UUID,
    @RequestBody dto: ProcedureRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ProcedureView> = ApiResponse.ok(encounterService.addProcedure(id, dto, principal, principal.hospitalId))

  @GetMapping("/{id}/procedures")
  @RequiresPermission("clinical:read:note")
  @Transactional(readOnly = true)
  fun listProcedures(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<ProcedureView>> = ApiResponse.ok(encounterService.listProcedures(id, principal.hospitalId))

  // 01.04 referrals: activates the existing clinical:manage:referral (V13).
  @PostMapping("/{id}/referrals")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("clinical:manage:referral")
  @Transactional
  fun addReferral(
    @PathVariable id: UUID,
    @RequestBody dto: ReferralRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ReferralView> = ApiResponse.ok(encounterService.addReferral(id, dto, principal, principal.hospitalId))

  @GetMapping("/{id}/referrals")
  @RequiresPermission("clinical:read:note")
  @Transactional(readOnly = true)
  fun listReferrals(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<ReferralView>> = ApiResponse.ok(encounterService.listReferrals(id, principal.hospitalId))

  // 01.04 follow-up: booking rights stay with reception/admin (V54 contract); doctors deep-link.
  @PostMapping("/{id}/follow-up")
  @RequiresPermission("scheduling:write:appointment")
  @Transactional
  fun bookFollowUp(
    @PathVariable id: UUID,
    @RequestBody dto: FollowUpRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<EncounterView> = ApiResponse.ok(encounterService.bookFollowUp(id, dto, principal))

  @PostMapping("/{id}/diagnoses")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("clinical:write:diagnosis")
  @Transactional
  fun addDiagnosis(
    @PathVariable id: UUID,
    @RequestBody dto: DiagnosisRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DiagnosisView> = ApiResponse.ok(encounterService.addDiagnosis(id, dto, principal, principal.hospitalId))

  @GetMapping("/{id}/diagnoses")
  @RequiresPermission("clinical:read:note")
  @Transactional(readOnly = true)
  fun listDiagnoses(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<DiagnosisView>> = ApiResponse.ok(encounterService.listDiagnoses(id, principal.hospitalId))

  @DeleteMapping("/{id}/diagnoses/{diagnosisId}")
  @RequiresPermission("clinical:write:diagnosis")
  @Transactional
  fun removeDiagnosis(
    @PathVariable id: UUID,
    @PathVariable diagnosisId: UUID,
    @RequestParam patientMrn: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Unit> {
    encounterService.removeDiagnosis(id, diagnosisId, patientMrn, principal, principal.hospitalId)
    return ApiResponse.ok()
  }

  @PostMapping("/{id}/orders")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("encounter:write:encounter")
  @Transactional
  fun placeOrder(
    @PathVariable id: UUID,
    @RequestBody dto: OrderRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<OrderView> = ApiResponse.ok(encounterService.placeOrder(id, dto, principal, principal.hospitalId))

  @GetMapping("/{id}/orders")
  @RequiresPermission("encounter:read:encounter")
  @Transactional(readOnly = true)
  fun listOrders(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<OrderView>> = ApiResponse.ok(encounterService.listOrders(id, principal.hospitalId))

  @PostMapping("/{id}/prescriptions")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("clinical:write:prescription")
  @Transactional
  fun prescribe(
    @PathVariable id: UUID,
    @RequestBody dto: PrescriptionRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PrescriptionView> = ApiResponse.ok(encounterService.prescribe(id, dto, principal, principal.hospitalId))

  @GetMapping("/{id}/prescriptions")
  @RequiresPermission("encounter:read:encounter")
  @Transactional(readOnly = true)
  fun listPrescriptions(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<PrescriptionView>> = ApiResponse.ok(encounterService.listPrescriptions(id, principal.hospitalId))
}
