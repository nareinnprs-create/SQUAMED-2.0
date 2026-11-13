package com.squamed.core.common.config

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("prod")
class ProductionHygieneConfig(
  @Value("\${squamed.security.jwt.secret:}") private val jwtSecret: String,
  @Value("\${spring.datasource.password:}") private val dbPassword: String,
  @Value("\${squamed.bootstrap.admin-password:}") private val bootstrapAdminPassword: String,
  @Value("\${squamed.bootstrap.admin-email:}") private val bootstrapAdminEmail: String,
) {
  @PostConstruct
  fun guard() {
    ProductionHygiene.validate(
      activeProfiles = listOf("prod"),
      jwtSecret = jwtSecret,
      dbPassword = dbPassword,
      bootstrapAdminPassword = bootstrapAdminPassword,
      bootstrapAdminEmail = bootstrapAdminEmail,
    )
  }
}
