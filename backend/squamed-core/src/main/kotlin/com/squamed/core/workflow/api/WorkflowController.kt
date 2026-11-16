package com.squamed.core.workflow.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.workflow.service.WorkflowService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/workflow")
class WorkflowController(
  private val workflowService: WorkflowService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping("/definitions")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("workflow:manage:workflow")
  @Transactional
  fun createDefinition(
    @RequestBody dto: CreateWorkflowDefinitionRequest,
  ): ApiResponse<WorkflowDefinitionDto> = ApiResponse.ok(workflowService.createDefinition(dto))

  @GetMapping("/definitions")
  @RequiresPermission("workflow:read:workflow")
  @Transactional(readOnly = true)
  fun listDefinitions(): ApiResponse<List<WorkflowDefinitionDto>> = ApiResponse.ok(workflowService.listDefinitions())

  @GetMapping("/definitions/{id}")
  @RequiresPermission("workflow:read:workflow")
  @Transactional(readOnly = true)
  fun getDefinition(
    @PathVariable id: UUID,
  ): ApiResponse<WorkflowDefinitionDto> = ApiResponse.ok(workflowService.getDefinition(id))

  @PatchMapping("/definitions/{id}")
  @RequiresPermission("workflow:manage:workflow")
  @Transactional
  fun updateDefinition(
    @PathVariable id: UUID,
    @RequestBody dto: CreateWorkflowDefinitionRequest,
  ): ApiResponse<WorkflowDefinitionDto> = ApiResponse.ok(workflowService.updateDefinition(id, dto))

  @PostMapping("/definitions/{id}/activate")
  @RequiresPermission("workflow:manage:workflow")
  @Transactional
  fun activateDefinition(
    @PathVariable id: UUID,
  ): ApiResponse<WorkflowDefinitionDto> = ApiResponse.ok(workflowService.activateDefinition(id))

  @PostMapping("/definitions/{id}/deactivate")
  @RequiresPermission("workflow:manage:workflow")
  @Transactional
  fun deactivateDefinition(
    @PathVariable id: UUID,
  ): ApiResponse<WorkflowDefinitionDto> = ApiResponse.ok(workflowService.deactivateDefinition(id))

  @PostMapping("/instances")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("workflow:manage:workflow")
  @Transactional
  fun triggerWorkflow(
    @RequestBody dto: TriggerWorkflowRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<WorkflowInstanceView> = ApiResponse.ok(workflowService.trigger(dto, principal.userId, tenantResolver.pinOrDefault(principal, null)))

  @GetMapping("/instances")
  @RequiresPermission("workflow:read:workflow")
  @Transactional(readOnly = true)
  fun listInstances(
    @RequestParam(name = "status", required = false) status: String?,
    // 00.04 tenant sweep: instance reads are pinned to the caller's hospital (null = admin).
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<WorkflowInstanceView>> = ApiResponse.ok(workflowService.listInstances(status, principal.hospitalId))

  @GetMapping("/instances/{id}")
  @RequiresPermission("workflow:read:workflow")
  @Transactional(readOnly = true)
  fun getInstance(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<WorkflowInstanceView> = ApiResponse.ok(workflowService.getInstance(id, principal.hospitalId))

  @PostMapping("/instances/{id}/approvals/{approvalId}/decide")
  @RequiresPermission("workflow:approve:workflow")
  @Transactional
  fun decideApproval(
    @PathVariable id: UUID,
    @PathVariable approvalId: UUID,
    @RequestBody dto: DecideApprovalRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<WorkflowInstanceView> = ApiResponse.ok(workflowService.decideApproval(id, approvalId, dto, principal.userId, force = true))

  @PostMapping("/instances/{id}/resume")
  @RequiresPermission("workflow:manage:workflow")
  @Transactional
  fun resume(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<WorkflowInstanceView> = ApiResponse.ok(workflowService.resume(id, principal.userId))

  @GetMapping("/approvals/pending")
  @RequiresPermission("workflow:read:workflow")
  @Transactional(readOnly = true)
  fun pendingApprovals(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<WorkflowPendingApprovalView>> = ApiResponse.ok(workflowService.pendingApprovals(principal.userId))
}
