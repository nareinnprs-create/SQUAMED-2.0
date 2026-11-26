package com.squamed.core.migration.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.migration.service.BenchmarkService
import com.squamed.core.migration.service.EsgService
import com.squamed.core.migration.service.MigrationJobDetailView
import com.squamed.core.migration.service.MigrationService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

data class MigrationJobRequest(
  val sourceType: String,
  val rows: List<Map<String, Any?>>,
)

data class BenchmarkParticipationRequest(
  val enabled: Boolean,
)

data class EsgMetricRequest(
  val period: String,
  val metricType: String,
  val value: BigDecimal,
)

@RestController
@RequestMapping("/api/v1/migration")
class MigrationController(
  private val service: MigrationService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping("/jobs")
  @RequiresPermission("migration:write:migration")
  fun create(
    @RequestBody dto: MigrationJobRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<MigrationJobDetailView> =
    ApiResponse.ok(service.createJob(tenantResolver.pinOrDefault(principal, null), dto.sourceType, dto.rows, principal.userId))

  @GetMapping("/jobs")
  @RequiresPermission("migration:read:migration")
  fun jobs(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.jobs(tenantResolver.pinOrDefault(principal, null)))

  @GetMapping("/jobs/{id}")
  @RequiresPermission("migration:read:migration")
  fun detail(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<MigrationJobDetailView> = ApiResponse.ok(service.detail(id, tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/jobs/{id}/import")
  @RequiresPermission("migration:write:migration")
  fun import(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<MigrationJobDetailView> = ApiResponse.ok(service.importJob(id, tenantResolver.pinOrDefault(principal, null), principal.userId))

  @PostMapping("/jobs/{id}/rollback")
  @RequiresPermission("migration:write:migration")
  fun rollback(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<MigrationJobDetailView> = ApiResponse.ok(service.rollback(id, tenantResolver.pinOrDefault(principal, null), principal.userId))
}

@RestController
@RequestMapping("/api/v1/benchmark")
class BenchmarkController(
  private val service: BenchmarkService,
  private val tenantResolver: TenantResolver,
) {
  @PutMapping("/participation")
  @RequiresPermission("benchmark:write:benchmark")
  fun setParticipation(
    @RequestBody dto: BenchmarkParticipationRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.setParticipation(tenantResolver.pinOrDefault(principal, null), dto.enabled, principal.userId))

  @GetMapping("/participation")
  @RequiresPermission("benchmark:read:benchmark")
  fun participation(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.participation(tenantResolver.pinOrDefault(principal, null)))

  @GetMapping("/summary")
  @RequiresPermission("benchmark:read:benchmark")
  fun summary(
    @RequestParam(name = "metric") metric: String,
    @RequestParam(name = "period") period: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.summary(tenantResolver.pinOrDefault(principal, null), metric, period))
}

@RestController
@RequestMapping("/api/v1/esg")
class EsgController(
  private val service: EsgService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping("/metrics")
  @RequiresPermission("esg:write:esg")
  fun recordMetric(
    @RequestBody dto: EsgMetricRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      service.recordMetric(
        tenantResolver.pinOrDefault(principal, null),
        dto.period,
        dto.metricType,
        dto.value,
        principal.userId,
      ),
    )

  @GetMapping("/metrics")
  @RequiresPermission("esg:read:esg")
  fun metrics(
    @RequestParam(name = "period") period: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.metrics(tenantResolver.pinOrDefault(principal, null), period))

  @GetMapping("/summary")
  @RequiresPermission("esg:read:esg")
  fun summary(
    @RequestParam(name = "period") period: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.summary(tenantResolver.pinOrDefault(principal, null), period))
}
