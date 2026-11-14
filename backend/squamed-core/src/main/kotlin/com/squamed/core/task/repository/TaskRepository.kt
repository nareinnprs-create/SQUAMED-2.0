package com.squamed.core.task.repository

import com.squamed.core.task.domain.Task
import com.squamed.core.task.domain.TaskLink
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface TaskRepository : JpaRepository<Task, UUID> {
  fun findByAssigneeId(assigneeId: UUID): List<Task>

  fun findByStatus(status: String): List<Task>

  /** Open tasks only: the SLA monitor never touches completed/cancelled work. */
  fun findByStatusNotIn(status: List<String>): List<Task>

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

interface TaskLinkRepository : JpaRepository<TaskLink, UUID> {
  fun findByTaskId(taskId: UUID): List<TaskLink>

  fun deleteByTaskId(taskId: UUID)

  fun findBySubjectTypeAndSubjectId(
    subjectType: String,
    subjectId: UUID,
  ): List<TaskLink>

  /** Tenant-scoped task ids for a subject — stage-1 filter for the reverse lookup API. */
  @Query(
    """
    select l from TaskLink l
    where l.subjectType = :subjectType and l.subjectId = :subjectId
      and (:hospitalId is null or l.hospitalId = :hospitalId)
    """,
  )
  fun findBySubject(
    @Param("subjectType") subjectType: String,
    @Param("subjectId") subjectId: UUID,
    @Param("hospitalId") hospitalId: UUID?,
  ): List<TaskLink>
}
