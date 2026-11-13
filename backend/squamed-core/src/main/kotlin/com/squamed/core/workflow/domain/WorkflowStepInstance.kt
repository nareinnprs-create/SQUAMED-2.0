package com.squamed.core.workflow.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "workflow_step_instance", schema = "workflow")
class WorkflowStepInstance(
  @Column(name = "instance_id", nullable = false)
  var instanceId: UUID,
  @Column(name = "step_key", nullable = false, length = 100)
  var stepKey: String,
  @Column(name = "step_type", nullable = false, length = 30)
  var stepType: String,
  @Column(name = "status", nullable = false, length = 30)
  var status: String = "PENDING",
  @Column(name = "step_config")
  var stepConfig: String? = null,
  @Column(name = "result")
  var result: String? = null,
  @Column(name = "sequence_order", nullable = false)
  var sequenceOrder: Int,
  @Column(name = "started_at")
  var startedAt: Instant? = null,
  @Column(name = "ended_at")
  var endedAt: Instant? = null,
) : BaseEntity()
