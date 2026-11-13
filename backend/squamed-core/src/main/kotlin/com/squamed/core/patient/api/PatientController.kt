package com.squamed.core.patient.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.patient.service.EmpiMatcher
import com.squamed.core.patient.service.PatientService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
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
@RequestMapping("/api/v1/patients")
class PatientController(
  private val patientService: PatientService,
  private val empiMatcher: EmpiMatcher,
) {
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("patient:write:patient")
  @Transactional
  fun create(
    @RequestBody dto: PatientCreateDto,
  ): ApiResponse<PatientCreateResponse> {
    val matches = empiMatcher.findMatches(dto)
    val patient = patientService.create(dto)
    return ApiResponse.ok(PatientCreateResponse(patient = patient, matches = matches))
  }

  @GetMapping("/{id}")
  @RequiresPermission("patient:read:patient")
  @Transactional(readOnly = true)
  fun getDetail(
    @PathVariable id: UUID,
  ): ApiResponse<PatientDetailDto> = ApiResponse.ok(patientService.getDetail(id))

  @PutMapping("/{id}")
  @RequiresPermission("patient:write:patient")
  @Transactional
  fun update(
    @PathVariable id: UUID,
    @RequestBody dto: PatientUpdateDto,
  ): ApiResponse<PatientDetailDto> = ApiResponse.ok(patientService.update(id, dto))

  @GetMapping
  @RequiresPermission("patient:read:patient")
  @Transactional(readOnly = true)
  fun search(
    @RequestParam(name = "query", required = false) query: String?,
    @RequestParam(name = "hospitalId", required = false) hospitalId: UUID?,
  ): ApiResponse<List<PatientSummaryDto>> = ApiResponse.ok(patientService.search(query, hospitalId))

  @GetMapping("/{id}/allergies")
  @RequiresPermission("patient:read:patient")
  @Transactional(readOnly = true)
  fun listAllergies(
    @PathVariable id: UUID,
  ): ApiResponse<List<AllergyDto>> = ApiResponse.ok(patientService.listAllergies(id))

  @PostMapping("/{id}/allergies")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("patient:write:patient")
  @Transactional
  fun addAllergy(
    @PathVariable id: UUID,
    @RequestBody dto: AllergyDto,
  ): ApiResponse<AllergyDto> = ApiResponse.ok(patientService.addAllergy(id, dto))

  @DeleteMapping("/{id}/allergies/{allergyId}")
  @RequiresPermission("patient:write:patient")
  @Transactional
  fun deactivateAllergy(
    @PathVariable id: UUID,
    @PathVariable allergyId: UUID,
  ): ApiResponse<AllergyDto> = ApiResponse.ok(patientService.deactivateAllergy(id, allergyId))

  @GetMapping("/{id}/notes")
  @RequiresPermission("patient:read:patient")
  @Transactional(readOnly = true)
  fun listNotes(
    @PathVariable id: UUID,
  ): ApiResponse<List<NoteDto>> = ApiResponse.ok(patientService.listNotes(id))

  @PostMapping("/{id}/notes")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("patient:write:patient")
  @Transactional
  fun addNote(
    @PathVariable id: UUID,
    @RequestBody dto: NoteDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<NoteDto> = ApiResponse.ok(patientService.addNote(id, dto, principal.userId))

  @GetMapping("/{id}/history")
  @RequiresPermission("patient:read:patient")
  @Transactional(readOnly = true)
  fun listMedicalHistory(
    @PathVariable id: UUID,
  ): ApiResponse<List<MedicalHistoryDto>> = ApiResponse.ok(patientService.listMedicalHistory(id))

  @PostMapping("/{id}/history")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("patient:write:patient")
  @Transactional
  fun addMedicalHistory(
    @PathVariable id: UUID,
    @RequestBody dto: MedicalHistoryDto,
  ): ApiResponse<MedicalHistoryDto> = ApiResponse.ok(patientService.addMedicalHistory(id, dto))

  @PostMapping("/match")
  @RequiresPermission("patient:read:patient")
  @Transactional(readOnly = true)
  fun match(
    @RequestBody dto: PatientCreateDto,
  ): ApiResponse<List<MatchCandidateDto>> = ApiResponse.ok(empiMatcher.findMatches(dto))

  @PostMapping("/merge")
  @RequiresPermission("patient:merge:patient")
  @Transactional
  fun merge(
    @RequestBody dto: MergePatientsDto,
  ): ApiResponse<MergeResponseDto> = ApiResponse.ok(patientService.mergePatient(dto.targetId, dto.duplicateId))
}
