package com.squamed.core.workflow.repository

import com.squamed.core.workflow.domain.WorkflowApproval
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkflowApprovalRepository : JpaRepository<WorkflowApproval, UUID> {
  fun findByStepInstanceId(stepInstanceId: UUID): List<WorkflowApproval>

  fun findByApproverIdAndStatus(
    approverId: UUID,
    status: String,
  ): List<WorkflowApproval>

  fun findAllByOrderByCreatedAtDesc(): List<WorkflowApproval>
}
