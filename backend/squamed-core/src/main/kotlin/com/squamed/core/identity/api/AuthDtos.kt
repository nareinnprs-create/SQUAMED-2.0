package com.squamed.core.identity.api

import com.squamed.core.identity.service.AuthService
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class RegisterDto(
  @field:Email
  @field:NotBlank
  val email: String,
  @field:NotBlank
  @field:Size(min = 8, max = 128)
  val password: String,
  @field:NotBlank
  @field:Size(max = 100)
  val firstName: String,
  @field:NotBlank
  @field:Size(max = 100)
  val lastName: String,
  @field:Pattern(regexp = "^[+0-9 ()-]{7,20}$")
  val phone: String? = null,
  val roles: List<String> = emptyList(),
) {
  fun toRequest() =
    AuthService.RegisterRequest(
      email = email,
      password = password,
      firstName = firstName,
      lastName = lastName,
      phone = phone,
      roles = roles,
    )
}

data class LoginDto(
  @field:NotBlank
  val username: String,
  @field:NotBlank
  val password: String,
  val hospitalId: String? = null,
)

data class MfaVerifyDto(
  @field:NotBlank
  val mfaToken: String,
  @field:NotBlank
  @field:Pattern(regexp = "^\\d{6}$")
  val code: String,
)

data class RefreshDto(
  @field:NotBlank
  val refreshToken: String,
)

data class LogoutDto(
  val accessToken: String? = null,
  val sessionId: String? = null,
)

data class SetupMfaDto(
  @field:NotBlank
  @field:Pattern(regexp = "^\\d{6}$")
  val verificationCode: String,
)
