package com.squamed.core.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional
import java.util.UUID

@Configuration
@EnableJpaAuditing
class JpaAuditingConfig {
  @Bean
  fun auditorProvider(): AuditorAware<UUID> =
    AuditorAware {
      Optional
        .ofNullable(SecurityContextHolder.getContext().authentication)
        .map(Authentication::getName)
        .map { name -> runCatching { UUID.fromString(name) }.getOrNull() }
    }
}
