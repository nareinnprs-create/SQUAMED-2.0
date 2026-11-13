package com.squamed.core.notification.service

import com.squamed.core.notification.api.NotificationView
import com.squamed.core.notification.domain.Notification
import com.squamed.core.notification.repository.NotificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class NotificationService(
  private val notificationRepository: NotificationRepository,
) {
  @Transactional
  fun create(
    recipientId: UUID,
    type: String,
    title: String,
    body: String?,
    payload: String? = null,
    channel: String = "in_app",
    hospitalId: UUID? = null,
  ): Notification =
    notificationRepository.save(
      Notification(
        hospitalId = hospitalId,
        recipientId = recipientId,
        channel = channel,
        type = type,
        title = title,
        body = body,
        payload = payload,
        deliveredAt = Instant.now(),
      ),
    )

  @Transactional(readOnly = true)
  fun listForUser(
    recipientId: UUID,
    unreadOnly: Boolean?,
  ): List<NotificationView> {
    val notifications =
      if (unreadOnly == true) {
        notificationRepository.findByRecipientIdAndReadAtIsNull(recipientId)
      } else {
        notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId)
      }
    return notifications.sortedByDescending { it.createdAt ?: Instant.EPOCH }.map { NotificationView.from(it) }
  }

  @Transactional
  fun markRead(
    id: UUID,
    recipientId: UUID,
  ): NotificationView {
    val notification =
      notificationRepository
        .findById(id)
        .orElseThrow { IllegalArgumentException("NOTIFICATION_NOT_FOUND") }
    if (notification.recipientId != recipientId) {
      throw IllegalArgumentException("NOTIFICATION_NOT_FOUND")
    }
    if (notification.readAt == null) {
      notification.readAt = Instant.now()
      notificationRepository.save(notification)
    }
    return NotificationView.from(notification)
  }

  @Transactional
  fun markAllRead(recipientId: UUID): Long {
    val unread = notificationRepository.findByRecipientIdAndReadAtIsNull(recipientId)
    if (unread.isEmpty()) return 0L
    val now = Instant.now()
    unread.forEach { it.readAt = now }
    notificationRepository.saveAll(unread)
    return unread.size.toLong()
  }

  @Transactional(readOnly = true)
  fun countUnread(recipientId: UUID): Long = notificationRepository.findByRecipientIdAndReadAtIsNull(recipientId).size.toLong()
}
