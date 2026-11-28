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
  // 05.07: the permission set an agent may act with (CSV of permission codes).
  @Column(name = "scopes", length = 500)
  var scopes: String? = null,
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

// 05.03 command bar: an AI proposal a human must approve before any platform action runs.
@Entity
@Table(name = "action_draft", schema = "ai")
class ActionDraft(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "proposed_by", nullable = false)
  var proposedBy: UUID,
  @Column(name = "action_type", nullable = false, length = 40)
  var actionType: String,
  @Column(name = "summary", nullable = false, length = 400)
  var summary: String,
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
  var payload: com.fasterxml.jackson.databind.JsonNode,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "PROPOSED",
  @Column(name = "expires_at", nullable = false)
  var expiresAt: java.time.Instant,
  @Column(name = "decided_at")
  var decidedAt: java.time.Instant? = null,
  @Column(name = "decided_by")
  var decidedBy: UUID? = null,
  @Column(name = "decision_reason", length = 400)
  var decisionReason: String? = null,
  @Column(name = "result_ref", length = 120)
  var resultRef: String? = null,
) : BaseEntity()

// 05.07 governance: eval results attached to a release subject (agent@version / model).
@Entity
@Table(name = "eval_run", schema = "ai")
class EvalRun(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "capability", nullable = false, length = 30)
  var capability: String,
  @Column(name = "subject", nullable = false, length = 160)
  var subject: String,
  @Column(name = "suite_name", nullable = false, length = 160)
  var suiteName: String,
  @Column(name = "cases_passed", nullable = false)
  var casesPassed: Int,
  @Column(name = "cases_total", nullable = false)
  var casesTotal: Int,
  @Column(name = "score", nullable = false)
  var score: java.math.BigDecimal,
  @Column(name = "verdict", nullable = false, length = 20)
  var verdict: String,
  @Column(name = "notes", length = 400)
  var notes: String? = null,
  @Column(name = "recorded_by")
  var recordedBy: UUID? = null,
  @Column(name = "recorded_at", nullable = false)
  var recordedAt: java.time.Instant = java.time.Instant.now(),
  @Column(name = "dataset_id")
  var datasetId: UUID? = null,
  @Column(name = "pass_at_k")
  var passAtK: java.math.BigDecimal? = null,
  @Column(name = "k")
  var k: Int? = null,
  @Column(name = "detail", columnDefinition = "TEXT")
  var detail: String? = null,
) : BaseEntity()

// 05.07 governance: the AI incident register.
@Entity
@Table(name = "incident", schema = "ai")
class AiIncident(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "capability", nullable = false, length = 30)
  var capability: String,
  @Column(name = "severity", nullable = false, length = 20)
  var severity: String,
  @Column(name = "summary", nullable = false, length = 300)
  var summary: String,
  @Column(name = "detail", columnDefinition = "TEXT")
  var detail: String? = null,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "OPEN",
  @Column(name = "reported_by")
  var reportedBy: UUID? = null,
  @Column(name = "reported_at", nullable = false)
  var reportedAt: java.time.Instant = java.time.Instant.now(),
  @Column(name = "closed_at")
  var closedAt: java.time.Instant? = null,
  @Column(name = "resolution", length = 400)
  var resolution: String? = null,
) : BaseEntity()

// 05.04 intelligence rail: an attention item assembled from governed platform state.
@Entity
@Table(name = "attention_item", schema = "ai")
class AttentionItem(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "item_type", nullable = false, length = 40)
  var itemType: String,
  @Column(name = "severity", nullable = false, length = 20)
  var severity: String,
  @Column(name = "title", nullable = false, length = 300)
  var title: String,
  @Column(name = "detail", length = 500)
  var detail: String? = null,
  @Column(name = "dedupe_key", nullable = false, length = 200)
  var dedupeKey: String,
  @Column(name = "source_type", nullable = false, length = 40)
  var sourceType: String,
  @Column(name = "source_id")
  var sourceId: UUID? = null,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "OPEN",
  @Column(name = "raised_at", nullable = false)
  var raisedAt: java.time.Instant = java.time.Instant.now(),
  @Column(name = "cleared_at")
  var clearedAt: java.time.Instant? = null,
  @Column(name = "dismissal_reason", length = 300)
  var dismissalReason: String? = null,
) : BaseEntity()

// 05.02 eval framework: a versioned case set per capability. Gate thresholds are code constants,
// so the dataset carries only the cases and where they came from.
@Entity
@Table(name = "eval_dataset", schema = "ai")
class EvalDataset(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "code", nullable = false, length = 60)
  var code: String,
  @Column(name = "capability", nullable = false, length = 30)
  var capability: String,
  @Column(name = "dataset_version", nullable = false)
  var datasetVersion: Int = 1,
  @Column(name = "description", length = 300)
  var description: String? = null,
  @Column(name = "case_count", nullable = false)
  var caseCount: Int = 0,
  @Column(name = "source", nullable = false, length = 30)
  var source: String = "MANUAL",
) : BaseEntity()

@Entity
@Table(name = "eval_case", schema = "ai")
class EvalCase(
  @Column(name = "dataset_id", nullable = false)
  var datasetId: UUID,
  @Column(name = "ordinal", nullable = false)
  var ordinal: Int,
  @Column(name = "input_text", nullable = false, columnDefinition = "TEXT")
  var inputText: String,
  @Column(name = "expected", nullable = false, columnDefinition = "TEXT")
  var expected: String,
  @Column(name = "forbidden_token", length = 200)
  var forbiddenToken: String? = null,
  @Column(name = "related_type", length = 60)
  var relatedType: String? = null,
  @Column(name = "critical", nullable = false)
  var critical: Boolean = false,
) : BaseEntity()
