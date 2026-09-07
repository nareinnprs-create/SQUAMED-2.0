package com.squamed.core.workflow.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "workflow_instance", schema = "workflow")
class WorkflowInstance(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "definition_id", nullable = false)
  var definitionId: UUID,
  @Column(name = "definition_code", length = 100)
  var definitionCode: String? = null,
  @Column(name = "status", nullable = false, length = 30)
  var status: String = "RUNNING",
  @Column(name = "trigger_payload")
  var triggerPayload: String? = null,
  @Column(name = "current_step_key", length = 100)
  var currentStepKey: String? = null,
  @Column(name = "started_at", nullable = false)
  var startedAt: Instant = Instant.now(),
  @Column(name = "ended_at")
  var endedAt: Instant? = null,
) : BaseEntity()
