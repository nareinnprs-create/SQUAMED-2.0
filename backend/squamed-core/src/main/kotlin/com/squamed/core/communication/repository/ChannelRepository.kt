package com.squamed.core.communication.repository

import com.squamed.core.communication.domain.Channel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ChannelRepository : JpaRepository<Channel, UUID> {
  fun findByHospitalId(hospitalId: UUID): List<Channel>

  @Query(
    value =
      "SELECT c.* FROM communication.channel c " +
        "INNER JOIN communication.channel_member cm ON cm.channel_id = c.id " +
        "WHERE cm.user_id = :userId",
    nativeQuery = true,
  )
  fun findByMemberUserId(userId: UUID): List<Channel>
}
