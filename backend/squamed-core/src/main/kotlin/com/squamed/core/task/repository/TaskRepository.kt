package com.squamed.core.task.repository

import com.squamed.core.task.domain.Task
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TaskRepository : JpaRepository<Task, UUID> {
  fun findByAssigneeId(assigneeId: UUID): List<Task>

  fun findByStatus(status: String): List<Task>

  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): Task?

  fun findByRelatedTypeAndRelatedId(
    relatedType: String,
    relatedId: UUID,
  ): List<Task>

  fun findByRelatedId(relatedId: UUID): List<Task>
}
