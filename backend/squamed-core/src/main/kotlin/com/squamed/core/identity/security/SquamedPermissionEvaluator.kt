package com.squamed.core.identity.security

import com.squamed.core.identity.repository.UserRoleRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

@Service("squamedPermissionEvaluator")
class SquamedPermissionEvaluator(
  private val userRoleRepository: UserRoleRepository,
  private val redis: StringRedisTemplate,
  private val transactionTemplate: TransactionTemplate,
  @Value("\${squamed.security.permission-cache-ttl-seconds:60}") private val cacheTtlSeconds: Long,
) {
  companion object {
    private const val CACHE_PREFIX = "squamed:perm:"
  }

  fun check(authentication: Authentication): Boolean {
    val principal = authentication.principal as? SquamedPrincipal ?: return false
    return check(principal, null)
  }

  fun check(
    principal: SquamedPrincipal,
    required: String?,
  ): Boolean {
    if (required == null) return true
    val permissions = loadPermissions(principal.userId)
    val granted = permissions.any { matchPermission(it, required) }
    if (!granted) {
      logger.debug { "Permission denied for user ${principal.userId}: $required" }
    }
    return granted
  }

  fun hasRole(
    principal: SquamedPrincipal,
    roleName: String,
  ): Boolean {
    val roles = loadRoles(principal.userId)
    return roleName in roles
  }

  private fun loadPermissions(userId: UUID): Set<String> {
    val key = "$CACHE_PREFIX$userId"
    val cached = redis.opsForSet().members(key)
    if (cached != null && cached.isNotEmpty()) {
      return cached.map { it.toString() }.toSet()
    }
    val permissions =
      transactionTemplate.execute {
        userRoleRepository
          .findByUserId(userId)
          .flatMap { it.role.permissions }
          .map { it.code }
          .toSet()
      } ?: emptySet()
    if (permissions.isNotEmpty()) {
      redis.opsForSet().add(key, *permissions.toTypedArray())
      redis.expire(key, cacheTtlSeconds, TimeUnit.SECONDS)
    }
    return permissions
  }

  private fun loadRoles(userId: UUID): Set<String> =
    userRoleRepository
      .findByUserId(userId)
      .map { it.role.name }
      .toSet()

  private fun matchPermission(
    granted: String,
    required: String,
  ): Boolean {
    if (granted == required) return true
    val grantedParts = granted.split(":")
    val requiredParts = required.split(":")
    if (grantedParts.size < 2 || requiredParts.size < 2) return false
    if (grantedParts[0] != "*" && grantedParts[0] != requiredParts[0]) return false
    if (grantedParts[1] != "*" && grantedParts[1] != requiredParts[1]) return false
    if (grantedParts.size > 2 && requiredParts.size > 2 && grantedParts[2] != "*" && grantedParts[2] != requiredParts[2]) {
      return false
    }
    return true
  }
}
