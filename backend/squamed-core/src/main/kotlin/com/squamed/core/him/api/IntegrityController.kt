package com.squamed.core.him.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.him.service.HimService
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// 03.05 revenue integrity engine: daily scan over the ledger vs bills, findings triaged with audit.
@RestController
@RequestMapping("/api/v1/integrity")
class IntegrityController(
  private val himService: HimService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping("/scan")
  @RequiresPermission("integrity:scan:billing")
  @Transactional
  fun scan(
    @RequestParam(name = "unbilledHours", required = false) unbilledHours: Long?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<IntegrityScanResultView> =
    ApiResponse.ok(
      himService.scan(
        principal.hospitalId ?: tenantResolver.primaryHospitalId(),
        unbilledHours ?: 24,
        principal,
      ),
    )

  @GetMapping("/findings")
  @RequiresPermission("integrity:triage:finding")
  @Transactional(readOnly = true)
  fun listFindings(
    @RequestParam(name = "status", required = false) status: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<IntegrityFindingView>> = ApiResponse.ok(himService.listFindings(principal.hospitalId, status))

  @PostMapping("/findings/{id}/triage")
  @RequiresPermission("integrity:triage:finding")
  @Transactional
  fun triage(
    @PathVariable id: UUID,
    @RequestBody dto: TriageDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<IntegrityFindingView> = ApiResponse.ok(himService.triageFinding(id, dto, principal))
}
