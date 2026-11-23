package com.squamed.core.ipd.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.ipd.service.AdmissionService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
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
@RequestMapping("/api/v1/admissions")
class AdmissionController(
  private val admissionService: AdmissionService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("ipd:write:admission")
  @Transactional
  fun admit(
    @RequestBody dto: AdmissionCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AdmissionView> = ApiResponse.ok(admissionService.admit(dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId)), principal.userId))

  @GetMapping
  @RequiresPermission("ipd:read:admission")
  @Transactional(readOnly = true)
  fun listAdmissions(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
    @RequestParam(name = "status", required = false) status: String?,
    @RequestParam(name = "wardId", required = false) wardId: UUID?,
    // 00.04 tenant sweep: read paths are pinned to the caller's hospital (null = admin).
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<AdmissionView>> = ApiResponse.ok(admissionService.listAdmissions(patientId, status, wardId, principal.hospitalId))

  @GetMapping("/{id}")
  @RequiresPermission("ipd:read:admission")
  @Transactional(readOnly = true)
  fun getAdmission(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AdmissionView> = ApiResponse.ok(admissionService.getAdmission(id, principal.hospitalId))

  @GetMapping("/{id}/transfers")
  @RequiresPermission("ipd:read:admission")
  @Transactional(readOnly = true)
  fun listTransfers(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<AdmissionTransferView>> = ApiResponse.ok(admissionService.listTransfers(id, principal.hospitalId))

  @PostMapping("/{id}/transfer")
  @RequiresPermission("ipd:write:admission")
  @Transactional
  fun transfer(
    @PathVariable id: UUID,
    @RequestBody dto: AdmissionTransferDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AdmissionView> = ApiResponse.ok(admissionService.transfer(id, dto, principal.userId))

  @PostMapping("/{id}/discharge")
  @RequiresPermission("ipd:write:admission")
  @Transactional
  fun discharge(
    @PathVariable id: UUID,
    @RequestBody dto: AdmissionDischargeDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AdmissionView> = ApiResponse.ok(admissionService.discharge(id, dto, principal.userId))

  @GetMapping("/{id}/discharge-summary")
  @RequiresPermission("ipd:read:admission")
  @Transactional(readOnly = true)
  fun getDischargeSummary(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DischargeSummaryView> = ApiResponse.ok(admissionService.getDischargeSummary(id, principal.hospitalId))

  @PutMapping("/{id}/discharge-summary")
  @RequiresPermission("ipd:write:admission")
  @Transactional
  fun upsertDischargeSummary(
    @PathVariable id: UUID,
    @RequestBody dto: DischargeSummaryUpsertDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DischargeSummaryView> = ApiResponse.ok(admissionService.upsertDischargeSummary(id, dto, principal.userId))

  // 04.01 expected-discharge planning + checklist + summary signature.
  @PostMapping("/{id}/plan-discharge")
  @RequiresPermission("ipd:write:admission")
  @Transactional
  fun planDischarge(
    @PathVariable id: UUID,
    @RequestBody dto: PlanDischargeDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AdmissionView> = ApiResponse.ok(admissionService.planDischarge(id, dto, principal.userId))

  @GetMapping("/{id}/checklist")
  @RequiresPermission("ipd:read:admission")
  @Transactional(readOnly = true)
  fun getChecklist(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DischargeChecklistView> = ApiResponse.ok(admissionService.getChecklist(id, principal.hospitalId))

  @PostMapping("/{id}/checklist/{item}")
  @RequiresPermission("ipd:write:admission")
  @Transactional
  fun completeChecklistItem(
    @PathVariable id: UUID,
    @PathVariable item: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DischargeChecklistView> = ApiResponse.ok(admissionService.completeChecklistItem(id, item, principal.userId))

  @PostMapping("/{id}/discharge-summary/sign")
  @RequiresPermission("ipd:write:admission")
  @Transactional
  fun signSummary(
    @PathVariable id: UUID,
    @RequestBody dto: SummarySignDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DischargeSummaryView> = ApiResponse.ok(admissionService.signSummary(id, dto, principal.userId))

  // 04.08 room charges, interim bills, dietary.
  @PostMapping("/{id}/room-charge")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("ipd:write:admission")
  @Transactional
  fun postRoomCharge(
    @PathVariable id: UUID,
    @RequestBody dto: RoomChargeRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<com.squamed.core.billing.api.ChargeView> = ApiResponse.ok(admissionService.postRoomCharge(id, dto, principal))

  @PostMapping("/{id}/interim-bill")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("ipd:write:admission")
  @Transactional
  fun interimBill(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<com.squamed.core.billing.api.InvoiceDetailView> = ApiResponse.ok(admissionService.interimBill(id, principal))

  @PostMapping("/{id}/diet-orders")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("ipd:write:admission")
  @Transactional
  fun createDietOrder(
    @PathVariable id: UUID,
    @RequestBody dto: DietOrderRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DietOrderView> = ApiResponse.ok(admissionService.createDietOrder(id, dto, principal))

  @GetMapping("/{id}/attendants")
  @RequiresPermission("ipd:read:admission")
  @Transactional(readOnly = true)
  fun listAttendants(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<AttendantView>> = ApiResponse.ok(admissionService.listAttendants(id, principal.hospitalId))

  @PostMapping("/{id}/attendants")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("ipd:write:admission")
  @Transactional
  fun addAttendant(
    @PathVariable id: UUID,
    @RequestBody dto: AttendantCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AttendantView> = ApiResponse.ok(admissionService.addAttendant(id, dto, principal.userId))
}
