package com.squamed.core.workflow.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "workflow_approval", schema = "workflow")
class WorkflowApproval(
  @Column(name = "step_instance_id", nullable = false)
  var stepInstanceId: UUID,
  @Column(name = "approver_id", nullable = false)
  var approverId: UUID,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "PENDING",
  @Column(name = "comments")
  var comments: String? = null,
  @Column(name = "decided_by")
  var decidedBy: UUID? = null,
  @Column(name = "decided_at")
  var decidedAt: Instant? = null,
) : BaseEntity()
