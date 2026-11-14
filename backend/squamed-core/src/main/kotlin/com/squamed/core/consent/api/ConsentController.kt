package com.squamed.core.consent.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.consent.service.ConsentService
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

@RestController
@RequestMapping("/api/v1/consents")
class ConsentController(
  private val consentService: ConsentService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping
  @RequiresPermission("consent:manage:consent")
  @Transactional
  fun capture(
    @RequestBody dto: ConsentCaptureDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ConsentView> =
    ApiResponse.ok(consentService.capture(dto.copy(hospitalId = tenantResolver.pinOrDefault(principal, dto.hospitalId)), principal.userId))

  @GetMapping
  @RequiresPermission("consent:manage:consent")
  @Transactional(readOnly = true)
  fun listForPatient(
    @RequestParam(name = "patientId") patientId: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<ConsentView>> = ApiResponse.ok(consentService.listForPatient(patientId, tenantResolver.pin(principal, null)))

  @GetMapping("/{id}")
  @RequiresPermission("consent:manage:consent")
  @Transactional(readOnly = true)
  fun getConsent(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ConsentView> = ApiResponse.ok(consentService.getConsent(id, tenantResolver.pin(principal, null)))

  /** Chunk item 11 withdrawal: consent.withdrawn event, per-channel gate flips immediately. */
  @PostMapping("/{id}/withdraw")
  @RequiresPermission("consent:manage:consent")
  @Transactional
  fun withdraw(
    @PathVariable id: UUID,
    @RequestBody dto: ConsentWithdrawDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ConsentView> = ApiResponse.ok(consentService.withdraw(id, principal.userId, dto.reason, tenantResolver.pin(principal, null)))
}
