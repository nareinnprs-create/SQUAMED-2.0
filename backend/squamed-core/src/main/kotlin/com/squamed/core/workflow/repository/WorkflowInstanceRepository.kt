package com.squamed.core.workflow.repository

import com.squamed.core.workflow.domain.WorkflowInstance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface WorkflowInstanceRepository : JpaRepository<WorkflowInstance, UUID> {
  // 00.04 tenant sweep: hid = caller's bound hospitalId (null = admin = unfiltered).
  @Query(
    """
    select i from WorkflowInstance i
    where (:hid is null or i.hospitalId = :hid)
    order by i.startedAt desc
    """,
  )
  fun findAllByOrderByStartedAtDesc(
    @Param("hid") hid: UUID?,
  ): List<WorkflowInstance>

  @Query(
    """
    select i from WorkflowInstance i
    where i.status = :status and (:hid is null or i.hospitalId = :hid)
    """,
  )
  fun findByStatus(
    @Param("status") status: String,
    @Param("hid") hid: UUID?,
  ): List<WorkflowInstance>
}
