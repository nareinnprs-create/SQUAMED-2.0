package com.squamed.core.bloodbank.api

import com.squamed.core.bloodbank.service.BloodBankService
import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
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
@RequestMapping("/api/v1/blood-bank")
class BloodBankController(
  private val bloodBankService: BloodBankService,
  private val tenantResolver: TenantResolver,
) {
  // ADR-13: hospital users are pinned to their tenant; admins may choose via dto.hospitalId.
  @PostMapping("/donors")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("bloodbank:write:bloodbank")
  @Transactional
  fun createDonor(
    @RequestBody dto: DonorCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DonorView> =
    ApiResponse.ok(bloodBankService.createDonor(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @PatchMapping("/donors/{id}/status")
  @RequiresPermission("bloodbank:write:bloodbank")
  @Transactional
  fun updateDonorStatus(
    @PathVariable id: UUID,
    @RequestBody dto: DonorStatusDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DonorView> = ApiResponse.ok(bloodBankService.updateDonorStatus(id, dto, principal.userId))

  @GetMapping("/donors")
  @RequiresPermission("bloodbank:read:bloodbank")
  @Transactional(readOnly = true)
  fun listDonors(
    @RequestParam(name = "status", required = false) status: String?,
    @RequestParam(name = "bloodGroup", required = false) bloodGroup: String?,
  ): ApiResponse<List<DonorView>> = ApiResponse.ok(bloodBankService.listDonors(status, bloodGroup))

  @PostMapping("/units")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("bloodbank:write:bloodbank")
  @Transactional
  fun createUnit(
    @RequestBody dto: UnitCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<UnitView> = ApiResponse.ok(bloodBankService.createUnit(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @PatchMapping("/units/{id}/status")
  @RequiresPermission("bloodbank:write:bloodbank")
  @Transactional
  fun updateUnitStatus(
    @PathVariable id: UUID,
    @RequestBody dto: UnitStatusDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<UnitView> = ApiResponse.ok(bloodBankService.updateUnitStatus(id, dto, principal.userId))

  @GetMapping("/units")
  @RequiresPermission("bloodbank:read:bloodbank")
  @Transactional(readOnly = true)
  fun listUnits(
    @RequestParam(name = "status", required = false) status: String?,
    @RequestParam(name = "bloodGroup", required = false) bloodGroup: String?,
    @RequestParam(name = "component", required = false) component: String?,
  ): ApiResponse<List<UnitView>> = ApiResponse.ok(bloodBankService.listUnits(status, bloodGroup, component))

  @PostMapping("/grouping")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("bloodbank:write:bloodbank")
  @Transactional
  fun addGrouping(
    @RequestBody dto: GroupingCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<GroupingView> =
    ApiResponse.ok(bloodBankService.addGrouping(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @PatchMapping("/grouping/{id}/confirm")
  @RequiresPermission("bloodbank:write:bloodbank")
  @Transactional
  fun confirmGrouping(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<GroupingView> = ApiResponse.ok(bloodBankService.confirmGrouping(id, principal.userId))

  @GetMapping("/grouping")
  @RequiresPermission("bloodbank:read:bloodbank")
  @Transactional(readOnly = true)
  fun getGrouping(
    @RequestParam(name = "unitId", required = true) unitId: UUID,
  ): ApiResponse<GroupingView> = ApiResponse.ok(bloodBankService.getGrouping(unitId))

  @PostMapping("/crossmatch")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("bloodbank:write:bloodbank")
  @Transactional
  fun createCrossmatch(
    @RequestBody dto: CrossmatchCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<CrossmatchView> =
    ApiResponse.ok(bloodBankService.createCrossmatch(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @PatchMapping("/crossmatch/{id}/result")
  @RequiresPermission("bloodbank:write:bloodbank")
  @Transactional
  fun updateCrossmatchResult(
    @PathVariable id: UUID,
    @RequestBody dto: CrossmatchResultDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<CrossmatchView> = ApiResponse.ok(bloodBankService.updateCrossmatchResult(id, dto, principal.userId))

  @GetMapping("/crossmatch")
  @RequiresPermission("bloodbank:read:bloodbank")
  @Transactional(readOnly = true)
  fun listCrossmatches(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
    @RequestParam(name = "unitId", required = false) unitId: UUID?,
  ): ApiResponse<List<CrossmatchView>> = ApiResponse.ok(bloodBankService.listCrossmatches(patientId, unitId))

  @PostMapping("/reservations")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("bloodbank:write:bloodbank")
  @Transactional
  fun reserve(
    @RequestBody dto: ReservationCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ReservationView> =
    ApiResponse.ok(bloodBankService.reserve(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @PostMapping("/reservations/{unitId}/release")
  @RequiresPermission("bloodbank:write:bloodbank")
  @Transactional
  fun releaseReservation(
    @PathVariable unitId: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ReservationView> = ApiResponse.ok(bloodBankService.releaseReservation(unitId, principal.userId))

  @PostMapping("/issues")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("bloodbank:write:bloodbank")
  @Transactional
  fun issue(
    @RequestBody dto: IssueCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<IssueView> = ApiResponse.ok(bloodBankService.issue(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @GetMapping("/issues")
  @RequiresPermission("bloodbank:read:bloodbank")
  @Transactional(readOnly = true)
  fun listIssues(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
  ): ApiResponse<List<IssueView>> = ApiResponse.ok(bloodBankService.listIssues(patientId))

  @PostMapping("/transfusions")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("bloodbank:write:bloodbank")
  @Transactional
  fun startTransfusion(
    @RequestBody dto: TransfusionCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<TransfusionView> =
    ApiResponse.ok(bloodBankService.startTransfusion(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @PatchMapping("/transfusions/{id}/status")
  @RequiresPermission("bloodbank:write:bloodbank")
  @Transactional
  fun updateTransfusionStatus(
    @PathVariable id: UUID,
    @RequestBody dto: TransfusionStatusDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<TransfusionView> = ApiResponse.ok(bloodBankService.updateTransfusionStatus(id, dto, principal.userId))

  @GetMapping("/transfusions")
  @RequiresPermission("bloodbank:read:bloodbank")
  @Transactional(readOnly = true)
  fun listTransfusions(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
  ): ApiResponse<List<TransfusionView>> = ApiResponse.ok(bloodBankService.listTransfusions(patientId))

  @PostMapping("/transfusions/{id}/reactions")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("bloodbank:write:bloodbank")
  @Transactional
  fun reportReaction(
    @PathVariable id: UUID,
    @RequestBody dto: ReactionCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ReactionView> = ApiResponse.ok(bloodBankService.reportReaction(id, dto, principal.userId))

  @GetMapping("/transfusions/{id}/reactions")
  @RequiresPermission("bloodbank:read:bloodbank")
  @Transactional(readOnly = true)
  fun listReactions(
    @PathVariable id: UUID,
  ): ApiResponse<List<ReactionView>> = ApiResponse.ok(bloodBankService.listReactions(id))
}
