package com.squamed.core.workflow.repository

import com.squamed.core.workflow.domain.WorkflowStepInstance
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkflowStepInstanceRepository : JpaRepository<WorkflowStepInstance, UUID> {
  fun findByInstanceIdOrderBySequenceOrderAsc(instanceId: UUID): List<WorkflowStepInstance>
}
