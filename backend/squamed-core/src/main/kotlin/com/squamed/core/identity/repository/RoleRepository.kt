package com.squamed.core.identity.repository

import com.squamed.core.identity.domain.Role
import com.squamed.core.identity.domain.UserRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface RoleRepository : JpaRepository<Role, UUID> {
  fun findByName(name: String): Optional<Role>
}

interface UserRoleRepository : JpaRepository<UserRole, UUID> {
  fun findByUserId(userId: UUID): List<UserRole>

  /** Escalation targets: users holding a role inside a hospital (00.11 SLA config `escalateToRole`). */
  @Query("select ur.userId from UserRole ur where ur.hospitalId = :hospitalId and ur.role.name = :roleName")
  fun findUserIdsByHospitalIdAndRoleName(
    @Param("hospitalId") hospitalId: UUID,
    @Param("roleName") roleName: String,
  ): List<UUID>

  // 06.01 group directory: every role assignment inside a hospital.
  fun findByHospitalId(hospitalId: UUID): List<UserRole>
}
