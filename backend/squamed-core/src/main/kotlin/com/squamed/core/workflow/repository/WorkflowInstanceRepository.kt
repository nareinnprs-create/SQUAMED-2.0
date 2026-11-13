package com.squamed.core.workflow.repository

import com.squamed.core.workflow.domain.WorkflowInstance
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkflowInstanceRepository : JpaRepository<WorkflowInstance, UUID> {
  fun findAllByOrderByStartedAtDesc(): List<WorkflowInstance>

  fun findByStatus(status: String): List<WorkflowInstance>
}
