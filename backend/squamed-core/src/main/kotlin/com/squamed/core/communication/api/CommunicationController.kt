package com.squamed.core.communication.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.communication.service.CommunicationService
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/communication")
class CommunicationController(
  private val communicationService: CommunicationService,
) {
  @GetMapping("/channels")
  @RequiresPermission("communication:read:message")
  @Transactional(readOnly = true)
  fun myChannels(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<ChannelSummaryView>> = ApiResponse.ok(communicationService.listMyChannels(principal.userId))

  @PostMapping("/channels")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("communication:manage:channel")
  @Transactional
  fun createChannel(
    @RequestBody dto: ChannelCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ChannelView> =
    ApiResponse.ok(
      communicationService.createChannel(
        creatorId = principal.userId,
        hospitalId = principal.hospitalId,
        name = dto.name ?: throw IllegalArgumentException("CHANNEL_NAME_REQUIRED"),
        type = dto.type ?: "direct",
        memberIds = dto.memberIds ?: emptyList(),
        description = dto.description,
        patientId = dto.patientId,
        encounterId = dto.encounterId,
      ),
    )

  @GetMapping("/channels/{id}")
  @RequiresPermission("communication:read:message")
  @Transactional(readOnly = true)
  fun getChannel(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ChannelDetailView> = ApiResponse.ok(communicationService.channelDetail(principal.userId, id))

  @PostMapping("/channels/{id}/members")
  @RequiresPermission("communication:manage:channel")
  @Transactional
  fun addMember(
    @PathVariable id: UUID,
    @RequestBody dto: MemberAddDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ChannelMemberView> =
    ApiResponse.ok(
      communicationService.addMember(principal.userId, id, dto.userId ?: throw IllegalArgumentException("USER_ID_REQUIRED")),
    )

  @DeleteMapping("/channels/{id}/members/{userId}")
  @RequiresPermission("communication:manage:channel")
  @Transactional
  fun removeMember(
    @PathVariable id: UUID,
    @PathVariable userId: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Unit> = ApiResponse.ok(communicationService.removeMember(principal.userId, id, userId))

  @GetMapping("/channels/{id}/members")
  @RequiresPermission("communication:read:message")
  @Transactional(readOnly = true)
  fun listMembers(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<ChannelMemberView>> = ApiResponse.ok(communicationService.listMembers(principal.userId, id))

  @GetMapping("/channels/{id}/messages")
  @RequiresPermission("communication:read:message")
  @Transactional(readOnly = true)
  fun listMessages(
    @PathVariable id: UUID,
    @RequestParam(name = "limit", defaultValue = "50") limit: Int,
    @RequestParam(name = "beforeId", required = false) beforeId: UUID?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<MessageView>> = ApiResponse.ok(communicationService.listMessages(principal.userId, id, limit, beforeId))

  @PostMapping("/channels/{id}/messages")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("communication:read:message")
  @Transactional
  fun sendMessage(
    @PathVariable id: UUID,
    @RequestBody dto: MessageCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<MessageView> =
    ApiResponse.ok(
      communicationService.sendMessage(
        userId = principal.userId,
        channelId = id,
        body = dto.body ?: throw IllegalArgumentException("MESSAGE_BODY_REQUIRED"),
        messageType = dto.messageType,
      ),
    )

  @PostMapping("/channels/{id}/read")
  @RequiresPermission("communication:read:message")
  @Transactional
  fun markRead(
    @PathVariable id: UUID,
    @RequestBody(required = false) dto: MarkReadDto?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Long> = ApiResponse.ok(communicationService.markRead(principal.userId, id, dto?.beforeId))

  @GetMapping("/unread-count")
  @RequiresPermission("communication:read:message")
  @Transactional(readOnly = true)
  fun unreadCount(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Long> = ApiResponse.ok(communicationService.unreadCountAggregate(principal.userId))
}
