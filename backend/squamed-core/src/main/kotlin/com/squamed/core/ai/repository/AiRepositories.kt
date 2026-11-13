package com.squamed.core.ai.repository

import com.squamed.core.ai.domain.AgentConfig
import com.squamed.core.ai.domain.InteractionLog
import com.squamed.core.ai.domain.ModelConfig
import com.squamed.core.ai.domain.ToolConfig
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ModelConfigRepository : JpaRepository<ModelConfig, UUID> {
  fun findByProviderIgnoreCase(provider: String): List<ModelConfig>

  fun findByEnabledAndIsDefaultTrue(enabled: Boolean): ModelConfig?

  fun findAllByOrderByCreatedAtAsc(): List<ModelConfig>
}

interface AgentConfigRepository : JpaRepository<AgentConfig, UUID> {
  fun findByCodeIgnoreCase(code: String): AgentConfig?

  fun findAllByOrderByCreatedAtAsc(): List<AgentConfig>
}

interface ToolConfigRepository : JpaRepository<ToolConfig, UUID> {
  fun findByEnabledTrue(): List<ToolConfig>

  fun findAllByOrderByToolCodeAsc(): List<ToolConfig>
}

interface InteractionLogRepository : JpaRepository<InteractionLog, UUID> {
  fun findTop100ByOrderByCreatedAtDesc(): List<InteractionLog>
}
