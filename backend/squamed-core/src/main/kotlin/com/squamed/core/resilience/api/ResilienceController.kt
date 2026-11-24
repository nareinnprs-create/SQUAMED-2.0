package com.squamed.core.resilience.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.resilience.service.DegradedModeService
import com.squamed.core.resilience.service.RetentionService
import com.squamed.core.resilience.service.SandboxService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/resilience")
class ResilienceController(
  private val degradedService: DegradedModeService,
  private val tenantResolver: TenantResolver,
) {
  @GetMapping("/degraded")
  @RequiresPermission("resilience:read:degraded")
  fun state(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DegradedStateView> = ApiResponse.ok(degradedService.state(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/degraded/activate")
  @RequiresPermission("resilience:write:degraded")
  fun activate(
    @RequestBody dto: DegradedActivateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DegradedStateView> = ApiResponse.ok(degradedService.activate(tenantResolver.pinOrDefault(principal, null), dto.reason, principal.userId))

  @PostMapping("/degraded/release")
  @RequiresPermission("resilience:write:degraded")
  fun release(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DegradedStateView> = ApiResponse.ok(degradedService.release(tenantResolver.pinOrDefault(principal, null), principal.userId))

  @PostMapping("/degraded/check")
  @RequiresPermission("resilience:read:degraded")
  fun check(
    @RequestBody dto: DegradedCheckDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DegradedCheckView> =
    ApiResponse.ok(degradedService.check(tenantResolver.pinOrDefault(principal, null), dto.module, dto.operation, dto.appendOnly))

  @PostMapping("/degraded/queue")
  @RequiresPermission("resilience:write:degraded")
  fun enqueue(
    @RequestBody dto: QueueWriteDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<QueuedWriteView> = ApiResponse.ok(degradedService.enqueue(tenantResolver.pinOrDefault(principal, null), dto, principal.userId))

  @GetMapping("/degraded/queue")
  @RequiresPermission("resilience:read:degraded")
  fun queued(
    @RequestParam(name = "status", required = false) status: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<QueuedWriteView>> = ApiResponse.ok(degradedService.queued(tenantResolver.pinOrDefault(principal, null), status))

  @PostMapping("/degraded/replay")
  @RequiresPermission("resilience:write:degraded")
  fun replay(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ReplayResultView> = ApiResponse.ok(degradedService.replay(tenantResolver.pinOrDefault(principal, null), principal.userId))
}

@RestController
@RequestMapping("/api/v1/governance")
class GovernanceController(
  private val retentionService: RetentionService,
  private val sandboxService: SandboxService,
) {
  @PostMapping("/retention/policies")
  @RequiresPermission("governance:write:retention")
  fun upsertPolicy(
    @RequestBody dto: RetentionPolicyDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<RetentionPolicyView> = ApiResponse.ok(retentionService.upsertPolicy(dto, principal.userId))

  @GetMapping("/retention/policies")
  @RequiresPermission("governance:read:retention")
  fun policies(): ApiResponse<List<RetentionPolicyView>> = ApiResponse.ok(retentionService.policies())

  @PostMapping("/legal-holds")
  @RequiresPermission("governance:write:retention")
  fun placeHold(
    @RequestBody dto: LegalHoldDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<LegalHoldView> = ApiResponse.ok(retentionService.placeHold(dto, principal.userId))

  @PostMapping("/legal-holds/{id}/release")
  @RequiresPermission("governance:write:retention")
  fun releaseHold(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<LegalHoldView> = ApiResponse.ok(retentionService.releaseHold(id, principal.userId))

  @PostMapping("/erasure")
  @RequiresPermission("governance:write:retention")
  fun requestErasure(
    @RequestBody dto: ErasureDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ErasureView> = ApiResponse.ok(retentionService.requestErasure(dto, principal.userId))

  @GetMapping("/erasure")
  @RequiresPermission("governance:read:retention")
  fun erasures(): ApiResponse<List<ErasureView>> = ApiResponse.ok(retentionService.erasures())

  @PostMapping("/sandbox/hospitals/{id}/enable")
  @RequiresPermission("governance:write:retention")
  fun enableSandbox(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(mapOf("sandbox" to sandboxService.enable(id, principal.userId)))

  @PostMapping("/sandbox/hospitals/{id}/seed")
  @RequiresPermission("governance:write:retention")
  fun seedSandbox(
    @PathVariable id: UUID,
    @RequestBody dto: SandboxSeedDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<SandboxSeedView> = ApiResponse.ok(sandboxService.seed(id, dto.patients, principal.userId))
}
