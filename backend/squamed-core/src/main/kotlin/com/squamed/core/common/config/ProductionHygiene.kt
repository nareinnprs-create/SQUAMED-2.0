package com.squamed.core.common.config

const val DEV_JWT_SECRET = "dev-only-secret-key-change-in-production-0123456789abcdef"
const val DEV_DB_PASSWORD = "squamed_dev_password"

object ProductionHygiene {
  val devJwtSecret: String = DEV_JWT_SECRET
  val devDbPassword: String = DEV_DB_PASSWORD

  fun validate(
    activeProfiles: Collection<String>,
    jwtSecret: String?,
    dbPassword: String?,
    bootstrapAdminPassword: String?,
    bootstrapAdminEmail: String?,
  ) {
    val prod = activeProfiles.any { it.equals("prod", ignoreCase = true) }
    if (!prod) return

    val errors = mutableListOf<String>()

    if (jwtSecret.isNullOrBlank() || jwtSecret.length < 32) {
      errors += "JWT_SECRET must be set to a value of at least 32 characters for the prod profile"
    } else if (jwtSecret == DEV_JWT_SECRET) {
      errors += "JWT_SECRET must not use the development placeholder secret for the prod profile"
    }

    if (dbPassword.isNullOrBlank() || dbPassword == DEV_DB_PASSWORD) {
      errors += "DB_PASSWORD must be set to a non-development value for the prod profile"
    }

    if (bootstrapAdminPassword.isNullOrBlank() || bootstrapAdminPassword.length < 8) {
      errors += "BOOTSTRAP_ADMIN_PASSWORD must be set to a value of at least 8 characters for the prod profile"
    }

    if (bootstrapAdminEmail.isNullOrBlank()) {
      errors += "BOOTSTRAP_ADMIN_EMAIL must be set for the prod profile"
    }

    if (errors.isNotEmpty()) {
      throw IllegalStateException(
        "production startup guard failed:\n  - " + errors.joinToString("\n  - "),
      )
    }
  }
}
