package com.squamed.core.notification.api

import com.squamed.core.notification.domain.Notification
import java.time.Instant
import java.util.UUID

data class NotificationView(
  val id: UUID?,
  val hospitalId: UUID?,
  val recipientId: UUID,
  val channel: String,
  val type: String,
  val title: String,
  val body: String?,
  val payload: String?,
  val readAt: Instant?,
  val deliveredAt: Instant?,
  val createdAt: Instant?,
) {
  companion object {
    fun from(entity: Notification) =
      NotificationView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        recipientId = entity.recipientId,
        channel = entity.channel,
        type = entity.type,
        title = entity.title,
        body = entity.body,
        payload = entity.payload,
        readAt = entity.readAt,
        deliveredAt = entity.deliveredAt,
        createdAt = entity.createdAt,
      )
  }
}
