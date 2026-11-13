package com.squamed.core.identity.service

import com.squamed.core.identity.domain.UserAccount
import com.squamed.core.identity.repository.UserAccountRepository
import com.squamed.core.identity.security.TotpService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MfaService(
  private val userRepository: UserAccountRepository,
  private val totpService: TotpService,
) {
  data class MfaSetupResponse(
    val otpAuthUrl: String,
    val secret: String,
  )

  @Transactional
  fun beginMfaSetup(userId: UUID): MfaSetupResponse {
    val user = userRepository.findById(userId).orElseThrow { IllegalStateException("USER_NOT_FOUND") }
    val secret = user.mfaSecret ?: totpService.generateSecret()
    if (user.mfaSecret == null) {
      user.mfaSecret = secret
      userRepository.save(user)
    }
    val otpAuthUrl = "otpauth://totp/SQUAMED:${user.email}?secret=${base32Encode(secret)}&issuer=SQUAMED&period=30&digits=6"
    return MfaSetupResponse(otpAuthUrl = otpAuthUrl, secret = secret)
  }

  @Transactional
  fun enableMfa(
    userId: UUID,
    verificationCode: String,
  ) {
    val user = userRepository.findById(userId).orElseThrow { IllegalStateException("USER_NOT_FOUND") }
    val secret = user.mfaSecret ?: throw IllegalStateException("MFA_SETUP_NOT_STARTED")
    if (!totpService.validateCode(secret, verificationCode)) {
      throw IllegalArgumentException("INVALID_MFA_CODE")
    }
    user.mfaEnabled = true
    userRepository.save(user)
  }

  private fun base32Encode(secretBase64: String): String {
    val decoded =
      java.util.Base64
        .getDecoder()
        .decode(secretBase64)
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    val builder = StringBuilder()
    var buffer = 0
    var bits = 0
    for (byte in decoded) {
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
