package com.squamed.core.identity.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 00.03 hardening (chunk-00 item 4): JWT signing-key rotation.
 * `keys` empty → legacy single-secret mode (no rotation). Non-empty → kid-keyed rotation:
 * tokens are signed with keys[activeKid] and carry that kid in the header; verification
 * pins the kid, and kid-less legacy tokens are tried against every configured key.
 */
@ConfigurationProperties(prefix = "squamed.security.jwt")
data class JwtKeyProperties(
  val secret: String,
  val keys: Map<String, String> = emptyMap(),
  val activeKid: String = "default",
)
