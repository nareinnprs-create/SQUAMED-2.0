package com.squamed.core.interop.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.interop.fhir.FhirOutboundService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/fhir")
class FhirOutboundController(
  private val service: FhirOutboundService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping("/export/{patientId}")
  @RequiresPermission("integration:export:fhir")
  fun export(
    @PathVariable patientId: UUID,
    @RequestParam(name = "target", required = false) target: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.exportPatient(tenantResolver.pinOrDefault(principal, null), patientId, target, principal.userId))

  @GetMapping("/exports")
  @RequiresPermission("integration:read:integration")
  fun history(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.history(tenantResolver.pinOrDefault(principal, null)))
}
