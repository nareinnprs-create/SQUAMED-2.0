package com.squamed.core.him.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.him.service.HimService
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
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

// 03.05 HIM coding worklist + coder queries.
@RestController
@RequestMapping("/api/v1/him")
class HimController(
  private val himService: HimService,
  private val tenantResolver: TenantResolver,
) {
  @GetMapping("/coding-queue")
  @RequiresPermission("him:read:coding")
  @Transactional(readOnly = true)
  fun codingQueue(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<CodingQueueRowView>> = ApiResponse.ok(himService.codingQueue(principal.hospitalId))

  @PostMapping("/encounters/{id}/coding")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("him:code:encounter")
  @Transactional
  fun assignCoding(
    @PathVariable id: UUID,
    @RequestBody dto: CodingAssignRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<CodingAssignmentView> = ApiResponse.ok(himService.assignCoding(id, dto, principal))

  @GetMapping("/coding-assignments")
  @RequiresPermission("him:read:coding")
  @Transactional(readOnly = true)
  fun listAssignments(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<CodingAssignmentView>> = ApiResponse.ok(himService.listAssignments(principal.hospitalId))

  @GetMapping("/coding-audit-sample")
  @RequiresPermission("him:audit:coding")
  @Transactional(readOnly = true)
  fun auditSample(
    @RequestParam(name = "percent", required = false) percent: Int?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<CodingAssignmentView>> = ApiResponse.ok(himService.codingAuditSample(principal.hospitalId, percent ?: 10))

  @PostMapping("/queries")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("him:query:clinician")
  @Transactional
  fun raiseQuery(
    @RequestBody dto: CoderQueryRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<CoderQueryView> = ApiResponse.ok(himService.raiseQuery(dto, principal, tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/queries/{id}/answer")
  @RequiresPermission("him:answer:query")
  @Transactional
  fun answerQuery(
    @PathVariable id: UUID,
    @RequestBody dto: CoderAnswerDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<CoderQueryView> = ApiResponse.ok(himService.answerQuery(id, dto, principal))

  @GetMapping("/queries")
  @RequiresPermission("him:read:coding")
  @Transactional(readOnly = true)
  fun listQueries(
    @RequestParam(name = "status", required = false) status: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<CoderQueryView>> = ApiResponse.ok(himService.listQueries(principal.hospitalId, status))
}
