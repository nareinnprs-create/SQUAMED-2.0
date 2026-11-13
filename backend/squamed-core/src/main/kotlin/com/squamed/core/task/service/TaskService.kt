package com.squamed.core.task.service

import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.task.api.TaskCreateDto
import com.squamed.core.task.api.TaskUpdateDto
import com.squamed.core.task.api.TaskView
import com.squamed.core.task.domain.Task
import com.squamed.core.task.repository.TaskRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class TaskService(
  private val taskRepository: TaskRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun createTask(
    dto: TaskCreateDto,
    creatorId: UUID,
  ): TaskView {
    val task =
      taskRepository
        .save(
          Task(
            hospitalId = dto.hospitalId,
            title = dto.title ?: throw IllegalArgumentException("TASK_TITLE_REQUIRED"),
            description = dto.description,
            assigneeId = dto.assigneeId,
            assignerId = dto.assignerId ?: creatorId,
            status = "pending",
            priority = dto.priority ?: "normal",
            dueAt = dto.dueAt,
            relatedType = dto.relatedType,
            relatedId = dto.relatedId,
          ),
        ).also { it.createdBy = creatorId }
    val taskId = task.id ?: throw IllegalStateException("TASK_ID_MISSING")
    val recipients = listOfNotNull(task.assigneeId)
    if (recipients.isNotEmpty()) {
      eventPublisher.publish(
        DomainEvent(
          eventId = UUID.randomUUID(),
          eventType = "task.created",
          occurredAt = Instant.now(),
          source = "task-service",
          entityType = "task",
          entityId = taskId,
          hospitalId = task.hospitalId,
          payload = mapOf("title" to task.title),
          recipientIds = recipients,
        ),
      )
    }
    return TaskView.from(task)
  }

  @Transactional(readOnly = true)
  fun listTasks(
    assigneeId: UUID?,
    status: String?,
  ): List<TaskView> {
    val tasks =
      when {
        assigneeId != null -> taskRepository.findByAssigneeId(assigneeId)
        status != null -> taskRepository.findByStatus(status)
        else -> taskRepository.findAll()
      }
    return tasks
      .sortedByDescending { it.createdAt ?: Instant.EPOCH }
      .map { TaskView.from(it) }
  }

  @Transactional(readOnly = true)
  fun getTask(id: UUID): TaskView = TaskView.from(findTask(id))

  @Transactional
  fun updateTask(
    id: UUID,
    dto: TaskUpdateDto,
  ): TaskView {
    val task = findTask(id)
    task.title = dto.title ?: task.title
    task.description = dto.description ?: task.description
    task.assigneeId = dto.assigneeId ?: task.assigneeId
    task.priority = dto.priority ?: task.priority
    task.dueAt = dto.dueAt ?: task.dueAt
    task.relatedType = dto.relatedType ?: task.relatedType
    task.relatedId = dto.relatedId ?: task.relatedId
    taskRepository.save(task)
    return TaskView.from(task)
  }

  @Transactional
  fun setStatus(
    id: UUID,
    newStatus: String,
    updaterId: UUID,
  ): TaskView {
    val valid = setOf("pending", "in_progress", "completed", "cancelled")
    if (newStatus !in valid) {
      throw IllegalArgumentException("TASK_INVALID_STATUS")
    }
    val task = findTask(id)
    val wasCompleted = task.status == "completed"
    task.status = newStatus
    task.updatedBy = updaterId
    if (newStatus == "completed") {
      task.completedAt = Instant.now()
    } else if (wasCompleted) {
      task.completedAt = null
    }
    taskRepository.save(task)
    if (newStatus == "completed") {
      val recipients = listOfNotNull(task.assignerId ?: task.assigneeId)
      if (recipients.isNotEmpty()) {
        eventPublisher.publish(
          DomainEvent(
            eventId = UUID.randomUUID(),
            eventType = "task.completed",
            occurredAt = Instant.now(),
            source = "task-service",
            entityType = "task",
            entityId = id,
            hospitalId = task.hospitalId,
            payload = mapOf("title" to task.title),
            recipientIds = recipients,
          ),
        )
      }
    }
    return TaskView.from(task)
  }

  @Transactional
  fun assignTask(
    id: UUID,
    assigneeId: UUID,
    assignerId: UUID,
  ): TaskView {
    val task = findTask(id)
    task.assigneeId = assigneeId
    task.assignerId = assignerId
    task.updatedBy = assignerId
    taskRepository.save(task)
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "task.assigned",
        occurredAt = Instant.now(),
        source = "task-service",
        entityType = "task",
        entityId = id,
        hospitalId = task.hospitalId,
        payload = mapOf("title" to task.title),
        recipientIds = listOf(assigneeId, assignerId).distinct(),
      ),
    )
    return TaskView.from(task)
  }

  private fun findTask(id: UUID): Task = taskRepository.findById(id).orElseThrow { IllegalArgumentException("TASK_NOT_FOUND") }
}
