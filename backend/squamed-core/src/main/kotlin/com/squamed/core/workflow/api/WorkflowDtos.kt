package com.squamed.core.workflow.api

import com.squamed.core.workflow.domain.WorkflowApproval
import com.squamed.core.workflow.domain.WorkflowStepInstance
import java.time.Instant
import java.util.UUID

data class WorkflowStepConfigDto(
  val stepKey: String,
  val stepType: String,
  val sequenceOrder: Int,
  val config: Map<String, Any?>? = null,
)

data class WorkflowDefinitionDto(
  val id: UUID?,
  val code: String,
  val name: String,
  val description: String?,
  val triggerType: String,
  val status: String,
  val version: Long,
  val steps: List<WorkflowStepConfigDto> = emptyList(),
)

data class CreateWorkflowDefinitionRequest(
  val code: String? = null,
  val name: String? = null,
  val description: String? = null,
  val triggerType: String? = null,
  val steps: List<WorkflowStepConfigDto>? = null,
)

data class TriggerWorkflowRequest(
  val definitionId: UUID? = null,
  val code: String? = null,
  val payload: Map<String, Any?>? = null,
)

data class DecideApprovalRequest(
  val decision: String? = null,
  val comments: String? = null,
)

data class WorkflowStepInstanceView(
  val id: UUID?,
  val stepKey: String,
  val stepType: String,
  val status: String,
  val stepConfig: String?,
  val result: String?,
  val sequenceOrder: Int,
  val startedAt: Instant?,
  val endedAt: Instant?,
) {
  companion object {
    fun from(entity: WorkflowStepInstance) =
      WorkflowStepInstanceView(
        id = entity.id,
        stepKey = entity.stepKey,
        stepType = entity.stepType,
        status = entity.status,
        stepConfig = entity.stepConfig,
        result = entity.result,
        sequenceOrder = entity.sequenceOrder,
        startedAt = entity.startedAt,
        endedAt = entity.endedAt,
      )
  }
}

data class WorkflowApprovalView(
  val id: UUID?,
  val stepInstanceId: UUID?,
  val approverId: UUID,
  val status: String,
  val comments: String?,
  val decidedBy: UUID?,
  val decidedAt: Instant?,
) {
  companion object {
    fun from(entity: WorkflowApproval) =
      WorkflowApprovalView(
        id = entity.id,
        stepInstanceId = entity.stepInstanceId,
        approverId = entity.approverId,
        status = entity.status,
        comments = entity.comments,
        decidedBy = entity.decidedBy,
        decidedAt = entity.decidedAt,
      )
  }
}

data class WorkflowInstanceView(
  val id: UUID?,
  val definitionId: UUID?,
  val definitionCode: String?,
  val status: String,
  val triggerPayload: String?,
  val currentStepKey: String?,
  val startedAt: Instant?,
  val endedAt: Instant?,
  val stepInstances: List<WorkflowStepInstanceView> = emptyList(),
  val approvals: List<WorkflowApprovalView> = emptyList(),
)

data class WorkflowPendingApprovalView(
  val approvalId: UUID?,
  val instanceId: UUID?,
  val instanceStatus: String?,
  val definitionCode: String?,
  val stepKey: String,
  val stepType: String,
  val approverId: UUID,
  val status: String,
  val comments: String?,
  val decidedBy: UUID?,
  val decidedAt: Instant?,
  val createdAt: Instant?,
)
