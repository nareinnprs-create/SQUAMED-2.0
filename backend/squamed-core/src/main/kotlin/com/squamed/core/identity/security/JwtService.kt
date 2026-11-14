package com.squamed.core.identity.security

import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * 00.03 hardening (chunk-00 item 4): kid-pinned signing-key rotation.
 * Sign with the active kid; verify pins kid → key (unknown kid fails), and
 * kid-less tokens minted before a rotation are tried against every configured key.
 */
@Service
class JwtService(
  private val jwtProperties: JwtKeyProperties,
  @Value("\${squamed.security.jwt.access-token-ttl-minutes}") private val accessTtlMinutes: Long,
  @Value("\${squamed.security.jwt.refresh-token-ttl-days}") private val refreshTtlDays: Long,
) {
  private val objectMapper = ObjectMapper()

  // Legacy single-secret mode surfaces as one key under the default kid.
  private val keysById: Map<String, SecretKey> =
    if (jwtProperties.keys.isNotEmpty()) {
      jwtProperties.keys.mapValues { (_, secret) ->
        Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
      }
    } else {
      mapOf(DEFAULT_KID to Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray(StandardCharsets.UTF_8)))
    }

  private val signingKey: SecretKey =
    if (jwtProperties.keys.isNotEmpty()) {
      keysById[jwtProperties.activeKid]
        ?: error("squamed.security.jwt.keys does not contain active-kid '${jwtProperties.activeKid}'")
    } else {
      keysById.values.first()
    }

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
    parse(token)?.let { claims ->
      val typ = claims["typ"] as? String
      if (typ == "refresh" || typ == "mfa") {
        null
      } else {
        TokenClaims.from(claims)
      }
    }

  fun parseRefreshToken(token: String): TokenClaims? =
    parse(token)?.let { claims ->
      if (claims["typ"] as? String != "refresh") {
        null
      } else {
        TokenClaims.from(claims)
      }
    }

  fun generateMfaToken(userId: UUID): String =
    generateToken(
      subject = userId.toString(),
      claims = mapOf("typ" to "mfa"),
      ttl = Duration.ofMinutes(10),
    )

  fun parseMfaToken(token: String): UUID? =
    parse(token)?.let { claims ->
      if (claims["typ"] as? String != "mfa") {
        null
      } else {
        UUID.fromString(claims.subject)
      }
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
        .header()
        .keyId(jwtProperties.activeKid)
        .and()
        .subject(subject)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
    claims.forEach { (k, v) -> if (v != null) builder.claim(k, v) }
    return builder.signWith(signingKey).compact()
  }

  private fun parse(token: String): Claims? =
    try {
      val kid = peekKid(token)
      when {
        kid != null -> {
          keysById[kid]?.let { key ->
            verifyWith(key, token)
          }
        }

        // Kid-less legacy token: try every configured key.
        else -> {
          keysById.values.firstNotNullOfOrNull { key -> verifyWith(key, token) }
        }
      }
    } catch (_: Exception) {
      null
    }

  private fun verifyWith(
    key: SecretKey,
    token: String,
  ): Claims? =
    try {
      Jwts
        .parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .payload
    } catch (_: Exception) {
      null
    }

  // jjwt 0.12.6 has no signed-token header peek: split, Base64url-decode part 0, read JSON.
  private fun peekKid(token: String): String? =
    runCatching {
      val headerPart = token.substringBefore('.', "").ifEmpty { return null }
      val json = String(Base64.getUrlDecoder().decode(headerPart), StandardCharsets.UTF_8)
      objectMapper.readTree(json).get("kid")?.asText()
    }.getOrNull()

  private companion object {
    const val DEFAULT_KID = "default"
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
