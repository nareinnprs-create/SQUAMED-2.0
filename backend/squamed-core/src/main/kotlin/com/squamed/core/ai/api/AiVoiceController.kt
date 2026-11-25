package com.squamed.core.ai.api

import com.squamed.core.ai.service.AiVoiceService
import com.squamed.core.ai.service.VoiceCommandView
import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class VoiceCommandRequest(
  val transcript: String,
  val locale: String = "en",
  val screen: String? = null,
  val patientId: UUID? = null,
)

data class VoiceRejectRequest(
  val reason: String? = null,
)

@RestController
@RequestMapping("/api/v1/ai/voice")
class AiVoiceController(
  private val voiceService: AiVoiceService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping("/commands")
  @RequiresPermission("ai:chat:agent")
  fun command(
    @RequestBody dto: VoiceCommandRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<VoiceCommandView> =
    ApiResponse.ok(
      voiceService.command(
        principal,
        tenantResolver.pinOrDefault(principal, null),
        dto.transcript,
        dto.locale,
        dto.screen,
        dto.patientId,
      ),
    )

  @GetMapping("/commands")
  @RequiresPermission("ai:chat:agent")
  fun commands(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<VoiceCommandView>> = ApiResponse.ok(voiceService.list(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/commands/{id}/approve")
  @RequiresPermission("ai:chat:agent")
  fun approve(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<VoiceCommandView> = ApiResponse.ok(voiceService.approveByVoice(id, principal, tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/commands/{id}/reject")
  @RequiresPermission("ai:chat:agent")
  fun reject(
    @PathVariable id: UUID,
    @RequestBody dto: VoiceRejectRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<VoiceCommandView> = ApiResponse.ok(voiceService.reject(id, principal, tenantResolver.pinOrDefault(principal, null), dto.reason))
}
