package com.squamed.core.identity.security

import org.springframework.stereotype.Component
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class TotpService {
  companion object {
    private const val SECRET_BYTES = 20
    private const val DIGITS = 6
    private const val PERIOD_SECONDS = 30
    private const val ALGORITHM = "HmacSHA1"
  }

  fun generateSecret(): String {
    val bytes = ByteArray(SECRET_BYTES).also { java.security.SecureRandom().nextBytes(it) }
    return Base64.getEncoder().encodeToString(bytes)
  }

  fun validateCode(
    secretBase64: String,
    code: String,
    window: Int = 1,
  ): Boolean {
    val expected = generateCodes(secretBase64, window)
    return expected.any { it == code }
  }

  fun generateCodes(
    secretBase64: String,
    window: Int = 1,
  ): List<String> {
    val secret = Base64.getDecoder().decode(secretBase64)
    val counter = System.currentTimeMillis() / 1000 / PERIOD_SECONDS
    return (-window..window).map { offset ->
      generateTOTP(secret, counter + offset)
    }
  }

  private fun generateTOTP(
    secret: ByteArray,
    counter: Long,
  ): String {
    val counterBytes =
      java.nio.ByteBuffer
        .allocate(8)
        .putLong(counter)
        .array()
    val mac = Mac.getInstance(ALGORITHM)
    mac.init(SecretKeySpec(secret, ALGORITHM))
    val hash = mac.doFinal(counterBytes)
    val offset = hash[hash.size - 1].toInt() and 0x0f
    val binary =
      ((hash[offset].toInt() and 0x7f) shl 24) or
        ((hash[offset + 1].toInt() and 0xff) shl 16) or
        ((hash[offset + 2].toInt() and 0xff) shl 8) or
        (hash[offset + 3].toInt() and 0xff)
    val otp = binary % 1_000_000
    return otp.toString().padStart(DIGITS, '0')
  }
}
