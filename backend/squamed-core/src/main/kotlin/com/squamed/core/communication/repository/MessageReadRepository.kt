package com.squamed.core.communication.repository

import com.squamed.core.communication.domain.MessageRead
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MessageReadRepository : JpaRepository<MessageRead, UUID> {
  fun findByMessageIdAndUserId(
    messageId: UUID,
    userId: UUID,
  ): MessageRead?

  fun existsByMessageIdAndUserId(
    messageId: UUID,
    userId: UUID,
  ): Boolean

  fun findByUserIdAndMessageIdIn(
    userId: UUID,
    messageIds: Collection<UUID>,
  ): List<MessageRead>
}
