package com.squamed.core.identity.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "\"user\"", schema = "identity")
class UserAccount(
  @Column(name = "email", nullable = false, unique = true, length = 255)
  var email: String,
  @Column(name = "phone", length = 20)
  var phone: String? = null,
  @Column(name = "password_hash", nullable = false, length = 255)
  var passwordHash: String,
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  var status: UserStatus = UserStatus.PENDING,
  @Column(name = "mfa_enabled", nullable = false)
  var mfaEnabled: Boolean = false,
  @Column(name = "mfa_secret", length = 255)
  var mfaSecret: String? = null,
  @Column(name = "last_login_at")
  var lastLoginAt: Instant? = null,
  @Column(name = "failed_login_attempts", nullable = false)
  var failedLoginAttempts: Int = 0,
  @Column(name = "locked_until")
  var lockedUntil: Instant? = null,
) : BaseEntity() {
  fun isLocked(): Boolean {
    val until = lockedUntil ?: return false
    return until.isAfter(Instant.now())
  }
}

enum class UserStatus {
  PENDING,
  ACTIVE,
  DISABLED,
  LOCKED,
  TERMINATED,
}
