package com.squamed.core.communication.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "message", schema = "communication")
class Message(
  @Column(name = "channel_id", nullable = false)
  var channelId: UUID,
  @Column(name = "sender_id", nullable = false)
  var senderId: UUID,
  @Column(name = "body", nullable = false)
  var body: String,
  @Column(name = "message_type", nullable = false, length = 20)
  var messageType: String = "text",
  @Column(name = "attachment_count", nullable = false)
  var attachmentCount: Int = 0,
  @Column(name = "edited_at")
  var editedAt: Instant? = null,
  @Column(name = "deleted_at")
  var deletedAt: Instant? = null,
) : BaseEntity()
