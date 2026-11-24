package com.squamed.core.qms.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.qms.service.QmsService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class IndicatorDto(
  val code: String,
  val name: String,
  val target: BigDecimal,
  val actual: BigDecimal,
  val period: String,
)

data class CapaDto(
  val code: String,
  val title: String,
  val rootCause: String? = null,
  val action: String,
  val dueOn: LocalDate? = null,
)

data class AuditDto(
  val title: String,
  val scheduledOn: LocalDate,
)

data class AuditCompleteDto(
  val findings: String? = null,
)

data class RiskDto(
  val title: String,
  val likelihood: Int,
  val impact: Int,
  val owner: String? = null,
)

data class InfectionDto(
  val patientId: UUID,
  val infectionType: String,
  val organism: String? = null,
  val identifiedOn: LocalDate,
)

data class OutbreakDto(
  val code: String,
  val pathogen: String,
)

data class HandHygieneDto(
  val wardId: UUID? = null,
  val period: String,
  val opportunities: Int,
  val compliant: Int,
)

data class LitigationHoldDto(
  val caseRef: String,
  val subjectType: String,
  val subjectId: UUID,
  val reason: String,
)

@RestController
@RequestMapping("/api/v1/qms")
class QmsController(
  private val qmsService: QmsService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping("/indicators")
  @RequiresPermission("qms:write:qms")
  fun upsertIndicator(
    @RequestBody dto: IndicatorDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      qmsService.upsertIndicator(
        tenantResolver.pinOrDefault(principal, null),
        dto.code,
        dto.name,
        dto.target,
        dto.actual,
        dto.period,
        principal.userId,
      ),
    )

  @GetMapping("/indicators")
  @RequiresPermission("qms:read:qms")
  fun indicators(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(qmsService.indicators(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/capa")
  @RequiresPermission("qms:write:qms")
  fun createCapa(
    @RequestBody dto: CapaDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      qmsService.createCapa(
        tenantResolver.pinOrDefault(principal, null),
        dto.code,
        dto.title,
        dto.rootCause,
        dto.action,
        dto.dueOn,
        principal.userId,
      ),
    )

  @GetMapping("/capa")
  @RequiresPermission("qms:read:qms")
  fun capas(
    @RequestParam(name = "status", required = false) status: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(qmsService.capas(tenantResolver.pinOrDefault(principal, null), status))

  @PostMapping("/capa/{id}/close")
  @RequiresPermission("qms:write:qms")
  fun closeCapa(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(qmsService.closeCapa(id, tenantResolver.pinOrDefault(principal, null), principal.userId))

  @PostMapping("/audits")
  @RequiresPermission("qms:write:qms")
  fun createAudit(
    @RequestBody dto: AuditDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(qmsService.createAudit(tenantResolver.pinOrDefault(principal, null), dto.title, dto.scheduledOn, principal.userId))

  @GetMapping("/audits")
  @RequiresPermission("qms:read:qms")
  fun audits(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(qmsService.audits(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/audits/{id}/complete")
  @RequiresPermission("qms:write:qms")
  fun completeAudit(
    @PathVariable id: UUID,
    @RequestBody dto: AuditCompleteDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(qmsService.completeAudit(id, tenantResolver.pinOrDefault(principal, null), dto.findings, principal.userId))

  @PostMapping("/risks")
  @RequiresPermission("qms:write:qms")
  fun createRisk(
    @RequestBody dto: RiskDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(qmsService.createRisk(tenantResolver.pinOrDefault(principal, null), dto.title, dto.likelihood, dto.impact, dto.owner, principal.userId))

  @GetMapping("/risks")
  @RequiresPermission("qms:read:qms")
  fun risks(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(qmsService.risks(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/ipc/infections")
  @RequiresPermission("qms:write:qms")
  fun recordInfection(
    @RequestBody dto: InfectionDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      qmsService.recordInfection(
        tenantResolver.pinOrDefault(principal, null),
        dto.patientId,
        dto.infectionType,
        dto.organism,
        dto.identifiedOn,
        principal.userId,
      ),
    )

  @GetMapping("/ipc/infections")
  @RequiresPermission("qms:read:qms")
  fun infections(
    @RequestParam(name = "status", required = false) status: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(qmsService.infections(tenantResolver.pinOrDefault(principal, null), status))

  @PostMapping("/ipc/infections/{id}/resolve")
  @RequiresPermission("qms:write:qms")
  fun resolveInfection(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(qmsService.resolveInfection(id, tenantResolver.pinOrDefault(principal, null), principal.userId))

  @PostMapping("/ipc/outbreaks")
  @RequiresPermission("qms:write:qms")
  fun declareOutbreak(
    @RequestBody dto: OutbreakDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(qmsService.declareOutbreak(tenantResolver.pinOrDefault(principal, null), dto.code, dto.pathogen, principal.userId))

  @GetMapping("/ipc/outbreaks")
  @RequiresPermission("qms:read:qms")
  fun outbreaks(
    @RequestParam(name = "status", required = false) status: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(qmsService.outbreaks(tenantResolver.pinOrDefault(principal, null), status))

  @PostMapping("/ipc/outbreaks/{id}/close")
  @RequiresPermission("qms:write:qms")
  fun closeOutbreak(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(qmsService.closeOutbreak(id, tenantResolver.pinOrDefault(principal, null), principal.userId))

  @PostMapping("/ipc/hand-hygiene")
  @RequiresPermission("qms:write:qms")
  fun recordHandHygiene(
    @RequestBody dto: HandHygieneDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      qmsService.recordHandHygiene(
        tenantResolver.pinOrDefault(principal, null),
        dto.wardId,
        dto.period,
        dto.opportunities,
        dto.compliant,
        principal.userId,
      ),
    )

  @GetMapping("/ipc/hand-hygiene")
  @RequiresPermission("qms:read:qms")
  fun handHygiene(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(qmsService.handHygiene(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/legal/holds")
  @RequiresPermission("qms:write:qms")
  fun placeHold(
    @RequestBody dto: LitigationHoldDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      qmsService.placeHold(
        tenantResolver.pinOrDefault(principal, null),
        dto.caseRef,
        dto.subjectType,
        dto.subjectId,
        dto.reason,
        principal.userId,
      ),
    )

  @GetMapping("/legal/holds")
  @RequiresPermission("qms:read:qms")
  fun holds(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(qmsService.holds(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/legal/holds/{id}/release")
  @RequiresPermission("qms:write:qms")
  fun releaseHold(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(qmsService.releaseHold(id, tenantResolver.pinOrDefault(principal, null), principal.userId))

  @GetMapping("/summary")
  @RequiresPermission("qms:read:qms")
  fun summary(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(qmsService.summary(tenantResolver.pinOrDefault(principal, null)))
}
