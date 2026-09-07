package com.squamed.core.task.api

import com.squamed.core.task.domain.Task
import java.time.Instant
import java.util.UUID

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
)

data class TaskUpdateDto(
  val title: String? = null,
  val description: String? = null,
  val assigneeId: UUID? = null,
  val priority: String? = null,
  val dueAt: Instant? = null,
  val relatedType: String? = null,
  val relatedId: UUID? = null,
)

data class TaskStatusDto(
  val status: String? = null,
)

data class TaskAssignDto(
  val assigneeId: UUID? = null,
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
  val createdAt: Instant?,
  val updatedAt: Instant?,
) {
  companion object {
    fun from(entity: Task) =
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
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
      )
  }
}
