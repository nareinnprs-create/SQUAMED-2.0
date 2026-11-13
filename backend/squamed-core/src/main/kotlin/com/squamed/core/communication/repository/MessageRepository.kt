package com.squamed.core.communication.repository

import com.squamed.core.communication.domain.Message
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface MessageRepository : JpaRepository<Message, UUID> {
  fun findByChannelIdOrderByCreatedAtDesc(
    channelId: UUID,
    pageable: Pageable,
  ): List<Message>

  fun findByChannelIdOrderByCreatedAtAsc(
    channelId: UUID,
    pageable: Pageable,
  ): List<Message>

  fun findTop1ByChannelIdOrderByCreatedAtDesc(channelId: UUID): Message?

  fun countByChannelIdAndCreatedAtAfterAndSenderIdNot(
    channelId: UUID,
    createdAt: Instant,
    senderId: UUID,
  ): Long
}
