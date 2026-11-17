package com.squamed.core.receptionqueue.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.receptionqueue.service.VisitRegistrar
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@RestController
@RequestMapping("/api/v1/reception")
class ReceptionController(
  private val visitRegistrar: VisitRegistrar,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping("/visits")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("reception:write:visit")
  @Transactional
  fun createVisit(
    @RequestBody dto: VisitCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<VisitCreateResult> {
    val patientId = dto.patientId ?: throw IllegalArgumentException("PATIENT_ID_REQUIRED")
    return ApiResponse.ok(visitRegistrar.createVisit(patientId, dto, tenantResolver.pinOrDefault(principal, dto.hospitalId)))
  }

  @GetMapping("/visits/{id}")
  @RequiresPermission("reception:read:visit")
  @Transactional(readOnly = true)
  fun getVisit(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<VisitDto> = ApiResponse.ok(visitRegistrar.getVisit(id, principal.hospitalId))

  @GetMapping("/visits")
  @RequiresPermission("reception:read:visit")
  @Transactional(readOnly = true)
  fun listVisits(
    @RequestParam(name = "status", required = false) status: String?,
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
    @RequestParam(name = "from", required = false) from: Instant?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<VisitDto>> {
    val defaultFrom = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant()
    return ApiResponse.ok(visitRegistrar.listVisits(status, patientId, from ?: defaultFrom, principal.hospitalId))
  }

  @PatchMapping("/visits/{id}/check-in")
  @RequiresPermission("reception:write:visit")
  @Transactional
  fun checkIn(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<VisitDto> = ApiResponse.ok(visitRegistrar.checkIn(id, principal.hospitalId))

  @PatchMapping("/visits/{id}/complete")
  @RequiresPermission("reception:write:visit")
  @Transactional
  fun complete(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<VisitDto> = ApiResponse.ok(visitRegistrar.complete(id, principal.hospitalId))

  @PatchMapping("/visits/{id}/cancel")
  @RequiresPermission("reception:write:visit")
  @Transactional
  fun cancelVisit(
    @PathVariable id: UUID,
    @RequestBody dto: VisitCancelDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<VisitDto> = ApiResponse.ok(visitRegistrar.cancelVisit(id, dto.reason, principal.hospitalId))

  // -- 01.03 reception workspace: visitor/attendant passes -------------------------------

  @PostMapping("/visits/{id}/passes")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("reception:write:visit")
  @Transactional
  fun issuePass(
    @PathVariable id: UUID,
    @RequestBody dto: VisitPassIssueDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<VisitPassDto> = ApiResponse.ok(visitRegistrar.issuePass(id, dto, principal.hospitalId))

  @GetMapping("/visits/{id}/passes")
  @RequiresPermission("reception:read:visit")
  @Transactional(readOnly = true)
  fun listPasses(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<VisitPassDto>> = ApiResponse.ok(visitRegistrar.listPasses(id, principal.hospitalId))

  @PatchMapping("/passes/{id}/return")
  @RequiresPermission("reception:write:visit")
  @Transactional
  fun returnPass(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<VisitPassDto> = ApiResponse.ok(visitRegistrar.returnPass(id, principal.hospitalId))

  // -- 01.03 reception workspace: consult-fee receipts -----------------------------------

  @PostMapping("/visits/{id}/receipts")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("reception:write:visit")
  @Transactional
  fun issueReceipt(
    @PathVariable id: UUID,
    @RequestBody dto: ConsultReceiptIssueDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ConsultReceiptDto> = ApiResponse.ok(visitRegistrar.issueReceipt(id, dto, principal.hospitalId))

  @GetMapping("/visits/{id}/receipts")
  @RequiresPermission("reception:read:visit")
  @Transactional(readOnly = true)
  fun listReceipts(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<ConsultReceiptDto>> = ApiResponse.ok(visitRegistrar.listReceipts(id, principal.hospitalId))

  @PatchMapping("/receipts/{id}/void")
  @RequiresPermission("reception:write:visit")
  @Transactional
  fun voidReceipt(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ConsultReceiptDto> = ApiResponse.ok(visitRegistrar.voidReceipt(id, principal.hospitalId))
}
