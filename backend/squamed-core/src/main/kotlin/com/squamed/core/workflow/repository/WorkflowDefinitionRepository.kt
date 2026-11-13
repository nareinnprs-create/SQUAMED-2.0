package com.squamed.core.workflow.repository

import com.squamed.core.workflow.domain.WorkflowDefinition
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkflowDefinitionRepository : JpaRepository<WorkflowDefinition, UUID> {
  fun findByCodeOrderByVersionDesc(code: String): List<WorkflowDefinition>

  fun findByCodeAndVersion(
    code: String,
    version: Long,
  ): WorkflowDefinition?

  fun findAllByOrderByCreatedAtDesc(): List<WorkflowDefinition>
}
