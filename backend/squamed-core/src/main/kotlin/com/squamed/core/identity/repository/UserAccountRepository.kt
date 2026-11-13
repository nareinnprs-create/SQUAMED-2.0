package com.squamed.core.identity.repository

import com.squamed.core.identity.domain.UserAccount
import com.squamed.core.identity.domain.UserRole
import com.squamed.core.identity.domain.UserStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface UserAccountRepository : JpaRepository<UserAccount, UUID> {
  fun findByEmailIgnoreCase(email: String): Optional<UserAccount>

  fun findByPhone(phone: String): Optional<UserAccount>

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
