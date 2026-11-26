package com.squamed.core.bi.api

import com.squamed.core.bi.service.BiService
import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/bi")
class BiController(
  private val service: BiService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping("/sync")
  @RequiresPermission("bi:write:bi")
  fun sync(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.sync(tenantResolver.pinOrDefault(principal, null), principal.userId))

  @GetMapping("/kpis")
  @RequiresPermission("bi:read:bi")
  fun kpis(
    @RequestParam(name = "period") period: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.kpis(tenantResolver.pinOrDefault(principal, null), period))

  @GetMapping("/dashboards/{role}")
  @RequiresPermission("bi:read:bi")
  fun dashboard(
    @PathVariable role: String,
    @RequestParam(name = "period") period: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.dashboard(tenantResolver.pinOrDefault(principal, null), role, period))

  @GetMapping("/exports")
  @RequiresPermission("bi:read:bi")
  fun export(
    @RequestParam(name = "period") period: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ResponseEntity<String> =
    ResponseEntity
      .ok()
      .contentType(MediaType.parseMediaType("text/csv"))
      .body(service.exportCsv(tenantResolver.pinOrDefault(principal, null), period))

  @GetMapping("/reconcile")
  @RequiresPermission("bi:read:bi")
  fun reconcile(
    @RequestParam(name = "period") period: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.reconcile(tenantResolver.pinOrDefault(principal, null), period))
}
