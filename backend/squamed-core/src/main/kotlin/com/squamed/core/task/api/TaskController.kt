package com.squamed.core.task.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.task.service.TaskService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tasks")
class TaskController(
  private val taskService: TaskService,
  private val tenantResolver: TenantResolver,
) {
  @GetMapping
  @RequiresPermission("task:manage:task")
  @Transactional(readOnly = true)
  fun listTasks(
    @RequestParam(name = "assigneeId", required = false) assigneeId: UUID?,
    @RequestParam(name = "status", required = false) status: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<TaskView>> = ApiResponse.ok(taskService.listTasks(assigneeId, status, tenantResolver.pin(principal, null)))

  @GetMapping("/overdue")
  @RequiresPermission("task:manage:task")
  @Transactional(readOnly = true)
  fun overdueTasks(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<TaskView>> = ApiResponse.ok(taskService.overdueTasks(tenantResolver.pin(principal, null)))

  @GetMapping("/subject")
  @RequiresPermission("task:manage:task")
  @Transactional(readOnly = true)
  fun tasksForSubject(
    @RequestParam(name = "subjectType") subjectType: String,
    @RequestParam(name = "subjectId") subjectId: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<TaskView>> = ApiResponse.ok(taskService.tasksForSubject(subjectType, subjectId, tenantResolver.pin(principal, null)))

  /** Monitor pass on demand (the scheduled job runs the same scan; chunk item 9). */
  @PostMapping("/sla/scan")
  @RequiresPermission("task:manage:task")
  @Transactional
  fun runSlaScan(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(taskService.scanAndEscalate(principal.userId))

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("task:manage:task")
  @Transactional
  fun createTask(
    @RequestBody dto: TaskCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<TaskView> =
    ApiResponse.ok(taskService.createTask(dto.copy(hospitalId = tenantResolver.pinOrDefault(principal, dto.hospitalId)), principal.userId))

  @GetMapping("/{id}")
  @RequiresPermission("task:manage:task")
  @Transactional(readOnly = true)
  fun getTask(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<TaskView> = ApiResponse.ok(taskService.getTask(id, tenantResolver.pin(principal, null)))

  @PutMapping("/{id}")
  @RequiresPermission("task:manage:task")
  @Transactional
  fun updateTask(
    @PathVariable id: UUID,
    @RequestBody dto: TaskUpdateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<TaskView> = ApiResponse.ok(taskService.updateTask(id, dto, principal.userId, tenantResolver.pin(principal, null)))

  @PatchMapping("/{id}/status")
  @RequiresPermission("task:manage:task")
  @Transactional
  fun updateTaskStatus(
    @PathVariable id: UUID,
    @RequestBody dto: TaskStatusDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<TaskView> =
    ApiResponse.ok(
      taskService.setStatus(id, dto.status ?: throw IllegalArgumentException("TASK_STATUS_REQUIRED"), principal.userId, tenantResolver.pin(principal, null)),
    )

  @PostMapping("/{id}/assign")
  @RequiresPermission("task:manage:task")
  @Transactional
  fun assignTask(
    @PathVariable id: UUID,
    @RequestBody dto: TaskAssignDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<TaskView> =
    ApiResponse.ok(
      taskService.assignTask(
        id,
        dto.assigneeId ?: throw IllegalArgumentException("TASK_ASSIGNEE_REQUIRED"),
        principal.userId,
        tenantResolver.pin(principal, null),
      ),
    )

  /** Chunk API `escalate`: manual escalation + notification fan-out (task.escalated event). */
  @PostMapping("/{id}/escalate")
  @RequiresPermission("task:manage:task")
  @Transactional
  fun escalateTask(
    @PathVariable id: UUID,
    @RequestBody dto: TaskEscalateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<TaskView> = ApiResponse.ok(taskService.escalateTask(id, principal.userId, dto.reason, tenantResolver.pin(principal, null)))
}
