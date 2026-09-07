package com.squamed.core.notification.repository

import com.squamed.core.notification.domain.Notification
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID> {
  fun findByRecipientIdOrderByCreatedAtDesc(recipientId: UUID): List<Notification>

  fun findByRecipientIdAndReadAtIsNull(recipientId: UUID): List<Notification>
}
