package com.squamed.core.anaesthesia.api

import com.squamed.core.anaesthesia.service.AnaesthesiaService
import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
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
@RequestMapping("/api/v1/anaesthesia")
class AnaesthesiaController(
  private val anaesthesiaService: AnaesthesiaService,
) {
  @PostMapping("/evaluations")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("anaesthesia:write:anaesthesia")
  @Transactional
  fun createEvaluation(
    @RequestBody dto: EvaluationCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<EvaluationView> = ApiResponse.ok(anaesthesiaService.createEvaluation(dto, principal.userId, principal.hospitalId))

  @PatchMapping("/evaluations/{id}/confirm")
  @RequiresPermission("anaesthesia:write:anaesthesia")
  @Transactional
  fun confirmEvaluation(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<EvaluationView> = ApiResponse.ok(anaesthesiaService.confirmEvaluation(id, principal.userId, principal.hospitalId))

  @GetMapping("/evaluations")
  @RequiresPermission("anaesthesia:read:anaesthesia")
  @Transactional(readOnly = true)
  fun listEvaluations(
    @RequestParam(name = "surgeryScheduleId", required = false) surgeryScheduleId: UUID?,
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<EvaluationView>> = ApiResponse.ok(anaesthesiaService.listEvaluations(surgeryScheduleId, patientId, principal.hospitalId))

  @PostMapping("/records")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("anaesthesia:write:anaesthesia")
  @Transactional
  fun createRecord(
    @RequestBody dto: AnaesthesiaRecordCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AnaesthesiaRecordView> = ApiResponse.ok(anaesthesiaService.createRecord(dto, principal.userId, principal.hospitalId))

  @PatchMapping("/records/{id}/status")
  @RequiresPermission("anaesthesia:write:anaesthesia")
  @Transactional
  fun updateRecordStatus(
    @PathVariable id: UUID,
    @RequestBody dto: AnaesthesiaRecordStatusDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AnaesthesiaRecordView> = ApiResponse.ok(anaesthesiaService.updateRecordStatus(id, dto, principal.userId, principal.hospitalId))

  @PostMapping("/records/{id}/monitoring")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("anaesthesia:write:anaesthesia")
  @Transactional
  fun addMonitoring(
    @PathVariable id: UUID,
    @RequestBody dto: MonitoringCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<MonitoringView> = ApiResponse.ok(anaesthesiaService.addMonitoring(id, dto, principal.userId, principal.hospitalId))

  @GetMapping("/records/{id}/monitoring")
  @RequiresPermission("anaesthesia:read:anaesthesia")
  @Transactional(readOnly = true)
  fun listMonitoring(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<MonitoringView>> = ApiResponse.ok(anaesthesiaService.listMonitoring(id, principal.hospitalId))

  @PostMapping("/records/{id}/medications")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("anaesthesia:write:anaesthesia")
  @Transactional
  fun addMedication(
    @PathVariable id: UUID,
    @RequestBody dto: MedicationCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<MedicationView> = ApiResponse.ok(anaesthesiaService.addMedication(id, dto, principal.userId, principal.hospitalId))

  @GetMapping("/records/{id}/medications")
  @RequiresPermission("anaesthesia:read:anaesthesia")
  @Transactional(readOnly = true)
  fun listMedications(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<MedicationView>> = ApiResponse.ok(anaesthesiaService.listMedications(id, principal.hospitalId))

  @PostMapping("/records/{id}/recovery")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("anaesthesia:write:anaesthesia")
  @Transactional
  fun recordRecovery(
    @PathVariable id: UUID,
    @RequestBody dto: RecoveryCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<RecoveryView> = ApiResponse.ok(anaesthesiaService.recordRecovery(id, dto, principal.userId, principal.hospitalId))

  @GetMapping("/records/{id}/recovery")
  @RequiresPermission("anaesthesia:read:anaesthesia")
  @Transactional(readOnly = true)
  fun listRecovery(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<RecoveryView>> = ApiResponse.ok(anaesthesiaService.listRecovery(id, principal.hospitalId))

  @PostMapping("/records/{id}/pacu")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("anaesthesia:write:anaesthesia")
  @Transactional
  fun recordPacu(
    @PathVariable id: UUID,
    @RequestBody dto: PacuCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PacuView> = ApiResponse.ok(anaesthesiaService.recordPacu(id, dto, principal.userId, principal.hospitalId))

  @GetMapping("/records/{id}/pacu")
  @RequiresPermission("anaesthesia:read:anaesthesia")
  @Transactional(readOnly = true)
  fun listPacu(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<PacuView>> = ApiResponse.ok(anaesthesiaService.listPacu(id, principal.hospitalId))
}
