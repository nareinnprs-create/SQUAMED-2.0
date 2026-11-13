package com.squamed.core.identity.security

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

@Service
class TokenBlacklistService(
  private val redis: StringRedisTemplate,
) {
  companion object {
    private const val PREFIX = "squamed:auth:blacklist:"
    private const val SESSION_PREFIX = "squamed:auth:session:"
  }

  fun blacklistAccessToken(
    jti: String,
    userId: UUID,
    ttl: Duration,
  ) {
    redis.opsForValue().set("$PREFIX$jti", userId.toString(), ttl)
  }

  fun revokeSession(
    sessionId: UUID,
    refreshHash: String,
    ttl: Duration,
  ) {
    redis.opsForValue().set("$SESSION_PREFIX$sessionId", "revoked", ttl)
    blacklistAccessToken(refreshHash, UUID.randomUUID(), ttl)
  }

  fun isTokenBlacklisted(jti: String): Boolean = redis.hasKey("$PREFIX$jti") == true

  fun isSessionRevoked(sessionId: UUID): Boolean = redis.hasKey("$SESSION_PREFIX$sessionId") == true
}
