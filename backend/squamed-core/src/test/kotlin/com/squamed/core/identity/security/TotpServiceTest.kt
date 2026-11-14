package com.squamed.core.identity.security

import dev.samstevens.totp.time.TimeProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

private class FixedTimeProvider(
  private val time: Long,
) : TimeProvider {
  override fun getTime(): Long = time
}

class TotpServiceTest {
  // RFC 4226 test secret: ASCII "12345678901234567890" = base32 GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ
  private val secretBase64 = Base64.getEncoder().encodeToString("12345678901234567890".toByteArray())

  private val rfc4226Vectors =
    mapOf(
      0L to "755224",
      1L to "287082",
      2L to "359152",
      3L to "969429",
      4L to "338314",
      5L to "254676",
      6L to "287922",
      7L to "162583",
      8L to "399871",
      9L to "520489",
    )

  @Test
  fun `validates RFC 4226 reference vectors at each period`() {
    rfc4226Vectors.forEach { (counter, code) ->
      val service = TotpService(FixedTimeProvider(counter * 30))
      assertTrue(service.validateCode(secretBase64, code, 0), "counter=$counter expected $code")
    }
  }

  @Test
  fun `window accepts adjacent periods`() {
    // At counter 5 (time 150): current + previous + next must validate with window=1.
    val service = TotpService(FixedTimeProvider(5 * 30))
    assertTrue(service.validateCode(secretBase64, "254676"))
    assertTrue(service.validateCode(secretBase64, "338314"))
    assertTrue(service.validateCode(secretBase64, "287922"))
    assertFalse(service.validateCode(secretBase64, "399871", 0), "counter-8 code is outside window")
  }

  @Test
  fun `rejects wrong code`() {
    val service = TotpService(FixedTimeProvider(0))
    assertFalse(service.validateCode(secretBase64, "000000"))
  }

  @Test
  fun `generateSecret returns base64 of 20 bytes`() {
    val secret = TotpService().generateSecret()
    assertEquals(20, Base64.getDecoder().decode(secret).size)
  }

  @Test
  fun `toBase32 matches RFC 4648 encoding`() {
    assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", TotpService().toBase32(secretBase64))
  }
}
