package com.squamed.core.identity.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.domain.Permission
import com.squamed.core.identity.domain.Role
import com.squamed.core.identity.repository.RoleRepository
import com.squamed.core.identity.security.RequiresPermission
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/identity/roles")
class RoleAdminController(
  private val roleRepository: RoleRepository,
) {
  data class RoleView(
    val id: UUID?,
    val name: String,
    val displayName: String,
    val description: String?,
    val isSystem: Boolean,
    val permissions: List<String>,
  ) {
    companion object {
      fun from(role: Role) =
        RoleView(
          id = role.id,
          name = role.name,
          displayName = role.displayName,
          description = role.description,
          isSystem = role.isSystem,
          permissions = role.permissions.map { it.code }.sorted(),
        )
    }
  }

  data class UpdateRoleDto(
    val permissions: List<String> = emptyList(),
  )

  @GetMapping
  @RequiresPermission("identity:read:role")
  @Transactional(readOnly = true)
  fun listRoles(): ApiResponse<List<RoleView>> {
    val roles = roleRepository.findAll().map { RoleView.from(it) }
    return ApiResponse.ok(roles)
  }

  @GetMapping("/{roleId}/permissions")
  @RequiresPermission("identity:read:role")
  @Transactional(readOnly = true)
  fun listRolePermissions(
    @PathVariable roleId: UUID,
  ): ApiResponse<List<String>> {
    val role = roleRepository.findById(roleId).orElseThrow { IllegalArgumentException("ROLE_NOT_FOUND") }
    return ApiResponse.ok(role.permissions.map { it.code }.sorted())
  }
}
