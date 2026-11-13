package com.squamed.core.identity.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.identity.service.AuthService
import com.squamed.core.identity.service.MfaService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
  private val authService: AuthService,
  private val mfaService: MfaService,
) {
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  fun register(
    @Valid @RequestBody dto: RegisterDto,
  ): ApiResponse<Map<String, Any>> {
    val user = authService.register(dto.toRequest())
    val userId = user.id ?: throw IllegalStateException("USER_ID_MISSING")
    return ApiResponse.ok(mapOf<String, Any>("userId" to userId.toString()))
  }

  @PostMapping("/login")
  fun login(
    @Valid @RequestBody dto: LoginDto,
    request: HttpServletRequest,
  ): ResponseEntity<ApiResponse<AuthService.LoginResult>> {
    val hospitalId = dto.hospitalId?.let(UUID::fromString)
    val result =
      authService.login(
        username = dto.username,
        password = dto.password,
        ipAddress = request.remoteAddr,
        userAgent = request.getHeader("User-Agent"),
        hospitalId = hospitalId,
      )
    return if (result.status == AuthService.LoginStatus.INVALID_CREDENTIALS) {
      ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.ok(result))
    } else {
      ResponseEntity.ok(ApiResponse.ok(result))
    }
  }

  @PostMapping("/mfa/verify")
  fun verifyMfa(
    @Valid @RequestBody dto: MfaVerifyDto,
    request: HttpServletRequest,
  ): ApiResponse<AuthService.LoginResult> {
    val result =
      authService.verifyMfa(
        mfaToken = dto.mfaToken,
        code = dto.code,
        ipAddress = request.remoteAddr,
        userAgent = request.getHeader("User-Agent"),
        hospitalId = null,
      )
    return ApiResponse.ok(result)
  }

  @PostMapping("/refresh")
  fun refresh(
    @Valid @RequestBody dto: RefreshDto,
    request: HttpServletRequest,
  ): ApiResponse<AuthService.LoginResult> {
    val result =
      authService.refresh(
        refreshToken = dto.refreshToken,
        ipAddress = request.remoteAddr,
        userAgent = request.getHeader("User-Agent"),
      )
    return ApiResponse.ok(result)
  }

  @PostMapping("/logout")
  fun logout(
    @Valid @RequestBody dto: LogoutDto,
  ): ApiResponse<Unit> {
    authService.logout(dto.accessToken, dto.sessionId?.let(UUID::fromString))
    return ApiResponse.ok()
  }

  @PostMapping("/mfa/setup")
  fun setupMfa(
    @Valid @RequestBody dto: SetupMfaDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Map<String, String?>> {
    mfaService.enableMfa(principal.userId, dto.verificationCode)
    return ApiResponse.ok(mapOf("mfaStatus" to "ENABLED"))
  }

  @GetMapping("/mfa/setup")
  fun beginMfaSetup(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<MfaService.MfaSetupResponse> {
    val setup = mfaService.beginMfaSetup(principal.userId)
    return ApiResponse.ok(setup)
  }

  @GetMapping("/me")
  fun me(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AuthService.UserSummary> {
    val summary = authService.me(principal.userId)
    return ApiResponse.ok(summary)
  }
}
