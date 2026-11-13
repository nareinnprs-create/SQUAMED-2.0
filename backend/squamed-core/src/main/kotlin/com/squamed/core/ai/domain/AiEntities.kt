package com.squamed.core.ai.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "model_config", schema = "ai")
class ModelConfig(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "name", nullable = false, length = 160)
  var name: String,
  @Column(name = "provider", nullable = false, length = 30)
  var provider: String,
  @Column(name = "model_name", length = 160)
  var modelName: String? = null,
  @Column(name = "base_url", length = 500)
  var baseUrl: String? = null,
  @Column(name = "api_key", length = 500)
  var apiKey: String? = null,
  @Column(name = "temperature", nullable = false)
  var temperature: Double = 0.2,
  @Column(name = "max_tokens", nullable = false)
  var maxTokens: Int = 512,
  @Column(name = "enabled", nullable = false)
  var enabled: Boolean = true,
  @Column(name = "is_default", nullable = false)
  var isDefault: Boolean = false,
) : BaseEntity()

@Entity
@Table(name = "agent_config", schema = "ai")
class AgentConfig(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "code", nullable = false, length = 100)
  var code: String,
  @Column(name = "name", nullable = false, length = 160)
  var name: String,
  @Column(name = "description")
  var description: String? = null,
  @Column(name = "system_prompt")
  var systemPrompt: String? = null,
  @Column(name = "model_id")
  var modelId: UUID? = null,
  @Column(name = "temperature", nullable = false)
  var temperature: Double = 0.2,
  @Column(name = "enabled", nullable = false)
  var enabled: Boolean = true,
) : BaseEntity()

@Entity
@Table(name = "tool_config", schema = "ai")
class ToolConfig(
  @Column(name = "tool_code", nullable = false, length = 60)
  var toolCode: String,
  @Column(name = "name", nullable = false, length = 160)
  var name: String,
  @Column(name = "description")
  var description: String? = null,
  @Column(name = "enabled", nullable = false)
  var enabled: Boolean = true,
) : BaseEntity()

@Entity
@Table(name = "interaction_log", schema = "ai")
class InteractionLog(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "agent_id")
  var agentId: UUID? = null,
  @Column(name = "agent_code", length = 100)
  var agentCode: String? = null,
  @Column(name = "model_name", length = 160)
  var modelName: String? = null,
  @Column(name = "user_id")
  var userId: UUID? = null,
  @Column(name = "user_message", columnDefinition = "TEXT")
  var userMessage: String? = null,
  @Column(name = "assistant_message", columnDefinition = "TEXT")
  var assistantMessage: String? = null,
  @Column(name = "intent", length = 50)
  var intent: String? = null,
  @Column(name = "latency_ms")
  var latencyMs: Long? = null,
  @Column(name = "tokens_used")
  var tokensUsed: Int? = null,
) : BaseEntity()
