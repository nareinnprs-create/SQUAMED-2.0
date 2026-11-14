package com.squamed.core.patient.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.patient.service.EmpiMatcher
import com.squamed.core.patient.service.PatientService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
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
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/patients")
class PatientController(
  private val patientService: PatientService,
  private val empiMatcher: EmpiMatcher,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("patient:write:patient")
  @Transactional
  fun create(
    @RequestBody dto: PatientCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PatientCreateResponse> {
    // ADR-13: hospital users are pinned to their own tenant; admins may choose or default to primary.
    val pinned = dto.copy(hospitalId = tenantResolver.pinOrDefault(principal, dto.hospitalId))
    val matches = empiMatcher.findMatches(pinned)
    val patient = patientService.create(pinned)
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
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PatientDetailDto> {
    requireSameHospital(principal, patientService.getHospitalId(id))
    return ApiResponse.ok(patientService.update(id, dto))
  }

  @GetMapping
  @RequiresPermission("patient:read:patient")
  @Transactional(readOnly = true)
  fun search(
    @RequestParam(name = "query", required = false) query: String?,
    @RequestParam(name = "hospitalId", required = false) hospitalId: UUID?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<PatientSummaryDto>> = ApiResponse.ok(patientService.search(query, tenantResolver.pin(principal, hospitalId)))

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
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<MergeResponseDto> {
    requireSameHospital(principal, patientService.getHospitalId(dto.targetId))
    return ApiResponse.ok(patientService.mergePatient(dto.targetId, dto.duplicateId))
  }

  @PostMapping("/{id}/identifiers")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("patient:write:patient")
  @Transactional
  fun addIdentifier(
    @PathVariable id: UUID,
    @RequestBody dto: PatientIdentifierDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PatientIdentifierDto> {
    requireSameHospital(principal, patientService.getHospitalId(id))
    return ApiResponse.ok(patientService.addIdentifier(id, dto))
  }

  @GetMapping("/{id}/identifiers")
  @RequiresPermission("patient:read:patient")
  @Transactional(readOnly = true)
  fun listIdentifiers(
    @PathVariable id: UUID,
  ): ApiResponse<List<PatientIdentifierDto>> = ApiResponse.ok(patientService.listIdentifiers(id))

  @DeleteMapping("/{id}/identifiers/{identifierId}")
  @RequiresPermission("patient:write:patient")
  @Transactional
  fun deleteIdentifier(
    @PathVariable id: UUID,
    @PathVariable identifierId: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Void> {
    requireSameHospital(principal, patientService.getHospitalId(id))
    patientService.deleteIdentifier(id, identifierId)
    return ApiResponse.ok(null)
  }

  @PostMapping("/{id}/relationships")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("patient:write:patient")
  @Transactional
  fun addRelationship(
    @PathVariable id: UUID,
    @RequestBody dto: PatientRelationshipDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PatientRelationshipDto> {
    requireSameHospital(principal, patientService.getHospitalId(id))
    return ApiResponse.ok(patientService.addRelationship(id, dto))
  }

  @GetMapping("/{id}/relationships")
  @RequiresPermission("patient:read:patient")
  @Transactional(readOnly = true)
  fun listRelationships(
    @PathVariable id: UUID,
  ): ApiResponse<List<PatientRelationshipDto>> = ApiResponse.ok(patientService.listRelationships(id))

  @DeleteMapping("/{id}/relationships/{relationshipId}")
  @RequiresPermission("patient:write:patient")
  @Transactional
  fun deleteRelationship(
    @PathVariable id: UUID,
    @PathVariable relationshipId: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Void> {
    requireSameHospital(principal, patientService.getHospitalId(id))
    patientService.deleteRelationship(id, relationshipId)
    return ApiResponse.ok(null)
  }

  @PostMapping("/{id}/photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
  @RequiresPermission("patient:write:patient")
  @Transactional
  fun uploadPhoto(
    @PathVariable id: UUID,
    @RequestParam("file") file: MultipartFile,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PhotoDto> {
    requireSameHospital(principal, patientService.getHospitalId(id))
    return ApiResponse.ok(PhotoDto(photoKey = patientService.setPhoto(id, file)))
  }

  @GetMapping("/{id}/photo")
  @RequiresPermission("patient:read:patient")
  @Transactional(readOnly = true)
  fun downloadPhoto(
    @PathVariable id: UUID,
  ): ResponseEntity<ByteArray> {
    val key = patientService.getPhotoKey(id) ?: throw IllegalArgumentException("PHOTO_NOT_UPLOADED")
    if (!key.startsWith("photos/")) throw IllegalArgumentException("PHOTO_NOT_STORED")
    val (bytes, contentType) = patientService.getPhoto(key)
    return ResponseEntity.ok().header("Content-Type", contentType).body(bytes)
  }

  private fun requireSameHospital(
    principal: SquamedPrincipal,
    entityHospitalId: UUID?,
  ) {
    val pinned = tenantResolver.pinOrDefault(principal, entityHospitalId)
    if (pinned != entityHospitalId) throw AccessDeniedException("TENANT_FORBIDDEN")
  }
}
