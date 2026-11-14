package com.squamed.core.task.api

import com.squamed.core.task.domain.Task
import com.squamed.core.task.domain.TaskLink
import java.time.Instant
import java.util.UUID

data class TaskLinkDto(
  val subjectType: String? = null,
  val subjectId: UUID? = null,
)

data class TaskCreateDto(
  val hospitalId: UUID? = null,
  val title: String? = null,
  val description: String? = null,
  val assigneeId: UUID? = null,
  val assignerId: UUID? = null,
  val priority: String? = null,
  val dueAt: Instant? = null,
  val relatedType: String? = null,
  val relatedId: UUID? = null,
  val links: List<TaskLinkDto>? = null,
)

data class TaskUpdateDto(
  val title: String? = null,
  val description: String? = null,
  val assigneeId: UUID? = null,
  val priority: String? = null,
  val dueAt: Instant? = null,
  val relatedType: String? = null,
  val relatedId: UUID? = null,
  val links: List<TaskLinkDto>? = null,
)

data class TaskStatusDto(
  val status: String? = null,
)

data class TaskAssignDto(
  val assigneeId: UUID? = null,
)

data class TaskEscalateDto(
  val reason: String? = null,
)

data class TaskView(
  val id: UUID?,
  val hospitalId: UUID?,
  val title: String,
  val description: String?,
  val assigneeId: UUID?,
  val assignerId: UUID?,
  val status: String,
  val priority: String,
  val dueAt: Instant?,
  val relatedType: String?,
  val relatedId: UUID?,
  val completedAt: Instant?,
  val escalatedAt: Instant?,
  val escalationCount: Int,
  val links: List<TaskLinkDto>,
  val createdAt: Instant?,
  val updatedAt: Instant?,
) {
  companion object {
    fun from(
      entity: Task,
      links: List<TaskLink> = emptyList(),
    ): TaskView =
      TaskView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        title = entity.title,
        description = entity.description,
        assigneeId = entity.assigneeId,
        assignerId = entity.assignerId,
        status = entity.status,
        priority = entity.priority,
        dueAt = entity.dueAt,
        relatedType = entity.relatedType,
        relatedId = entity.relatedId,
        completedAt = entity.completedAt,
        escalatedAt = entity.escalatedAt,
        escalationCount = entity.escalationCount,
        links =
          links
            .sortedWith(compareBy({ it.subjectType }, { it.subjectId }))
            .map { TaskLinkDto(subjectType = it.subjectType, subjectId = it.subjectId) },
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
      )
  }
}
