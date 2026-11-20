package com.squamed.core.radiology.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.radiology.service.RadiologyService
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

// 02.05 RIS-lite: PACS = Orthanc, viewer = OHIF. We own the RIS surfaces they consume.
@RestController
@RequestMapping("/api/v1/radiology")
class RadiologyController(
  private val radiologyService: RadiologyService,
) {
  @PostMapping("/orders")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("radiology:order:study")
  @Transactional
  fun createOrder(
    @RequestBody dto: RadOrderRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<RadOrderView> = ApiResponse.ok(radiologyService.createOrder(dto, principal))

  // Modality worklist feed for Orthanc's MWL channel.
  @GetMapping("/mwl")
  @RequiresPermission("radiology:read:worklist")
  @Transactional(readOnly = true)
  fun mwl(
    @RequestParam(name = "modality") modality: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<MwlRowView>> = ApiResponse.ok(radiologyService.mwl(principal.hospitalId, modality))

  // Radiologist worklist.
  @GetMapping("/worklist")
  @RequiresPermission("radiology:read:worklist")
  @Transactional(readOnly = true)
  fun worklist(
    @RequestParam(name = "status", required = false) status: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<RadOrderView>> = ApiResponse.ok(radiologyService.worklist(principal.hospitalId, status))

  @PostMapping("/orders/{id}/study:complete")
  @RequiresPermission("radiology:order:study")
  @Transactional
  fun completeStudy(
    @PathVariable id: UUID,
    @RequestBody dto: StudyCompleteDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<RadOrderView> = ApiResponse.ok(radiologyService.completeStudy(id, dto, principal))

  @PostMapping("/orders/{id}/reports")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("radiology:draft:report")
  @Transactional
  fun draftReport(
    @PathVariable id: UUID,
    @RequestBody dto: RadiologyReportDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<RadiologyReportView> = ApiResponse.ok(radiologyService.draftReport(id, dto, principal))

  @PutMapping("/reports/{id}")
  @RequiresPermission("radiology:draft:report")
  @Transactional
  fun updateDraft(
    @PathVariable id: UUID,
    @RequestBody dto: RadiologyReportDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<RadiologyReportView> = ApiResponse.ok(radiologyService.updateDraft(id, dto, principal))

  @PostMapping("/reports/{id}/sign")
  @RequiresPermission("radiology:sign:report")
  @Transactional
  fun signReport(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<RadiologyReportView> = ApiResponse.ok(radiologyService.signReport(id, principal))

  @PostMapping("/reports/{id}/amendments")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("radiology:sign:report")
  @Transactional
  fun amendReport(
    @PathVariable id: UUID,
    @RequestBody dto: ReportAmendmentDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<RadiologyReportView> = ApiResponse.ok(radiologyService.amendReport(id, dto, principal))

  @PostMapping("/reports/{id}/critical-findings")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("radiology:sign:report")
  @Transactional
  fun raiseCriticalFinding(
    @PathVariable id: UUID,
    @RequestBody dto: CriticalFindingRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<CriticalFindingView> = ApiResponse.ok(radiologyService.raiseCriticalFinding(id, dto, principal))

  @GetMapping("/critical-findings")
  @RequiresPermission("radiology:read:worklist")
  @Transactional(readOnly = true)
  fun listCriticalFindings(
    @RequestParam(name = "status", required = false) status: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<CriticalFindingView>> = ApiResponse.ok(radiologyService.listCriticalFindings(principal.hospitalId, status))

  @PostMapping("/critical-findings/{id}/ack")
  @RequiresPermission("radiology:ack:critical")
  @Transactional
  fun acknowledgeCriticalFinding(
    @PathVariable id: UUID,
    @RequestBody dto: CriticalAckDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<CriticalFindingView> = ApiResponse.ok(radiologyService.acknowledgeCriticalFinding(id, dto, principal))

  @PostMapping("/studies/{id}/viewer-token")
  @RequiresPermission("radiology:read:worklist")
  @Transactional(readOnly = true)
  fun viewerToken(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<StudyViewerTokenView> = ApiResponse.ok(radiologyService.viewerToken(id, principal))
}
