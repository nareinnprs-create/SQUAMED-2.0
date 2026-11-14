package com.squamed.core.identity.service

import com.squamed.core.identity.domain.SessionRecord
import com.squamed.core.identity.domain.UserAccount
import com.squamed.core.identity.domain.UserProfile
import com.squamed.core.identity.domain.UserRole
import com.squamed.core.identity.domain.UserStatus
import com.squamed.core.identity.repository.RoleRepository
import com.squamed.core.identity.repository.SessionRepository
import com.squamed.core.identity.repository.UserAccountRepository
import com.squamed.core.identity.repository.UserProfileRepository
import com.squamed.core.identity.repository.UserRoleRepository
import com.squamed.core.identity.security.JwtService
import com.squamed.core.identity.security.TokenBlacklistService
import com.squamed.core.identity.security.TotpService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class AuthService(
  private val userRepository: UserAccountRepository,
  private val profileRepository: UserProfileRepository,
  private val roleRepository: RoleRepository,
  private val userRoleRepository: UserRoleRepository,
  private val sessionRepository: SessionRepository,
  private val passwordEncoder: PasswordEncoder,
  private val jwtService: JwtService,
  private val tokenBlacklistService: TokenBlacklistService,
  private val totpService: TotpService,
  private val deviceRegistryService: DeviceRegistryService,
  @Value("\${squamed.security.jwt.refresh-token-ttl-days}") private val refreshTtlDays: Long,
) {
  data class RegisterRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val phone: String? = null,
    val roles: List<String> = emptyList(),
  )

  data class LoginResult(
    val status: LoginStatus,
    val userId: UUID? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val sessionId: UUID? = null,
    val mfaRequired: Boolean = false,
    val mfaToken: String? = null,
    val user: UserSummary? = null,
  )

  enum class LoginStatus {
    SUCCESS,
    MFA_REQUIRED,
    INVALID_CREDENTIALS,
    ACCOUNT_LOCKED,
    ACCOUNT_DISABLED,
    EMAIL_NOT_VERIFIED,
  }

  data class UserSummary(
    val userId: UUID,
    val email: String,
    val displayName: String,
    val roles: List<String>,
    val permissions: List<String>,
    val themePreference: String,
  )

  @Transactional
  fun register(request: RegisterRequest): UserAccount {
    if (userRepository.findByEmailIgnoreCase(request.email).isPresent) {
      throw IllegalStateException("EMAIL_ALREADY_EXISTS")
    }
    val user =
      UserAccount(
        email = request.email.trim().lowercase(),
        phone = request.phone?.trim(),
        passwordHash = passwordEncoder.encode(request.password),
        status = UserStatus.ACTIVE,
      )
    userRepository.save(user)

    val profile =
      UserProfile(
        userId = user.id ?: throw IllegalStateException("USER_ID_MISSING"),
        firstName = request.firstName.trim(),
        lastName = request.lastName.trim(),
      )
    profileRepository.save(profile)

    if (request.roles.isNotEmpty()) {
      request.roles.forEach { roleName ->
        roleRepository.findByName(roleName).ifPresent { role ->
          userRoleRepository.save(
            UserRole(
              userId = user.id ?: throw IllegalStateException("USER_ID_MISSING"),
              role = role,
            ),
          )
        }
      }
    }
    logger.info { "Registered user ${user.id} (${user.email})" }
    return user
  }

  @Transactional
  fun login(
    username: String,
    password: String,
    ipAddress: String?,
    userAgent: String?,
    hospitalId: UUID?,
  ): LoginResult {
    require(username.isNotBlank()) { "USERNAME_REQUIRED" }
    require(password.isNotBlank()) { "PASSWORD_REQUIRED" }
    val user =
      userRepository
        .findByEmailIgnoreCase(username.trim())
        .orElse(userRepository.findByPhone(username.trim()).orElse(null))
        ?: return LoginResult(LoginStatus.INVALID_CREDENTIALS)

    if (user.isLocked()) {
      return LoginResult(LoginStatus.ACCOUNT_LOCKED)
    }

    if (!passwordEncoder.matches(password, user.passwordHash)) {
      user.failedLoginAttempts += 1
      if (user.failedLoginAttempts >= 5) {
        user.lockedUntil = Instant.now().plus(Duration.ofMinutes(15))
      }
      userRepository.save(user)
      return LoginResult(LoginStatus.INVALID_CREDENTIALS)
    }

    user.failedLoginAttempts = 0
    user.lockedUntil = null
    user.lastLoginAt = Instant.now()
    userRepository.save(user)

    if (user.mfaEnabled) {
      val mfaToken = jwtService.generateMfaToken(user.id ?: throw IllegalStateException("USER_ID_MISSING"))
      return LoginResult(LoginStatus.MFA_REQUIRED, mfaRequired = true, mfaToken = mfaToken, userId = user.id)
    }

    return completeLogin(user, ipAddress, userAgent, hospitalId)
  }

  @Transactional
  fun verifyMfa(
    mfaToken: String,
    code: String,
    ipAddress: String?,
    userAgent: String?,
    hospitalId: UUID?,
  ): LoginResult {
    val userId = jwtService.parseMfaToken(mfaToken) ?: return LoginResult(LoginStatus.INVALID_CREDENTIALS)
    val user = userRepository.findById(userId).orElse(null) ?: return LoginResult(LoginStatus.INVALID_CREDENTIALS)
    val secret = user.mfaSecret ?: return LoginResult(LoginStatus.INVALID_CREDENTIALS)
    if (!totpService.validateCode(secret, code.trim())) {
      return LoginResult(LoginStatus.INVALID_CREDENTIALS)
    }
    return completeLogin(user, ipAddress, userAgent, hospitalId)
  }

  @Transactional
  fun refresh(
    refreshToken: String,
    ipAddress: String?,
    userAgent: String?,
  ): LoginResult {
    val claims = jwtService.parseRefreshToken(refreshToken) ?: return LoginResult(LoginStatus.INVALID_CREDENTIALS)
    val session =
      sessionRepository.findById(claims.sessionId).orElse(null)
        ?: return LoginResult(LoginStatus.INVALID_CREDENTIALS)
    if (session.isRevoked() || session.isExpired()) {
      return LoginResult(LoginStatus.INVALID_CREDENTIALS)
    }
    if (session.refreshTokenHash != hash(refreshToken)) {
      return LoginResult(LoginStatus.INVALID_CREDENTIALS)
    }
    val user =
      userRepository.findById(claims.userId).orElse(null)
        ?: return LoginResult(LoginStatus.INVALID_CREDENTIALS)
    return completeLogin(user, ipAddress, userAgent, session.hospitalId)
  }

  @Transactional
  fun logout(
    accessToken: String?,
    sessionIdValue: UUID?,
  ) {
    if (sessionIdValue != null) {
      val session = sessionRepository.findById(sessionIdValue).orElse(null)
      session?.let {
        it.revokedAt = Instant.now()
        sessionRepository.save(it)
      }
      tokenBlacklistService.revokeSession(
        sessionIdValue,
        session?.refreshTokenHash ?: "none",
        Duration.ofDays(refreshTtlDays),
      )
    }
    accessToken?.let { rawToken ->
      val claims = jwtService.parseAccessToken(rawToken)
      if (claims != null) {
        tokenBlacklistService.blacklistAccessToken(claims.sessionId.toString(), claims.userId, Duration.ofMinutes(15))
      }
    }
  }

  private fun completeLogin(
    user: UserAccount,
    ipAddress: String?,
    userAgent: String?,
    hospitalId: UUID?,
  ): LoginResult {
    if (user.status == UserStatus.DISABLED) return LoginResult(LoginStatus.ACCOUNT_DISABLED)
    if (user.status != UserStatus.ACTIVE && user.status != UserStatus.PENDING) {
      return LoginResult(LoginStatus.ACCOUNT_DISABLED)
    }
    val userId = user.id ?: throw IllegalStateException("USER_ID_MISSING")
    val session =
      SessionRecord(
        userId = userId,
        tokenHash = UUID.randomUUID().toString(),
        refreshTokenHash = UUID.randomUUID().toString(),
        ipAddress = ipAddress,
        userAgent = userAgent?.take(512),
        deviceId = deviceRegistryService.registerOrTouch(userId, deviceRegistryService.fingerprint(userAgent), userAgent?.take(255), hospitalId).id,
        hospitalId = hospitalId,
        expiresAt = Instant.now().plus(Duration.ofDays(refreshTtlDays)),
      )
    sessionRepository.save(session)

    val sessionId = session.id ?: throw IllegalStateException("SESSION_ID_MISSING")
    val accessToken = jwtService.generateAccessToken(userId, hospitalId, sessionId)
    val newRefreshToken = jwtService.generateRefreshToken(userId, sessionId)
    session.refreshTokenHash = hash(newRefreshToken)
    sessionRepository.save(session)

    val summary = buildUserSummary(user)

    return LoginResult(
      status = LoginStatus.SUCCESS,
      userId = user.id,
      accessToken = accessToken,
      refreshToken = newRefreshToken,
      sessionId = session.id,
      user = summary,
    )
  }

  @Transactional(readOnly = true)
  fun me(userId: UUID): UserSummary {
    val user = userRepository.findById(userId).orElseThrow { IllegalStateException("USER_NOT_FOUND") }
    return buildUserSummary(user)
  }

  private fun buildUserSummary(user: UserAccount): UserSummary {
    val userId = user.id ?: throw IllegalStateException("USER_ID_MISSING")
    val roles = userRoleRepository.findByUserId(userId)
    val roleNames = roles.mapNotNull { it.role.name }
    val permissions =
      roles
        .flatMap { it.role.permissions }
        .map { it.code }
        .distinct()
    val profile = profileRepository.findByUserId(userId).orElse(null)
    return UserSummary(
      userId = userId,
      email = user.email,
      displayName = profile?.effectiveDisplayName ?: user.email,
      roles = roleNames,
      permissions = permissions,
      themePreference = profile?.themePreference ?: "clinical",
    )
  }

  private fun hash(raw: String): String = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
}
