package com.squamed.core.identity.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(
  name = "session",
  schema = "identity",
  indexes = [
    Index(name = "idx_identity_session_user", columnList = "user_id"),
    Index(name = "idx_identity_session_token", columnList = "token_hash"),
  ],
)
class SessionRecord(
  @Column(name = "user_id", nullable = false)
  var userId: UUID,
  @Column(name = "token_hash", nullable = false, length = 255)
  var tokenHash: String,
  @Column(name = "refresh_token_hash", nullable = false, length = 255)
  var refreshTokenHash: String,
  @Column(name = "ip_address", length = 45)
  var ipAddress: String? = null,
  @Column(name = "user_agent", columnDefinition = "text")
  var userAgent: String? = null,
  @Column(name = "device_id")
  var deviceId: UUID? = null,
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "expires_at", nullable = false)
  var expiresAt: Instant,
  @Column(name = "revoked_at")
  var revokedAt: Instant? = null,
) : BaseEntity() {
  fun isRevoked(): Boolean = revokedAt != null

  fun isExpired(now: Instant = Instant.now()): Boolean = expiresAt.isBefore(now)
}
