package com.squamed.core.notification.api

import com.squamed.core.notification.domain.Notification
import com.squamed.core.notification.domain.NotificationRequest
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

/** Chunk-00 item 12 `/notifications:send`: outbound dispatch request. */
data class NotificationDispatchDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val recipientId: UUID? = null,
  val recipientAddress: String? = null,
  val channel: String? = null,
  val type: String? = null,
  val title: String? = null,
  val body: String? = null,
  val payload: String? = null,
)

/** Dispatch ledger view: one outbound attempt with its gate outcome (consent/DND/provider). */
data class NotificationRequestView(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID?,
  val recipientId: UUID?,
  val recipientAddress: String?,
  val channel: String,
  val type: String,
  val title: String,
  val body: String?,
  val status: String,
  val providerRef: String?,
  val errorMessage: String?,
  val createdAt: Instant?,
) {
  companion object {
    fun from(request: NotificationRequest): NotificationRequestView =
      NotificationRequestView(
        id = request.id,
        hospitalId = request.hospitalId,
        patientId = request.patientId,
        recipientId = request.recipientId,
        recipientAddress = request.recipientAddress,
        channel = request.channel,
        type = request.type,
        title = request.title,
        body = request.body,
        status = request.status,
        providerRef = request.providerRef,
        errorMessage = request.errorMessage,
        createdAt = request.createdAt,
      )
  }
}
