package com.squamed.core.communication.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "message_read", schema = "communication")
class MessageRead(
  @Column(name = "message_id", nullable = false)
  var messageId: UUID,
  @Column(name = "user_id", nullable = false)
  var userId: UUID,
  @Column(name = "read_at")
  var readAt: Instant? = null,
) : BaseEntity()
