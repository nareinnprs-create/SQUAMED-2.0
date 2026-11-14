package com.squamed.core.surgery.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.surgery.service.SurgeryService
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
@RequestMapping("/api/v1/surgery")
class SurgeryController(
  private val surgeryService: SurgeryService,
  private val tenantResolver: TenantResolver,
) {
  // ADR-13: hospital users are pinned to their tenant; admins may choose via dto.hospitalId.
  @PostMapping("/ot-rooms")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("surgery:write:surgery")
  @Transactional
  fun createOtRoom(
    @RequestBody dto: OtRoomCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<OtRoomView> =
    ApiResponse.ok(surgeryService.createOtRoom(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @GetMapping("/ot-rooms")
  @RequiresPermission("surgery:read:surgery")
  @Transactional(readOnly = true)
  fun listOtRooms(
    @RequestParam(name = "hospitalId", required = false) hospitalId: UUID?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<OtRoomView>> = ApiResponse.ok(surgeryService.listOtRooms(tenantResolver.pin(principal, hospitalId)))

  @PostMapping("/schedules")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("surgery:write:surgery")
  @Transactional
  fun createSchedule(
    @RequestBody dto: SurgeryScheduleCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<SurgeryScheduleView> =
    ApiResponse.ok(surgeryService.createSchedule(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @GetMapping("/schedules")
  @RequiresPermission("surgery:read:surgery")
  @Transactional(readOnly = true)
  fun listSchedules(
    @RequestParam(name = "status", required = false) status: String?,
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
  ): ApiResponse<List<SurgeryScheduleView>> = ApiResponse.ok(surgeryService.listSchedules(status, patientId))

  @GetMapping("/schedules/{id}")
  @RequiresPermission("surgery:read:surgery")
  @Transactional(readOnly = true)
  fun getSchedule(
    @PathVariable id: UUID,
  ): ApiResponse<SurgeryScheduleView> = ApiResponse.ok(surgeryService.getSchedule(id))

  @PatchMapping("/schedules/{id}/status")
  @RequiresPermission("surgery:write:surgery")
  @Transactional
  fun updateStatus(
    @PathVariable id: UUID,
    @RequestBody dto: SurgeryStatusDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<SurgeryScheduleView> = ApiResponse.ok(surgeryService.updateStatus(id, dto, principal.userId))

  @PostMapping("/schedules/{id}/ot-allocation")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("surgery:write:surgery")
  @Transactional
  fun allocateOt(
    @PathVariable id: UUID,
    @RequestBody dto: OtAllocationCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<OtAllocationView> = ApiResponse.ok(surgeryService.allocateOt(id, dto, principal.userId))

  @PostMapping("/schedules/{id}/team")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("surgery:write:surgery")
  @Transactional
  fun addTeamMember(
    @PathVariable id: UUID,
    @RequestBody dto: SurgicalTeamCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<SurgicalTeamView> = ApiResponse.ok(surgeryService.addTeamMember(id, dto, principal.userId))

  @GetMapping("/schedules/{id}/team")
  @RequiresPermission("surgery:read:surgery")
  @Transactional(readOnly = true)
  fun listTeam(
    @PathVariable id: UUID,
  ): ApiResponse<List<SurgicalTeamView>> = ApiResponse.ok(surgeryService.listTeam(id))

  @PostMapping("/schedules/{id}/checklist")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("surgery:write:surgery")
  @Transactional
  fun addChecklistItem(
    @PathVariable id: UUID,
    @RequestBody dto: ChecklistCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PreOpChecklistView> = ApiResponse.ok(surgeryService.addChecklistItem(id, dto, principal.userId))

  @PatchMapping("/schedules/{id}/checklist/{itemId}/check")
  @RequiresPermission("surgery:write:surgery")
  @Transactional
  fun checkChecklistItem(
    @PathVariable id: UUID,
    @PathVariable itemId: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PreOpChecklistView> = ApiResponse.ok(surgeryService.checkChecklistItem(id, itemId, principal.userId))

  @GetMapping("/schedules/{id}/checklist")
  @RequiresPermission("surgery:read:surgery")
  @Transactional(readOnly = true)
  fun listChecklist(
    @PathVariable id: UUID,
  ): ApiResponse<List<PreOpChecklistView>> = ApiResponse.ok(surgeryService.listChecklist(id))

  @PostMapping("/schedules/{id}/instruments")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("surgery:write:surgery")
  @Transactional
  fun addInstrument(
    @PathVariable id: UUID,
    @RequestBody dto: InstrumentCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<InstrumentView> = ApiResponse.ok(surgeryService.addInstrument(id, dto, principal.userId))

  @PatchMapping("/schedules/{id}/instruments/{instrumentId}/status")
  @RequiresPermission("surgery:write:surgery")
  @Transactional
  fun updateInstrumentStatus(
    @PathVariable id: UUID,
    @PathVariable instrumentId: UUID,
    @RequestBody dto: InstrumentStatusDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<InstrumentView> = ApiResponse.ok(surgeryService.updateInstrumentStatus(id, instrumentId, dto, principal.userId))

  @GetMapping("/schedules/{id}/instruments")
  @RequiresPermission("surgery:read:surgery")
  @Transactional(readOnly = true)
  fun listInstruments(
    @PathVariable id: UUID,
  ): ApiResponse<List<InstrumentView>> = ApiResponse.ok(surgeryService.listInstruments(id))

  @PostMapping("/schedules/{id}/implants")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("surgery:write:surgery")
  @Transactional
  fun addImplant(
    @PathVariable id: UUID,
    @RequestBody dto: ImplantCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ImplantView> = ApiResponse.ok(surgeryService.addImplant(id, dto, principal.userId))

  @GetMapping("/schedules/{id}/implants")
  @RequiresPermission("surgery:read:surgery")
  @Transactional(readOnly = true)
  fun listImplants(
    @PathVariable id: UUID,
  ): ApiResponse<List<ImplantView>> = ApiResponse.ok(surgeryService.listImplants(id))

  @PostMapping("/schedules/{id}/intra-op")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("surgery:write:surgery")
  @Transactional
  fun recordIntraOp(
    @PathVariable id: UUID,
    @RequestBody dto: IntraOpCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<IntraOpView> = ApiResponse.ok(surgeryService.recordIntraOp(id, dto, principal.userId))

  @GetMapping("/schedules/{id}/intra-op")
  @RequiresPermission("surgery:read:surgery")
  @Transactional(readOnly = true)
  fun listIntraOp(
    @PathVariable id: UUID,
  ): ApiResponse<List<IntraOpView>> = ApiResponse.ok(surgeryService.listIntraOp(id))

  @PostMapping("/schedules/{id}/post-op")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("surgery:write:surgery")
  @Transactional
  fun recordPostOp(
    @PathVariable id: UUID,
    @RequestBody dto: PostOpCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PostOpView> = ApiResponse.ok(surgeryService.recordPostOp(id, dto, principal.userId))

  @GetMapping("/schedules/{id}/post-op")
  @RequiresPermission("surgery:read:surgery")
  @Transactional(readOnly = true)
  fun getPostOp(
    @PathVariable id: UUID,
  ): ApiResponse<PostOpView> = ApiResponse.ok(surgeryService.getPostOp(id))
}
