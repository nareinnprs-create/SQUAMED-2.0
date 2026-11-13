package com.squamed.core.identity.repository

import com.squamed.core.identity.domain.Role
import com.squamed.core.identity.domain.UserRole
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface RoleRepository : JpaRepository<Role, UUID> {
  fun findByName(name: String): Optional<Role>
}

interface UserRoleRepository : JpaRepository<UserRole, UUID> {
  fun findByUserId(userId: UUID): List<UserRole>
}
