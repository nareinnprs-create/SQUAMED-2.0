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
  // 05.01: cost trace per 1k tokens for the routed model.
  @Column(name = "cost_per_1k_tokens", nullable = false)
  var costPer1kTokens: java.math.BigDecimal = java.math.BigDecimal.ZERO,
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
  // 05.01 full trace: capability, prompt version, token split, cost, redaction.
  @Column(name = "capability", nullable = false, length = 30)
  var capability: String = "chat",
  @Column(name = "prompt_version", length = 120)
  var promptVersion: String? = null,
  @Column(name = "prompt_tokens")
  var promptTokens: Int? = null,
  @Column(name = "completion_tokens")
  var completionTokens: Int? = null,
  @Column(name = "cost_usd", nullable = false)
  var costUsd: java.math.BigDecimal = java.math.BigDecimal.ZERO,
  @Column(name = "redacted", nullable = false)
  var redacted: Boolean = false,
) : BaseEntity()

// 05.01 gateway governance: per-hospital, per-capability kill switch + daily token cap.
@Entity
@Table(name = "gateway_policy", schema = "ai")
class GatewayPolicy(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "capability", nullable = false, length = 30)
  var capability: String,
  @Column(name = "enabled", nullable = false)
  var enabled: Boolean = true,
  @Column(name = "daily_token_cap", nullable = false)
  var dailyTokenCap: Int = 200_000,
) : BaseEntity()
