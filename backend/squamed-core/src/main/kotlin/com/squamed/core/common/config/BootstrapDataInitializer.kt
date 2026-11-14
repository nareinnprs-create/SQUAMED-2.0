package com.squamed.core.common.config

import com.squamed.core.hospitalconfig.domain.Hospital
import com.squamed.core.hospitalconfig.repository.HospitalRepository
import com.squamed.core.identity.domain.Role
import com.squamed.core.identity.domain.UserAccount
import com.squamed.core.identity.domain.UserProfile
import com.squamed.core.identity.domain.UserRole
import com.squamed.core.identity.domain.UserStatus
import com.squamed.core.identity.repository.RoleRepository
import com.squamed.core.identity.repository.UserAccountRepository
import com.squamed.core.identity.repository.UserProfileRepository
import com.squamed.core.identity.repository.UserRoleRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Component
@Profile("dev")
class BootstrapDataInitializer(
  private val userRepository: UserAccountRepository,
  private val profileRepository: UserProfileRepository,
  private val roleRepository: RoleRepository,
  private val userRoleRepository: UserRoleRepository,
  private val hospitalRepository: HospitalRepository,
  private val passwordEncoder: PasswordEncoder,
  @Value("\${squamed.bootstrap.admin-email:}") private val adminEmail: String,
  @Value("\${squamed.bootstrap.admin-password:}") private val adminPassword: String,
) : CommandLineRunner {
  override fun run(vararg args: String) {
    seedDefaultHospital()
    val email = adminEmail.ifBlank { "admin@squamed.local" }
    val password = adminPassword.ifBlank { "Admin@12345" }

    val systemRole =
      roleRepository
        .findByName("SYSTEM_ADMIN")
        .orElseGet {
          roleRepository.save(
            Role(
              name = "SYSTEM_ADMIN",
              displayName = "System Administrator",
              description = "Full platform access across all hospitals",
              isSystem = true,
            ),
          )
        }

    val existing = userRepository.findByEmailIgnoreCase(email)
    if (existing.isEmpty) {
      val user =
        userRepository.save(
          UserAccount(
            email = email,
            passwordHash = passwordEncoder.encode(password),
            status = UserStatus.ACTIVE,
          ),
        )
      profileRepository.save(
        UserProfile(
          userId = user.id!!,
          firstName = "System",
          lastName = "Administrator",
          displayName = "SQUAMED Admin",
        ),
      )
      userRoleRepository.save(
        UserRole(
          userId = user.id ?: throw IllegalStateException("USER_ID_MISSING"),
          role = systemRole,
        ),
      )
      logger.info { "Bootstrap admin created: $email" }
    } else {
      logger.info { "Bootstrap admin already exists: $email" }
    }
  }

  // Primary (first-created) hospital. Tenant backfill in V43 attributes all pre-tenant rows to it,
  // and TenantResolver uses it when a platform admin writes without naming a hospital.
  private fun seedDefaultHospital() {
    if (hospitalRepository.findFirstByOrderByCreatedAtAsc() != null) return
    hospitalRepository.save(
      Hospital(
        name = "SQUAMED General Hospital",
        code = "SQUAMED-MAIN",
        country = "Qatar",
        city = "Doha",
      ),
    )
    logger.info { "Bootstrap hospital created: SQUAMED-MAIN" }
  }
}
