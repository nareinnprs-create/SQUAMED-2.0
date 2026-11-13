package com.squamed.core.communication.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "channel_member", schema = "communication")
class ChannelMember(
  @Column(name = "channel_id", nullable = false)
  var channelId: UUID,
  @Column(name = "user_id", nullable = false)
  var userId: UUID,
  @Column(name = "role", nullable = false, length = 20)
  var role: String = "member",
  @Column(name = "last_read_at")
  var lastReadAt: Instant? = null,
  @Column(name = "muted_until")
  var mutedUntil: Instant? = null,
  @Column(name = "notifications_enabled", nullable = false)
  var notificationsEnabled: Boolean = true,
) : BaseEntity()
