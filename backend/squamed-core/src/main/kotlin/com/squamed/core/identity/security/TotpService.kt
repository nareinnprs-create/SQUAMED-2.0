package com.squamed.core.identity.security

import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.DefaultCodeVerifier
import dev.samstevens.totp.code.HashingAlgorithm
import dev.samstevens.totp.time.SystemTimeProvider
import dev.samstevens.totp.time.TimeProvider
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64

/**
 * 00.03 hardening (chunk-00 item 4): delegates RFC 6238 code math to the vetted
 * samstevens totp library instead of the hand-rolled HMAC implementation.
 * Stored secrets stay base64 (UserAccount.mfaSecret); the boundary re-encodes to base32.
 */
@Component
class TotpService(
  private val timeProvider: TimeProvider = SystemTimeProvider(),
) {
  companion object {
    private const val SECRET_BYTES = 20
    private const val DIGITS = 6
    private const val PERIOD_SECONDS = 30
  }

  private val generator = DefaultCodeGenerator(HashingAlgorithm.SHA1, DIGITS)

  fun generateSecret(): String {
    val bytes = ByteArray(SECRET_BYTES).also { SecureRandom().nextBytes(it) }
    return Base64.getEncoder().encodeToString(bytes)
  }

  fun validateCode(
    secretBase64: String,
    code: String,
    window: Int = 1,
  ): Boolean {
    val verifier = DefaultCodeVerifier(generator, timeProvider)
    verifier.setTimePeriod(PERIOD_SECONDS)
    verifier.setAllowedTimePeriodDiscrepancy(window)
    return verifier.isValidCode(toBase32(secretBase64), code)
  }

  fun toBase32(secretBase64: String): String {
    val bytes = Base64.getDecoder().decode(secretBase64)
    // RFC 4648 base32, unpadded — what authenticator apps expect in otpauth URLs.
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    val builder = StringBuilder()
    var buffer = 0
    var bits = 0
    for (byte in bytes) {
      buffer = (buffer shl 8) or (byte.toInt() and 0xff)
      bits += 8
      while (bits >= 5) {
        builder.append(alphabet[(buffer shr (bits - 5)) and 0x1f])
        bits -= 5
      }
    }
    if (bits > 0) {
      builder.append(alphabet[(buffer shl (5 - bits)) and 0x1f])
    }
    return builder.toString()
  }
}
