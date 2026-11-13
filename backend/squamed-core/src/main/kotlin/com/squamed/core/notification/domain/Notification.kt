package com.squamed.core.notification.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "notification", schema = "notifications")
class Notification(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "recipient_id", nullable = false)
  var recipientId: UUID,
  @Column(name = "channel", nullable = false, length = 30)
  var channel: String = "in_app",
  @Column(name = "type", nullable = false, length = 50)
  var type: String,
  @Column(name = "title", nullable = false, length = 255)
  var title: String,
  @Column(name = "body")
  var body: String? = null,
  @Column(name = "payload")
  var payload: String? = null,
  @Column(name = "read_at")
  var readAt: Instant? = null,
  @Column(name = "delivered_at")
  var deliveredAt: Instant? = null,
) : BaseEntity()
