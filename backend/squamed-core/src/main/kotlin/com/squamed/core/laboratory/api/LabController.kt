package com.squamed.core.laboratory.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.laboratory.service.LabService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/lab")
class LabController(
  private val labService: LabService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping("/orders")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("laboratory:write:lab")
  @Transactional
  fun createOrder(
    @RequestBody dto: CreateLabOrderDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<LabOrderDetailView> =
    ApiResponse.ok(
      labService.createOrder(
        patientId = dto.patientId,
        encounterId = dto.encounterId,
        dto = dto,
        hospitalId = tenantResolver.pinOrDefault(principal, dto.hospitalId),
      ),
    )

  @GetMapping("/orders")
  @RequiresPermission("laboratory:read:lab")
  @Transactional(readOnly = true)
  fun listOrders(
    @RequestParam(required = false) patientId: UUID?,
    @RequestParam(required = false) status: String?,
    @RequestParam(required = false) resultStatus: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<LabOrderView>> =
    ApiResponse.ok(labService.listOrders(patientId = patientId, status = status, resultStatus = resultStatus, hospitalId = principal.hospitalId))

  @GetMapping("/orders/{id}")
  @RequiresPermission("laboratory:read:lab")
  @Transactional(readOnly = true)
  fun getOrder(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<LabOrderDetailView> = ApiResponse.ok(labService.getOrder(id, principal.hospitalId))

  @PostMapping("/orders/{id}/collect")
  @RequiresPermission("laboratory:collect:lab")
  @Transactional
  fun collectSpecimen(
    @PathVariable id: UUID,
    @RequestBody dto: CollectRequestDto,
  ): ApiResponse<LabOrderDetailView> = ApiResponse.ok(labService.collectSpecimen(id, dto.collectedBy))

  @PostMapping("/orders/{id}/receive")
  @RequiresPermission("laboratory:write:lab")
  @Transactional
  fun receiveSpecimen(
    @PathVariable id: UUID,
  ): ApiResponse<LabOrderDetailView> = ApiResponse.ok(labService.receiveSpecimen(id))

  @PostMapping("/orders/{id}/start")
  @RequiresPermission("laboratory:write:lab")
  @Transactional
  fun startProcessing(
    @PathVariable id: UUID,
  ): ApiResponse<LabOrderDetailView> = ApiResponse.ok(labService.startProcessing(id))

  @PostMapping("/orders/{id}/results")
  @RequiresPermission("laboratory:write:lab")
  @Transactional
  fun enterResults(
    @PathVariable id: UUID,
    @RequestBody dto: EnterResultsDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<LabOrderDetailView> = ApiResponse.ok(labService.enterResults(id, dto.results, enteredBy = principal.userId))

  @PostMapping("/orders/{id}/verify")
  @RequiresPermission("laboratory:verify:lab")
  @Transactional
  fun verifyOrder(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<LabOrderDetailView> = ApiResponse.ok(labService.verifyOrder(id, principal.userId))

  @PostMapping("/orders/{id}/release")
  @RequiresPermission("laboratory:verify:lab")
  @Transactional
  fun releaseOrder(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<LabOrderDetailView> = ApiResponse.ok(labService.releaseOrder(id, principal.userId))

  @PostMapping("/orders/{id}/reject")
  @RequiresPermission("laboratory:write:lab")
  @Transactional
  fun rejectOrder(
    @PathVariable id: UUID,
    @RequestBody dto: RejectRequestDto,
  ): ApiResponse<LabOrderDetailView> = ApiResponse.ok(labService.rejectOrder(id, dto.reason))
}
