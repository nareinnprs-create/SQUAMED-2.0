package com.squamed.core.laboratory.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.laboratory.service.LabService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
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

  @GetMapping("/phlebotomy/worklist")
  @RequiresPermission("laboratory:collect:lab")
  @Transactional(readOnly = true)
  fun phlebotomyWorklist(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<LabOrderView>> = ApiResponse.ok(labService.phlebotomyWorklist(hospitalId = principal.hospitalId))

  @PostMapping("/orders/{id}/collect")
  @RequiresPermission("laboratory:collect:lab")
  @Transactional
  fun collectSpecimen(
    @PathVariable id: UUID,
    @RequestBody dto: CollectRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<LabOrderDetailView> = ApiResponse.ok(labService.collectSpecimen(id, dto.collectedBy ?: principal.userId))

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
  ): ApiResponse<LabOrderDetailView> =
    ApiResponse.ok(
      labService.enterResults(id, dto.results, enteredBy = principal.userId, enteredVia = dto.enteredVia ?: "MANUAL"),
    )

  @PostMapping("/orders/{id}/verify")
  @RequiresPermission("laboratory:verify:lab")
  @Transactional
  fun verifyOrder(
    @PathVariable id: UUID,
    @RequestBody(required = false) dto: VerifyLabOrderDto?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<LabOrderDetailView> = ApiResponse.ok(labService.verifyOrder(id, principal.userId, dto?.overrideNote))

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
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<LabOrderDetailView> = ApiResponse.ok(labService.rejectOrder(id, dto.reason, rejectedBy = principal.userId, recollect = dto.recollect))

  @GetMapping("/critical-results")
  @RequiresPermission("laboratory:read:lab")
  @Transactional(readOnly = true)
  fun listCriticalResults(
    @RequestParam(required = false) status: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<CriticalResultView>> = ApiResponse.ok(labService.listCriticalResults(principal.hospitalId, status))

  @GetMapping("/critical-results/{id}/acks")
  @RequiresPermission("laboratory:read:lab")
  @Transactional(readOnly = true)
  fun listCriticalResultAcks(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<CriticalResultAckView>> = ApiResponse.ok(labService.listCriticalResultAcks(id, principal.hospitalId))

  @PostMapping("/critical-results/{id}/ack")
  @RequiresPermission("laboratory:verify:lab")
  @Transactional
  fun acknowledgeCriticalResult(
    @PathVariable id: UUID,
    @RequestBody(required = false) dto: AckCriticalResultDto?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<CriticalResultView> = ApiResponse.ok(labService.acknowledgeCriticalResult(id, principal.hospitalId, principal.userId, dto?.note))

  @PostMapping("/qc-runs")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("laboratory:write:lab")
  @Transactional
  fun createQcRun(
    @RequestBody dto: CreateQcRunDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<QcRunView> = ApiResponse.ok(labService.createQcRun(dto, tenantResolver.pinOrDefault(principal, null), principal.userId))

  @GetMapping("/qc-runs")
  @RequiresPermission("laboratory:read:lab")
  @Transactional(readOnly = true)
  fun listQcRuns(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<QcRunView>> = ApiResponse.ok(labService.listQcRuns(principal.hospitalId))

  // ---- 02.04 Lab reports + delivery ----

  @PostMapping("/orders/{id}/report")
  @RequiresPermission("laboratory:verify:lab")
  @Transactional
  fun publishReport(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<LabReportView> = ApiResponse.ok(labService.publishReport(id, principal.userId))

  @GetMapping("/orders/{id}/report")
  @RequiresPermission("laboratory:read:lab")
  @Transactional(readOnly = true)
  fun downloadReport(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ResponseEntity<ByteArray> {
    val download = labService.getReportBytes(id, principal.hospitalId)
    val filename = "${download.orderNumber}-v${download.reportVersion}.pdf"
    return ResponseEntity
      .ok()
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
      .contentType(MediaType.APPLICATION_PDF)
      .body(download.bytes)
  }

  @GetMapping("/orders/{id}/reports")
  @RequiresPermission("laboratory:read:lab")
  @Transactional(readOnly = true)
  fun listReports(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<LabReportView>> = ApiResponse.ok(labService.listReports(id, principal.hospitalId))

  @PostMapping("/reports/{reportId}/deliver")
  @RequiresPermission("laboratory:verify:lab")
  @Transactional
  fun deliverReport(
    @PathVariable reportId: UUID,
    @RequestBody dto: DeliverReportDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<LabReportView> = ApiResponse.ok(labService.deliverReport(reportId, dto, principal.userId, principal.hospitalId))

  @PostMapping("/orders/{id}/results/{testRequestId}/amend")
  @RequiresPermission("laboratory:verify:lab")
  @Transactional
  fun amendResult(
    @PathVariable id: UUID,
    @PathVariable testRequestId: UUID,
    @RequestBody dto: AmendResultDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<LabOrderDetailView> = ApiResponse.ok(labService.amendResult(id, testRequestId, dto, principal.userId))

  @GetMapping("/orders/{id}/amendments")
  @RequiresPermission("laboratory:read:lab")
  @Transactional(readOnly = true)
  fun listAmendments(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<LabResultAmendmentView>> = ApiResponse.ok(labService.listAmendments(id, principal.hospitalId))

  @PostMapping("/reference-ranges")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("laboratory:verify:lab")
  @Transactional
  fun createReferenceRange(
    @RequestBody dto: CreateReferenceRangeDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ReferenceRangeView> = ApiResponse.ok(labService.createReferenceRange(dto, tenantResolver.pinOrDefault(principal, dto.hospitalId)))

  @GetMapping("/reference-ranges")
  @RequiresPermission("laboratory:read:lab")
  @Transactional(readOnly = true)
  fun listReferenceRanges(
    @RequestParam(required = false) testCode: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<ReferenceRangeView>> = ApiResponse.ok(labService.listReferenceRanges(testCode, principal.hospitalId))

  @GetMapping("/tat")
  @RequiresPermission("laboratory:read:lab")
  @Transactional(readOnly = true)
  fun tatAnalytics(
    @RequestParam(required = false, defaultValue = "30") days: Int,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<TatRowView>> = ApiResponse.ok(labService.tatAnalytics(principal.hospitalId, days))
}
