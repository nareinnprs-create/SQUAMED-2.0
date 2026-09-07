package com.squamed.core.communication.service

import com.squamed.core.communication.api.ChannelDetailView
import com.squamed.core.communication.api.ChannelMemberView
import com.squamed.core.communication.api.ChannelSummaryView
import com.squamed.core.communication.api.ChannelView
import com.squamed.core.communication.api.MessageView
import com.squamed.core.communication.domain.Channel
import com.squamed.core.communication.domain.ChannelMember
import com.squamed.core.communication.domain.Message
import com.squamed.core.communication.domain.MessageRead
import com.squamed.core.communication.repository.ChannelMemberRepository
import com.squamed.core.communication.repository.ChannelRepository
import com.squamed.core.communication.repository.MessageReadRepository
import com.squamed.core.communication.repository.MessageRepository
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.events.gateway.RealtimeGateway
import com.squamed.core.identity.repository.UserProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class CommunicationService(
  private val channelRepository: ChannelRepository,
  private val channelMemberRepository: ChannelMemberRepository,
  private val messageRepository: MessageRepository,
  private val messageReadRepository: MessageReadRepository,
  private val userProfileRepository: UserProfileRepository,
  private val eventPublisher: DomainEventPublisher,
  private val realtimeGateway: RealtimeGateway,
) {
  private val logger = LoggerFactory.getLogger(CommunicationService::class.java)

  @Transactional
  fun createChannel(
    creatorId: UUID,
    hospitalId: UUID?,
    name: String,
    type: String,
    memberIds: List<UUID>,
    description: String?,
    patientId: UUID?,
    encounterId: UUID?,
  ): ChannelView {
    val members = memberIds.distinct()
    if (type != "hospital" && members.isEmpty()) {
      throw IllegalArgumentException("CHANNEL_MEMBERS_REQUIRED")
    }
    val channel =
      channelRepository.save(
        Channel(
          hospitalId = hospitalId,
          name = name,
          type = type,
          description = description,
          ownerId = creatorId,
          patientId = patientId,
          encounterId = encounterId,
        ),
      )
    val channelId = channel.id ?: throw IllegalStateException("CHANNEL_ID_MISSING")
    val memberships =
      (members + creatorId).distinct().map { uid ->
        ChannelMember(
          channelId = channelId,
          userId = uid,
          role = if (uid == creatorId) "admin" else "member",
        )
      }
    channelMemberRepository.saveAll(memberships)
    return ChannelView.from(channel)
  }

  @Transactional
  fun createDirectChannel(
    a: UUID,
    b: UUID,
  ): ChannelView {
    findDirectChannel(a, b)?.let { return ChannelView.from(it) }
    return createChannel(
      creatorId = a,
      hospitalId = null,
      name = "Direct",
      type = "direct",
      memberIds = listOf(b),
      description = null,
      patientId = null,
      encounterId = null,
    )
  }

  @Transactional
  fun addMember(
    userId: UUID,
    channelId: UUID,
    newUserId: UUID,
  ): ChannelMemberView {
    findChannel(channelId)
    requireMember(channelId, userId)
    return channelMemberRepository.findByChannelIdAndUserId(channelId, newUserId)?.let { ChannelMemberView.from(it) }
      ?: ChannelMemberView.from(
        channelMemberRepository.save(
          ChannelMember(
            channelId = channelId,
            userId = newUserId,
            role = "member",
          ),
        ),
      )
  }

  @Transactional
  fun removeMember(
    userId: UUID,
    channelId: UUID,
    targetUserId: UUID,
  ) {
    val channel = findChannel(channelId)
    requireMember(channelId, userId)
    if (channel.ownerId == targetUserId) {
      throw IllegalStateException("CHANNEL_OWNER_CANNOT_REMOVE")
    }
    val membership =
      channelMemberRepository.findByChannelIdAndUserId(channelId, targetUserId)
        ?: throw IllegalStateException("NOT_CHANNEL_MEMBER")
    channelMemberRepository.delete(membership)
  }

  @Transactional
  fun sendMessage(
    userId: UUID,
    channelId: UUID,
    body: String,
    messageType: String?,
  ): MessageView {
    if (body.isBlank()) {
      throw IllegalArgumentException("MESSAGE_BODY_REQUIRED")
    }
    requireMember(channelId, userId)
    val channel = findChannel(channelId)
    val message =
      messageRepository.save(
        Message(
          channelId = channelId,
          senderId = userId,
          body = body,
          messageType = messageType ?: "text",
        ),
      )
    val recipientIds = channelMemberRepository.findByChannelId(channelId).map { it.userId }.minus(userId)
    if (recipientIds.isNotEmpty()) {
      eventPublisher.publish(
        DomainEvent(
          eventId = UUID.randomUUID(),
          eventType = "message.sent",
          occurredAt = Instant.now(),
          source = "communication-service",
          entityType = "channel",
          entityId = channelId,
          hospitalId = channel.hospitalId,
          payload =
            mapOf(
              "channelId" to channelId.toString(),
              "senderId" to userId.toString(),
              "senderName" to senderName(userId),
              "preview" to body.take(120),
              "body" to body,
            ),
          recipientIds = recipientIds,
        ),
      )
      val view = messageView(message)
      try {
        realtimeGateway.sendToUsers(recipientIds, mapOf("type" to "message.sent", "message" to view))
      } catch (e: Exception) {
        logger.debug("Failed to push message.sent frame for channel={}", channelId, e)
      }
    }
    return messageView(message)
  }

  @Transactional
  fun markRead(
    userId: UUID,
    channelId: UUID,
    beforeIdOrNull: UUID?,
  ): Long {
    val member =
      channelMemberRepository.findByChannelIdAndUserId(channelId, userId)
        ?: throw IllegalStateException("NOT_CHANNEL_MEMBER")
    var candidates = messageRepository.findByChannelIdOrderByCreatedAtAsc(channelId, PageRequest.of(0, 500))
    beforeIdOrNull?.let { beforeId ->
      val cursor = messageRepository.findById(beforeId).orElse(null)
      if (cursor != null) {
        val cutoff = cursor.createdAt ?: Instant.MAX
        candidates = candidates.filter { (it.createdAt ?: Instant.EPOCH) <= cutoff }
      }
    }
    if (candidates.isEmpty()) return 0L
    val existingReadIds =
      messageReadRepository
        .findByUserIdAndMessageIdIn(userId, candidates.mapNotNull { it.id })
        .map { it.messageId }
        .toSet()
    val newReads =
      candidates
        .filter { it.id != null && it.id !in existingReadIds }
        .map { MessageRead(messageId = it.id!!, userId = userId, readAt = Instant.now()) }
    messageReadRepository.saveAll(newReads)
    member.lastReadAt = Instant.now()
    channelMemberRepository.save(member)
    return newReads.size.toLong()
  }

  @Transactional(readOnly = true)
  fun listMyChannels(userId: UUID): List<ChannelSummaryView> {
    val memberships = channelMemberRepository.findByUserId(userId)
    if (memberships.isEmpty()) return emptyList()
    val membershipByChannel = memberships.associateBy { it.channelId }
    return channelRepository.findAllById(membershipByChannel.keys).map { channel ->
      summaryView(userId, channel, membershipByChannel[channel.id!!])
    }
  }

  @Transactional(readOnly = true)
  fun channelDetail(
    userId: UUID,
    channelId: UUID,
  ): ChannelDetailView {
    val channel = findChannel(channelId)
    requireMember(channelId, userId)
    val members = channelMemberRepository.findByChannelId(channelId).map { ChannelMemberView.from(it) }
    val member = channelMemberRepository.findByChannelIdAndUserId(channelId, userId)
    val unreadCount =
      messageRepository.countByChannelIdAndCreatedAtAfterAndSenderIdNot(
        channelId,
        member?.lastReadAt ?: Instant.EPOCH,
        userId,
      )
    return ChannelDetailView(
      channel = ChannelView.from(channel),
      members = members,
      unreadCount = unreadCount,
    )
  }

  @Transactional(readOnly = true)
  fun listMessages(
    userId: UUID,
    channelId: UUID,
    limit: Int = 50,
    beforeId: UUID? = null,
  ): List<MessageView> {
    requireMember(channelId, userId)
    val messages = messageRepository.findByChannelIdOrderByCreatedAtDesc(channelId, PageRequest.of(0, limit))
    val filtered =
      beforeId?.let { cursorId ->
        val cursor = messageRepository.findById(cursorId).orElse(null)
        if (cursor != null) {
          messages.filter { (it.createdAt ?: Instant.EPOCH) < (cursor.createdAt ?: Instant.EPOCH) }
        } else {
          messages
        }
      } ?: messages
    return filtered.map { messageView(it) }
  }

  @Transactional(readOnly = true)
  fun listMembers(
    userId: UUID,
    channelId: UUID,
  ): List<ChannelMemberView> {
    requireMember(channelId, userId)
    return channelMemberRepository.findByChannelId(channelId).map { ChannelMemberView.from(it) }
  }

  @Transactional(readOnly = true)
  fun unreadCountAggregate(userId: UUID): Long {
    val memberships = channelMemberRepository.findByUserId(userId)
    if (memberships.isEmpty()) return 0L
    val lastReadByChannel = memberships.associate { it.channelId to (it.lastReadAt ?: Instant.EPOCH) }
    return channelRepository.findAllById(memberships.map { it.channelId }).sumOf { channel ->
      messageRepository.countByChannelIdAndCreatedAtAfterAndSenderIdNot(
        channel.id!!,
        lastReadByChannel[channel.id!!] ?: Instant.EPOCH,
        userId,
      )
    }
  }

  private fun findDirectChannel(
    a: UUID,
    b: UUID,
  ): Channel? {
    val channelIds = channelMemberRepository.findByUserId(a).map { it.channelId }
    if (channelIds.isEmpty()) return null
    return channelRepository
      .findAllById(channelIds)
      .filter { it.type == "direct" }
      .firstOrNull { c -> c.id != null && channelMemberRepository.existsByChannelIdAndUserId(c.id!!, b) }
  }

  private fun summaryView(
    userId: UUID,
    channel: Channel,
    member: ChannelMember?,
  ): ChannelSummaryView {
    val channelId = channel.id ?: throw IllegalStateException("CHANNEL_ID_MISSING")
    val lastMessage = messageRepository.findTop1ByChannelIdOrderByCreatedAtDesc(channelId)
    return ChannelSummaryView(
      id = channel.id,
      name = channel.name,
      type = channel.type,
      description = channel.description,
      lastMessagePreview = lastMessage?.body?.take(120),
      lastMessageAt = lastMessage?.createdAt,
      unreadCount =
        messageRepository.countByChannelIdAndCreatedAtAfterAndSenderIdNot(
          channelId,
          member?.lastReadAt ?: Instant.EPOCH,
          userId,
        ),
      memberCount = channelMemberRepository.countByChannelId(channelId),
    )
  }

  private fun findChannel(channelId: UUID): Channel = channelRepository.findById(channelId).orElseThrow { IllegalArgumentException("CHANNEL_NOT_FOUND") }

  private fun requireMember(
    channelId: UUID,
    userId: UUID,
  ) {
    if (!channelMemberRepository.existsByChannelIdAndUserId(channelId, userId)) {
      throw IllegalStateException("NOT_CHANNEL_MEMBER")
    }
  }

  private fun messageView(message: Message): MessageView = MessageView.from(message, senderName(message.senderId))

  private fun senderName(userId: UUID): String? = userProfileRepository.findByUserId(userId).map { it.effectiveDisplayName }.orElse(null)
}
