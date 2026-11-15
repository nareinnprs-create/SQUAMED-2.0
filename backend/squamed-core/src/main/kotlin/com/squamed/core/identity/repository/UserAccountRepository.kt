package com.squamed.core.identity.repository

import com.squamed.core.identity.domain.UserAccount
import com.squamed.core.identity.domain.UserRole
import com.squamed.core.identity.domain.UserStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional
import java.util.UUID

interface UserAccountRepository : JpaRepository<UserAccount, UUID> {
  fun findByEmailIgnoreCase(email: String): Optional<UserAccount>

  fun findByPhone(phone: String): Optional<UserAccount>

  // Bulk update instead of a managed-entity save: login bookkeeping is
  // last-write-wins, and a versioned save 500s when two logins on the same
  // account race (proven by concurrent admin logins during load tests).
  @Modifying
  @Query(
    "update UserAccount u set u.failedLoginAttempts = 0, u.lockedUntil = null, " +
      "u.lastLoginAt = :now where u.id = :id",
  )
  fun recordSuccessfulLogin(
    @Param("id") id: UUID,
    @Param("now") now: Instant,
  ): Int

  @Modifying
  @Query("update UserAccount u set u.failedLoginAttempts = u.failedLoginAttempts + 1 where u.id = :id")
  fun recordFailedLogin(
    @Param("id") id: UUID,
  ): Int

  @Query("select u.failedLoginAttempts from UserAccount u where u.id = :id")
  fun failedLoginAttempts(
    @Param("id") id: UUID,
  ): Int

  @Modifying
  @Query("update UserAccount u set u.lockedUntil = :until where u.id = :id")
  fun lockAccount(
    @Param("id") id: UUID,
    @Param("until") until: Instant,
  ): Int

  @Query(
    "select ur from UserRole ur " +
      "join fetch ur.role r " +
      "left join fetch r.permissions " +
      "where ur.userId = :userId " +
      "and (ur.validFrom is null or ur.validFrom <= current_timestamp) " +
      "and (ur.validUntil is null or ur.validUntil >= current_timestamp)",
  )
  fun findActiveRolesForUser(
    @Param("userId") userId: UUID,
  ): List<UserRole>
}
