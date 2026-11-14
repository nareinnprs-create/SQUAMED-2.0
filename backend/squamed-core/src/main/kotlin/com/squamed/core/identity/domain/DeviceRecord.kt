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
  name = "device",
  schema = "identity",
  indexes = [
    Index(name = "idx_identity_device_user", columnList = "user_id"),
  ],
)
class DeviceRecord(
  @Column(name = "user_id", nullable = false)
  var userId: UUID,
  // Server-side device identity: SHA-256 of the login User-Agent (no client-side fingerprinting).
  @Column(name = "fingerprint", nullable = false, length = 64)
  var fingerprint: String,
  @Column(name = "label", length = 255)
  var label: String? = null,
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "first_seen_at", nullable = false)
  var firstSeenAt: Instant = Instant.now(),
  @Column(name = "last_seen_at", nullable = false)
  var lastSeenAt: Instant = Instant.now(),
  @Column(name = "revoked_at")
  var revokedAt: Instant? = null,
) : BaseEntity() {
  fun isRevoked(): Boolean = revokedAt != null
}
