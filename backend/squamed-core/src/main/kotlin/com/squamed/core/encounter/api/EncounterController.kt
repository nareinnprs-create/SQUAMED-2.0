package com.squamed.core.encounter.api

import com.squamed.core.common.api.ApiResponse
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
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/encounters")
class EncounterController(
  private val encounterService: EncounterService,
) {
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("encounter:write:encounter")
  @Transactional
  fun startEncounter(
    @RequestBody dto: StartEncounterDto,
  ): ApiResponse<EncounterView> {
    val encounter =
      encounterService.startEncounter(
        patientId = dto.patientId,
        visitId = dto.visitId,
        doctorId = dto.doctorId,
        departmentId = dto.departmentId,
        reason = dto.reason,
        chiefComplaint = dto.chiefComplaint,
      )
    return ApiResponse.ok(encounter)
  }

  @GetMapping("/{id}")
  @RequiresPermission("encounter:read:encounter")
  @Transactional(readOnly = true)
  fun getEncounterDetail(
    @PathVariable id: UUID,
  ): ApiResponse<EncounterDetailView> = ApiResponse.ok(encounterService.getEncounterDetail(id))

  @PatchMapping("/{id}/complete")
  @RequiresPermission("encounter:write:encounter")
  @Transactional
  fun completeEncounter(
    @PathVariable id: UUID,
  ): ApiResponse<EncounterView> = ApiResponse.ok(encounterService.completeEncounter(id))

  @PostMapping("/{id}/vitals")
  @RequiresPermission("clinical:write:vital")
  @Transactional
  fun recordVitals(
    @PathVariable id: UUID,
    @RequestBody dto: VitalSignRequestDto,
  ): ApiResponse<VitalSignView> = ApiResponse.ok(encounterService.recordVitals(id, dto))

  @GetMapping("/patients/{patientId}/vitals")
  @RequiresPermission("encounter:read:encounter")
  @Transactional(readOnly = true)
  fun recentVitals(
    @PathVariable patientId: UUID,
  ): ApiResponse<List<VitalSignView>> = ApiResponse.ok(encounterService.recentVitals(patientId))

  @PostMapping("/{id}/notes")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("clinical:write:note")
  @Transactional
  fun addNote(
    @PathVariable id: UUID,
    @RequestBody dto: ClinicalNoteRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ClinicalNoteView> = ApiResponse.ok(encounterService.addNote(id, dto, principal.userId))

  @GetMapping("/{id}/notes")
  @RequiresPermission("clinical:read:note")
  @Transactional(readOnly = true)
  fun listNotes(
    @PathVariable id: UUID,
  ): ApiResponse<List<ClinicalNoteView>> = ApiResponse.ok(encounterService.listNotes(id))

  @PostMapping("/notes/{noteId}/sign")
  @RequiresPermission("clinical:write:note")
  @Transactional
  fun signNote(
    @PathVariable noteId: UUID,
    @RequestBody body: SignNoteRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ClinicalNoteView> = ApiResponse.ok(encounterService.signNote(noteId, principal.userId))

  @PostMapping("/{id}/diagnoses")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("clinical:write:diagnosis")
  @Transactional
  fun addDiagnosis(
    @PathVariable id: UUID,
    @RequestBody dto: DiagnosisRequestDto,
  ): ApiResponse<DiagnosisView> = ApiResponse.ok(encounterService.addDiagnosis(id, dto))

  @GetMapping("/{id}/diagnoses")
  @RequiresPermission("clinical:read:note")
  @Transactional(readOnly = true)
  fun listDiagnoses(
    @PathVariable id: UUID,
  ): ApiResponse<List<DiagnosisView>> = ApiResponse.ok(encounterService.listDiagnoses(id))

  @DeleteMapping("/{id}/diagnoses/{diagnosisId}")
  @RequiresPermission("clinical:write:diagnosis")
  @Transactional
  fun removeDiagnosis(
    @PathVariable id: UUID,
    @PathVariable diagnosisId: UUID,
  ): ApiResponse<Unit> {
    encounterService.removeDiagnosis(id, diagnosisId)
    return ApiResponse.ok()
  }

  @PostMapping("/{id}/orders")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("encounter:write:encounter")
  @Transactional
  fun placeOrder(
    @PathVariable id: UUID,
    @RequestBody dto: OrderRequestDto,
  ): ApiResponse<OrderView> = ApiResponse.ok(encounterService.placeOrder(id, dto))

  @GetMapping("/{id}/orders")
  @RequiresPermission("encounter:read:encounter")
  @Transactional(readOnly = true)
  fun listOrders(
    @PathVariable id: UUID,
  ): ApiResponse<List<OrderView>> = ApiResponse.ok(encounterService.listOrders(id))

  @PostMapping("/{id}/prescriptions")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("clinical:write:prescription")
  @Transactional
  fun prescribe(
    @PathVariable id: UUID,
    @RequestBody dto: PrescriptionRequestDto,
  ): ApiResponse<PrescriptionView> = ApiResponse.ok(encounterService.prescribe(id, dto))

  @GetMapping("/{id}/prescriptions")
  @RequiresPermission("encounter:read:encounter")
  @Transactional(readOnly = true)
  fun listPrescriptions(
    @PathVariable id: UUID,
  ): ApiResponse<List<PrescriptionView>> = ApiResponse.ok(encounterService.listPrescriptions(id))
}
