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
  ): ApiResponse<List<TaskView>> = ApiResponse.ok(taskService.listTasks(assigneeId, status))

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
  ): ApiResponse<TaskView> = ApiResponse.ok(taskService.getTask(id))

  @PutMapping("/{id}")
  @RequiresPermission("task:manage:task")
  @Transactional
  fun updateTask(
    @PathVariable id: UUID,
    @RequestBody dto: TaskUpdateDto,
  ): ApiResponse<TaskView> = ApiResponse.ok(taskService.updateTask(id, dto))

  @PatchMapping("/{id}/status")
  @RequiresPermission("task:manage:task")
  @Transactional
  fun updateTaskStatus(
    @PathVariable id: UUID,
    @RequestBody dto: TaskStatusDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<TaskView> = ApiResponse.ok(taskService.setStatus(id, dto.status ?: throw IllegalArgumentException("TASK_STATUS_REQUIRED"), principal.userId))

  @PostMapping("/{id}/assign")
  @RequiresPermission("task:manage:task")
  @Transactional
  fun assignTask(
    @PathVariable id: UUID,
    @RequestBody dto: TaskAssignDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<TaskView> =
    ApiResponse.ok(
      taskService.assignTask(id, dto.assigneeId ?: throw IllegalArgumentException("TASK_ASSIGNEE_REQUIRED"), principal.userId),
    )
}
