package com.squamed.core.workflow.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "workflow_definition", schema = "workflow")
class WorkflowDefinition(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "code", nullable = false, length = 100)
  var code: String,
  @Column(name = "name", nullable = false, length = 255)
  var name: String,
  @Column(name = "description")
  var description: String? = null,
  @Column(name = "trigger_type", nullable = false, length = 50)
  var triggerType: String = "MANUAL",
  @Column(name = "definition_json", nullable = false)
  var definitionJson: String,
  @Column(name = "status", nullable = false, length = 30)
  var status: String = "DRAFT",
  version: Long,
) : BaseEntity(version = version)
