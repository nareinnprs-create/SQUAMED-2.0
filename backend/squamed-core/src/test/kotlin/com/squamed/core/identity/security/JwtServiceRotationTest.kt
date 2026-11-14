package com.squamed.core.identity.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.crypto.SecretKey

class JwtServiceRotationTest {
  private val secretA = "rotation-secret-a-0123456789abcdef0123456789abcdef"
  private val secretB = "rotation-secret-b-0123456789abcdef0123456789abcdef"

  private fun service(
    keys: Map<String, String> = emptyMap(),
    secret: String = secretA,
    activeKid: String = "default",
  ) = JwtService(JwtKeyProperties(secret = secret, keys = keys, activeKid = activeKid), 15, 7)

  @Test
  fun `legacy mode roundtrips access token`() {
    val jwt = service()
    val userId = UUID.randomUUID()
    val sessionId = UUID.randomUUID()
    val token = jwt.generateAccessToken(userId, null, sessionId)
    val claims = jwt.parseAccessToken(token)
    assertNotNull(claims)
    assertEquals(userId, claims!!.userId)
    assertEquals(sessionId, claims.sessionId)
    assertTrue(jwt.parseRefreshToken(token) == null, "access token must not parse as refresh")
  }

  @Test
  fun `rotated key signs with active kid and verifies under it`() {
    val jwt = service(keys = mapOf("old" to secretA, "new" to secretB), activeKid = "new")
    val token = jwt.generateAccessToken(UUID.randomUUID(), null, UUID.randomUUID())
    assertNotNull(jwt.parseAccessToken(token), "token signed with active kid must verify")
  }

  @Test
  fun `unknown kid is rejected`() {
    val signing = service(keys = mapOf("old" to secretA, "new" to secretB), activeKid = "new")
    val token = signing.generateAccessToken(UUID.randomUUID(), null, UUID.randomUUID())
    // Verifier without the "new" kid configured → kid pinning must fail the token.
    val oldOnly = service(keys = mapOf("old" to secretA), activeKid = "old")
    assertNull(oldOnly.parseAccessToken(token), "unknown kid must fail verification")
  }

  @Test
  fun `signature mismatch under matching kid is rejected`() {
    val jwt = service(keys = mapOf("old" to secretA), activeKid = "old")
    val token = jwt.generateAccessToken(UUID.randomUUID(), null, UUID.randomUUID())
    val tampered = service(keys = mapOf("old" to secretB), activeKid = "old")
    assertNull(tampered.parseAccessToken(token))
  }

  @Test
  fun `kid-less legacy token verifies via try-all during rotation`() {
    // Mint a kid-less signed token exactly the way the pre-rotation build did.
    val key: SecretKey = Keys.hmacShaKeyFor(secretA.toByteArray(StandardCharsets.UTF_8))
    val now = java.time.Instant.now()
    val legacyToken =
      Jwts
        .builder()
        .subject(UUID.randomUUID().toString())
        .claim("sid", UUID.randomUUID().toString())
        .issuedAt(java.util.Date.from(now))
        .expiration(java.util.Date.from(now.plusSeconds(900)))
        .signWith(key)
        .compact()
    // Rotating verifier configured with the same secret under a kid: no kid header → try all keys.
    val rotating = service(keys = mapOf("old" to secretA, "new" to secretB), activeKid = "new")
    assertNotNull(rotating.parseAccessToken(legacyToken))
  }
}
