package com.squamed.core.ai.api

import com.squamed.core.ai.service.AiCommandOutput
import com.squamed.core.ai.service.AiReply
import com.squamed.core.ai.service.AiService
import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class AiChatMessageDto(
  val role: String,
  val content: String,
)

data class AiChatRequest(
  val agentCode: String? = null,
  val messages: List<AiChatMessageDto>,
)

data class AiCommandRequest(
  val query: String,
  val agentCode: String? = null,
)

data class AiModelCreateRequest(
  val name: String,
  val provider: String,
  val modelName: String? = null,
  val baseUrl: String? = null,
  val apiKey: String? = null,
  val temperature: Double? = null,
  val maxTokens: Int? = null,
  val isDefault: Boolean = false,
  val costPer1kTokens: java.math.BigDecimal? = null,
)

data class AiPolicyUpdateRequest(
  val enabled: Boolean? = null,
  val dailyTokenCap: Int? = null,
)

data class AiActionDraftRequest(
  val actionType: String,
  val payload: Map<String, Any?> = emptyMap(),
)

data class AiActionRejectRequest(
  val reason: String? = null,
)

data class AiAgentCreateRequest(
  val code: String,
  val name: String,
  val description: String? = null,
  val systemPrompt: String? = null,
  val modelId: UUID? = null,
  val temperature: Double? = null,
  val enabled: Boolean? = null,
  val scopes: String? = null,
)

data class AiEvalRequest(
  val capability: String,
  val subject: String,
  val suiteName: String,
  val casesPassed: Int,
  val casesTotal: Int,
  val notes: String? = null,
)

data class EvalCaseDto(
  val input: String,
  val expected: String,
  val forbiddenToken: String? = null,
  val relatedType: String? = null,
  val critical: Boolean = false,
)

data class EvalDatasetRequest(
  val code: String,
  val capability: String,
  val description: String? = null,
  val cases: List<EvalCaseDto>,
)

data class AiIncidentRequest(
  val capability: String,
  val severity: String,
  val summary: String,
  val detail: String? = null,
)

data class AiIncidentUpdateRequest(
  val status: String? = null,
  val resolution: String? = null,
)

@RestController
@RequestMapping("/api/v1/ai")
@com.squamed.core.entitlement.api.RequiresFeature("ai")
class AiController(
  private val aiService: AiService,
  private val governanceService: com.squamed.core.ai.service.AiGovernanceService,
  private val actionService: com.squamed.core.ai.service.AiActionService,
  private val railService: com.squamed.core.ai.service.AiRailService,
  private val evalFramework: com.squamed.core.ai.service.AiEvalFramework,
  private val tenantResolver: TenantResolver,
) {
  @GetMapping("/models")
  @RequiresPermission("ai:read:agent")
  fun models(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(aiService.models().map { it.toMap() })

  @PostMapping("/models")
  @RequiresPermission("ai:manage:agent")
  fun createModel(
    @RequestBody req: AiModelCreateRequest,
  ): ApiResponse<Map<String, Any?>> {
    val model =
      aiService.createModel(
        req.name,
        req.provider,
        req.modelName,
        req.baseUrl,
        req.apiKey,
        req.temperature,
        req.maxTokens,
        req.isDefault,
        req.costPer1kTokens,
      )
    return ApiResponse.ok(mapOf("id" to model.id, "name" to model.name, "provider" to model.provider))
  }

  @DeleteMapping("/models/{id}")
  @RequiresPermission("ai:manage:agent")
  fun deleteModel(
    @PathVariable id: UUID,
  ): ApiResponse<Map<String, Any?>> {
    aiService.deleteModel(id)
    return ApiResponse.ok(mapOf("deleted" to true))
  }

  @GetMapping("/agents")
  @RequiresPermission("ai:read:agent")
  fun agents(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(aiService.agents().map { it.toMap() })

  @PostMapping("/agents")
  @RequiresPermission("ai:manage:agent")
  fun createAgent(
    @RequestBody req: AiAgentCreateRequest,
  ): ApiResponse<Map<String, Any?>> {
    val agent =
      aiService.createAgent(
        req.code,
        req.name,
        req.description,
        req.systemPrompt,
        req.modelId,
        req.temperature,
        req.enabled,
        req.scopes,
      )
    return ApiResponse.ok(mapOf("id" to agent.id, "code" to agent.code, "name" to agent.name))
  }

  @PatchMapping("/agents/{id}")
  @RequiresPermission("ai:manage:agent")
  fun updateAgent(
    @PathVariable id: UUID,
    @RequestBody patch: Map<String, Any?>,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(aiService.updateAgent(id, patch).toMap())

  @GetMapping("/tools")
  @RequiresPermission("ai:read:agent")
  fun tools(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(aiService.tools())

  @PostMapping("/chat")
  @RequiresPermission("ai:chat:agent")
  fun chat(
    @RequestBody req: AiChatRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AiReply> =
    ApiResponse.ok(
      aiService.chat(
        req.agentCode,
        req.messages.map {
          com.squamed.core.ai.service
            .AiChatMessage(it.role, it.content)
        },
        principal,
        tenantResolver.pinOrDefault(principal, null),
      ),
    )

  @PostMapping("/command")
  @RequiresPermission("ai:chat:agent")
  fun command(
    @RequestBody req: AiCommandRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AiCommandOutput> = ApiResponse.ok(aiService.command(req.query, principal, tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/chat/stream")
  @RequiresPermission("ai:chat:agent")
  fun chatStream(
    @RequestBody req: AiChatRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
    response: HttpServletResponse,
  ) {
    val userMessage = req.messages.lastOrNull { it.role == "user" }?.content ?: ""
    response.contentType = "text/event-stream; charset=UTF-8"
    response.setHeader("Cache-Control", "no-cache")
    response.setHeader("X-Accel-Buffering", "no")
    try {
      val reply = aiService.generate(req.agentCode, userMessage, principal, tenantResolver.pinOrDefault(principal, null))
      val chunks = reply.reply.split(Regex("(?<=\\s)|(?=\\s)")).filter { it.isNotBlank() }
      val writer = response.writer
      chunks.forEach { chunk ->
        writer.write("data: {\"type\":\"token\",\"text\":${jsonEscape(chunk)}}\n\n")
        writer.flush()
        try {
          Thread.sleep(18)
        } catch (_: InterruptedException) {
          Thread.currentThread().interrupt()
        }
      }
      writer.write(
        "data: {\"type\":\"done\",\"model\":${jsonEscape(
          reply.modelName,
        )},\"intent\":${jsonEscape(
          reply.intent,
        )},\"latencyMs\":${reply.latencyMs},\"logId\":${jsonEscape(reply.logId.toString())}}\n\n",
      )
      writer.flush()
    } catch (e: Exception) {
      response.writer.write("data: {\"type\":\"error\",\"message\":${jsonEscape(e.message ?: "AI_ERROR")}}\n\n")
      response.writer.flush()
    }
  }

  @GetMapping("/logs")
  @RequiresPermission("ai:read:agent")
  fun logs(
    // 00.04 tenant sweep: interaction logs (raw messages) are pinned to the caller's hospital (null = admin).
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<Map<String, Any?>>> =
    ApiResponse.ok(
      aiService.logs(principal.hospitalId).map { log ->
        mapOf(
          "id" to log.id,
          "agentCode" to log.agentCode,
          "modelName" to log.modelName,
          "userId" to log.userId,
          "userMessage" to log.userMessage,
          "assistantMessage" to log.assistantMessage,
          "intent" to log.intent,
          "latencyMs" to log.latencyMs,
          "capability" to log.capability,
          "promptVersion" to log.promptVersion,
          "promptTokens" to log.promptTokens,
          "completionTokens" to log.completionTokens,
          "tokensUsed" to log.tokensUsed,
          "costUsd" to log.costUsd,
          "redacted" to log.redacted,
          "createdAt" to log.createdAt,
        )
      },
    )

  // 05.01 governance: per-capability kill switch + daily token cap, and today's usage/cost.
  @GetMapping("/governance/policies")
  @RequiresPermission("ai:read:agent")
  fun policies(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<Map<String, Any?>>> =
    ApiResponse.ok(
      governanceService.policies(tenantResolver.pinOrDefault(principal, null)).map {
        mapOf(
          "capability" to it.capability,
          "enabled" to it.enabled,
          "dailyTokenCap" to it.dailyTokenCap,
          "updatedAt" to it.updatedAt,
        )
      },
    )

  @PutMapping("/governance/policies/{capability}")
  @RequiresPermission("ai:manage:agent")
  fun updatePolicy(
    @PathVariable capability: String,
    @RequestBody req: AiPolicyUpdateRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Map<String, Any?>> {
    val policy =
      governanceService.update(
        tenantResolver.pinOrDefault(principal, null),
        capability,
        req.enabled,
        req.dailyTokenCap,
      )
    return ApiResponse.ok(mapOf("capability" to policy.capability, "enabled" to policy.enabled, "dailyTokenCap" to policy.dailyTokenCap))
  }

  @GetMapping("/governance/usage")
  @RequiresPermission("ai:read:agent")
  fun usage(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<com.squamed.core.ai.service.AiUsageView>> = ApiResponse.ok(governanceService.usage(tenantResolver.pinOrDefault(principal, null)))

  // 05.03 command-bar action drafts: propose -> human approve/reject -> governed platform call.
  @PostMapping("/actions/draft")
  @RequiresPermission("ai:chat:agent")
  fun draftAction(
    @RequestBody req: AiActionDraftRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<com.squamed.core.ai.service.ActionDraftView> =
    ApiResponse.ok(
      actionService.propose(
        principal,
        tenantResolver.pinOrDefault(principal, null),
        req.actionType,
        req.payload,
      ),
    )

  // 05.06: an agent persona proposes; a human still approves.
  @PostMapping("/agents/{code}/propose")
  @RequiresPermission("ai:chat:agent")
  fun proposeAsAgent(
    @PathVariable code: String,
    @RequestBody req: AiActionDraftRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<com.squamed.core.ai.service.ActionDraftView> =
    ApiResponse.ok(
      actionService.proposeAsAgent(
        code,
        principal,
        tenantResolver.pinOrDefault(principal, null),
        req.actionType,
        req.payload,
      ),
    )

  @GetMapping("/actions")
  @RequiresPermission("ai:chat:agent")
  fun actions(
    @RequestParam(name = "status", required = false) status: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<com.squamed.core.ai.service.ActionDraftView>> = ApiResponse.ok(actionService.list(tenantResolver.pinOrDefault(principal, null), status))

  @PostMapping("/actions/{id}/approve")
  @RequiresPermission("ai:chat:agent")
  fun approveAction(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<com.squamed.core.ai.service.ActionDraftView> =
    ApiResponse.ok(actionService.approve(id, principal, tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/actions/{id}/reject")
  @RequiresPermission("ai:chat:agent")
  fun rejectAction(
    @PathVariable id: UUID,
    @RequestBody req: AiActionRejectRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<com.squamed.core.ai.service.ActionDraftView> =
    ApiResponse.ok(actionService.reject(id, principal, tenantResolver.pinOrDefault(principal, null), req.reason))

  // 05.07 governance registry: evals on releases, incident register, cost dashboard.
  @PostMapping("/governance/evals")
  @RequiresPermission("ai:manage:agent")
  fun recordEval(
    @RequestBody req: AiEvalRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<com.squamed.core.ai.service.EvalRunView> =
    ApiResponse.ok(
      governanceService.recordEval(
        tenantResolver.pinOrDefault(principal, null),
        req.capability,
        req.subject,
        req.suiteName,
        req.casesPassed,
        req.casesTotal,
        req.notes,
        principal.userId,
      ),
    )

  @GetMapping("/governance/evals")
  @RequiresPermission("ai:read:agent")
  fun evals(
    @RequestParam(name = "subject", required = false) subject: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<com.squamed.core.ai.service.EvalRunView>> = ApiResponse.ok(governanceService.evals(tenantResolver.pinOrDefault(principal, null), subject))

  // 05.02 eval framework: datasets, runs through the real path, and the 500-sim agent harness.
  @PostMapping("/evals/datasets")
  @RequiresPermission("ai:manage:agent")
  fun ingestEvalDataset(
    @RequestBody req: EvalDatasetRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<com.squamed.core.ai.service.EvalDatasetView> =
    ApiResponse.ok(
      evalFramework.ingest(
        tenantResolver.pinOrDefault(principal, null),
        req.code,
        req.capability,
        req.description,
        req.cases.map {
          com.squamed.core.ai.service
            .EvalCaseInput(it.input, it.expected, it.forbiddenToken, it.relatedType, it.critical)
        },
        "MANUAL",
        principal.userId,
      ),
    )

  @GetMapping("/evals/datasets")
  @RequiresPermission("ai:read:agent")
  fun evalDatasets(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<com.squamed.core.ai.service.EvalDatasetView>> = ApiResponse.ok(evalFramework.datasets(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/evals/datasets/{code}/run")
  @RequiresPermission("ai:manage:agent")
  fun runEvalDataset(
    @PathVariable code: String,
    @RequestParam(name = "k", required = false, defaultValue = "1") k: Int,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<com.squamed.core.ai.service.EvalSuiteResult> =
    ApiResponse.ok(
      evalFramework.run(
        tenantResolver.pinOrDefault(principal, null),
        code,
        principal,
        k,
        principal.userId,
      ),
    )

  @PostMapping("/evals/datasets/scribe-corrections/ingest")
  @RequiresPermission("ai:manage:agent")
  fun ingestScribeCorrections(
    @RequestParam(name = "limit", required = false, defaultValue = "200") limit: Int,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<com.squamed.core.ai.service.EvalDatasetView> =
    ApiResponse.ok(
      evalFramework.ingestScribeCorrections(
        tenantResolver.pinOrDefault(principal, null),
        limit,
        principal.userId,
      ),
    )

  @GetMapping("/evals/gates")
  @RequiresPermission("ai:read:agent")
  fun evalGates(
    @RequestParam(name = "capability", required = false) capability: String?,
  ): ApiResponse<List<com.squamed.core.ai.service.EvalGate>> =
    ApiResponse.ok(
      com.squamed.core.ai.service.AiEvalFramework
        .gates(capability),
    )

  @PostMapping("/evals/agents/simulate")
  @RequiresPermission("ai:manage:agent")
  fun simulateAgents(
    @RequestParam(name = "count", required = false, defaultValue = "500") count: Int,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<com.squamed.core.ai.service.AgentSimulationView> =
    ApiResponse.ok(
      evalFramework.simulateAgents(
        tenantResolver.pinOrDefault(principal, null),
        count,
        principal.userId,
      ),
    )

  @PostMapping("/governance/incidents")
  @RequiresPermission("ai:chat:agent")
  fun reportIncident(
    @RequestBody req: AiIncidentRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<com.squamed.core.ai.service.AiIncidentView> =
    ApiResponse.ok(
      governanceService.reportIncident(
        tenantResolver.pinOrDefault(principal, null),
        req.capability,
        req.severity,
        req.summary,
        req.detail,
        principal.userId,
      ),
    )

  @GetMapping("/governance/incidents")
  @RequiresPermission("ai:read:agent")
  fun incidents(
    @RequestParam(name = "status", required = false) status: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<com.squamed.core.ai.service.AiIncidentView>> =
    ApiResponse.ok(governanceService.incidents(tenantResolver.pinOrDefault(principal, null), status))

  @PatchMapping("/governance/incidents/{id}")
  @RequiresPermission("ai:manage:agent")
  fun updateIncident(
    @PathVariable id: UUID,
    @RequestBody req: AiIncidentUpdateRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<com.squamed.core.ai.service.AiIncidentView> =
    ApiResponse.ok(
      governanceService.updateIncident(
        id,
        tenantResolver.pinOrDefault(principal, null),
        req.status,
        req.resolution,
        principal.userId,
      ),
    )

  @GetMapping("/governance/costs")
  @RequiresPermission("ai:read:agent")
  fun costs(
    @RequestParam(name = "from", required = false) from: String?,
    @RequestParam(name = "to", required = false) to: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<com.squamed.core.ai.service.CostDashboardView> =
    ApiResponse.ok(
      governanceService.costs(
        tenantResolver.pinOrDefault(principal, null),
        from?.takeIf { it.isNotBlank() }?.let { java.time.Instant.parse(it) },
        to?.takeIf { it.isNotBlank() }?.let { java.time.Instant.parse(it) },
      ),
    )

  // 05.04 intelligence rail: items assembled from governed state, dismissible with a reason.
  @GetMapping("/rail/items")
  @RequiresPermission("ai:chat:agent")
  fun railItems(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<com.squamed.core.ai.service.AttentionItemView>> = ApiResponse.ok(railService.scan(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/rail/items/{id}/dismiss")
  @RequiresPermission("ai:chat:agent")
  fun dismissRailItem(
    @PathVariable id: UUID,
    @RequestBody req: AiActionRejectRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<com.squamed.core.ai.service.AttentionItemView> =
    ApiResponse.ok(
      railService.dismiss(
        id,
        tenantResolver.pinOrDefault(principal, null),
        principal.userId,
        req.reason,
      ),
    )

  private fun jsonEscape(s: String): String =
    s
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "")
}

private fun com.squamed.core.ai.domain.ModelConfig.toMap(): Map<String, Any?> =
  mapOf(
    "id" to id,
    "name" to name,
    "provider" to provider,
    "modelName" to modelName,
    "baseUrl" to baseUrl,
    "hasApiKey" to (apiKey != null),
    "temperature" to temperature,
    "maxTokens" to maxTokens,
    "enabled" to enabled,
    "isDefault" to isDefault,
    "createdAt" to createdAt,
  )

private fun com.squamed.core.ai.domain.AgentConfig.toMap(): Map<String, Any?> =
  mapOf(
    "id" to id,
    "code" to code,
    "name" to name,
    "description" to description,
    "systemPrompt" to systemPrompt,
    "modelId" to modelId,
    "temperature" to temperature,
    "enabled" to enabled,
    "scopes" to scopes,
    "createdAt" to createdAt,
  )
