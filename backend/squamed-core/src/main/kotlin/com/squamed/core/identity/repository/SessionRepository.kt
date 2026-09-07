package com.squamed.core.identity.repository

import com.squamed.core.identity.domain.SessionRecord
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface SessionRepository : JpaRepository<SessionRecord, UUID> {
  fun findByTokenHash(tokenHash: String): Optional<SessionRecord>

  fun findByRefreshTokenHash(refreshTokenHash: String): Optional<SessionRecord>

  fun findByUserIdAndRevokedAtIsNull(userId: UUID): List<SessionRecord>
}
