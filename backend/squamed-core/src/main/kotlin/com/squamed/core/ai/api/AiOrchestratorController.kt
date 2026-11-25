package com.squamed.core.ai.api

import com.squamed.core.ai.service.AgentEvalView
import com.squamed.core.ai.service.AiOrchestratorService
import com.squamed.core.ai.service.OrchestrationView
import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class OrchestrationRequest(
  val question: String,
)

@RestController
@RequestMapping("/api/v1/ai")
class AiOrchestratorController(
  private val orchestrator: AiOrchestratorService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping("/orchestrate")
  @RequiresPermission("ai:chat:agent")
  fun orchestrate(
    @RequestBody dto: OrchestrationRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<OrchestrationView> = ApiResponse.ok(orchestrator.orchestrate(tenantResolver.pinOrDefault(principal, null), dto.question))

  @PostMapping("/agents/{code}/eval")
  @RequiresPermission("ai:manage:agent")
  fun runAgentEval(
    @PathVariable code: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AgentEvalView> =
    ApiResponse.ok(
      orchestrator.runAgentEval(code, tenantResolver.pinOrDefault(principal, null), principal.userId),
    )
}
