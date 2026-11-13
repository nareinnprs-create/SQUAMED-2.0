package com.squamed.core.communication.repository

import com.squamed.core.communication.domain.ChannelMember
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ChannelMemberRepository : JpaRepository<ChannelMember, UUID> {
  fun findByUserId(userId: UUID): List<ChannelMember>

  fun findByChannelId(channelId: UUID): List<ChannelMember>

  fun findByChannelIdAndUserId(
    channelId: UUID,
    userId: UUID,
  ): ChannelMember?

  fun existsByChannelIdAndUserId(
    channelId: UUID,
    userId: UUID,
  ): Boolean

  fun countByChannelId(channelId: UUID): Long
}
