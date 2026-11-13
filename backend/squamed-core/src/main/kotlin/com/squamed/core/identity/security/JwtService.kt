package com.squamed.core.identity.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
  @Value("\${squamed.security.jwt.secret}") private val secret: String,
  @Value("\${squamed.security.jwt.access-token-ttl-minutes}") private val accessTtlMinutes: Long,
  @Value("\${squamed.security.jwt.refresh-token-ttl-days}") private val refreshTtlDays: Long,
) {
  private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

  fun generateAccessToken(
    userId: UUID,
    hospitalId: UUID?,
    sessionId: UUID,
  ): String =
    generateToken(
      subject = userId.toString(),
      claims =
        mapOf(
          "sid" to sessionId.toString(),
          "hp" to hospitalId?.toString(),
        ),
      ttl = Duration.ofMinutes(accessTtlMinutes),
    )

  fun generateRefreshToken(
    userId: UUID,
    sessionId: UUID,
  ): String =
    generateToken(
      subject = userId.toString(),
      claims = mapOf("sid" to sessionId.toString(), "typ" to "refresh"),
      ttl = Duration.ofDays(refreshTtlDays),
    )

  fun parseAccessToken(token: String): TokenClaims? =
    try {
      val claims =
        Jwts
          .parser()
          .verifyWith(key)
          .build()
          .parseSignedClaims(token)
          .payload
      val typ = claims["typ"] as? String
      if (typ == "refresh" || typ == "mfa") {
        null
      } else {
        TokenClaims.from(claims)
      }
    } catch (_: Exception) {
      null
    }

  fun parseRefreshToken(token: String): TokenClaims? =
    try {
      val claims =
        Jwts
          .parser()
          .verifyWith(key)
          .build()
          .parseSignedClaims(token)
          .payload
      if (claims["typ"] as? String != "refresh") {
        null
      } else {
        TokenClaims.from(claims)
      }
    } catch (_: Exception) {
      null
    }

  fun generateMfaToken(userId: UUID): String =
    generateToken(
      subject = userId.toString(),
      claims = mapOf("typ" to "mfa"),
      ttl = Duration.ofMinutes(10),
    )

  fun parseMfaToken(token: String): UUID? =
    try {
      val claims =
        Jwts
          .parser()
          .verifyWith(key)
          .build()
          .parseSignedClaims(token)
          .payload
      if (claims["typ"] as? String != "mfa") {
        null
      } else {
        UUID.fromString(claims.subject)
      }
    } catch (_: Exception) {
      null
    }

  private fun generateToken(
    subject: String,
    claims: Map<String, String?>,
    ttl: Duration,
  ): String {
    val now = Instant.now()
    val builder =
      Jwts
        .builder()
        .subject(subject)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
    claims.forEach { (k, v) -> if (v != null) builder.claim(k, v) }
    return builder.signWith(key).compact()
  }
}

data class TokenClaims(
  val userId: UUID,
  val sessionId: UUID,
  val hospitalId: UUID?,
  val issuedAt: Instant,
  val expiresAt: Instant,
) {
  companion object {
    fun from(claims: Claims): TokenClaims =
      TokenClaims(
        userId = UUID.fromString(claims.subject),
        sessionId = UUID.fromString(claims["sid"] as String),
        hospitalId = (claims["hp"] as? String)?.let(UUID::fromString),
        issuedAt = claims.issuedAt.toInstant(),
        expiresAt = claims.expiration.toInstant(),
      )
  }
}
