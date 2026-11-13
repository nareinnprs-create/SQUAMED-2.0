package com.squamed.core.identity.repository

import com.squamed.core.identity.domain.UserProfile
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface UserProfileRepository : JpaRepository<UserProfile, UUID> {
  fun findByUserId(userId: UUID): Optional<UserProfile>
}
