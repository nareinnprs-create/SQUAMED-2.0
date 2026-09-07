package com.squamed.core.communication.api

import com.squamed.core.communication.domain.Channel
import com.squamed.core.communication.domain.ChannelMember
import com.squamed.core.communication.domain.Message
import java.time.Instant
import java.util.UUID

data class ChannelCreateDto(
  val name: String? = null,
  val type: String? = null,
  val description: String? = null,
  val memberIds: List<UUID>? = null,
  val patientId: UUID? = null,
  val encounterId: UUID? = null,
)

data class MemberAddDto(
  val userId: UUID? = null,
)

data class MessageCreateDto(
  val body: String? = null,
  val messageType: String? = null,
)

data class MarkReadDto(
  val beforeId: UUID? = null,
)

data class ChannelView(
  val id: UUID?,
  val hospitalId: UUID?,
  val name: String,
  val type: String,
  val description: String?,
  val ownerId: UUID?,
  val patientId: UUID?,
  val encounterId: UUID?,
  val isArchived: Boolean,
  val createdAt: Instant?,
  val updatedAt: Instant?,
) {
  companion object {
    fun from(entity: Channel) =
      ChannelView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        name = entity.name,
        type = entity.type,
        description = entity.description,
        ownerId = entity.ownerId,
        patientId = entity.patientId,
        encounterId = entity.encounterId,
        isArchived = entity.isArchived,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
      )
  }
}

data class ChannelMemberView(
  val id: UUID?,
  val channelId: UUID,
  val userId: UUID,
  val role: String,
  val lastReadAt: Instant?,
  val mutedUntil: Instant?,
  val notificationsEnabled: Boolean,
) {
  companion object {
    fun from(entity: ChannelMember) =
      ChannelMemberView(
        id = entity.id,
        channelId = entity.channelId,
        userId = entity.userId,
        role = entity.role,
        lastReadAt = entity.lastReadAt,
        mutedUntil = entity.mutedUntil,
        notificationsEnabled = entity.notificationsEnabled,
      )
  }
}

data class MessageView(
  val id: UUID?,
  val channelId: UUID,
  val senderId: UUID,
  val senderName: String?,
  val body: String,
  val messageType: String,
  val attachmentCount: Int,
  val createdAt: Instant?,
  val editedAt: Instant?,
  val deletedAt: Instant?,
) {
  companion object {
    fun from(
      entity: Message,
      senderName: String?,
    ) = MessageView(
      id = entity.id,
      channelId = entity.channelId,
      senderId = entity.senderId,
      senderName = senderName,
      body = entity.body,
      messageType = entity.messageType,
      attachmentCount = entity.attachmentCount,
      createdAt = entity.createdAt,
      editedAt = entity.editedAt,
      deletedAt = entity.deletedAt,
    )
  }
}

data class ChannelSummaryView(
  val id: UUID?,
  val name: String,
  val type: String,
  val description: String?,
  val lastMessagePreview: String?,
  val lastMessageAt: Instant?,
  val unreadCount: Long,
  val memberCount: Long,
)

data class ChannelDetailView(
  val channel: ChannelView,
  val members: List<ChannelMemberView>,
  val unreadCount: Long,
)
