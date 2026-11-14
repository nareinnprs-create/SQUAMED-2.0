package com.squamed.core.identity.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import java.time.Instant
import com.squamed.core.identity.security.JwtAuthenticationFilter as JwtFilter

@Configuration
@EnableMethodSecurity
class SecurityConfig(
  private val jwtFilter: JwtFilter,
  private val objectMapper: ObjectMapper,
) {
  @Bean
  fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
      .csrf { it.disable() }
      .cors { }
      .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
      .authorizeHttpRequests { authorize ->
        authorize
          .requestMatchers("/health", "/", "/actuator/health", "/actuator/info")
          .permitAll()
          .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**")
          .permitAll()
          .requestMatchers(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/mfa/verify",
          ).permitAll()
          .requestMatchers("/ws", "/realtime")
          .permitAll()
          .requestMatchers("/api/v1/events/catalog")
          .permitAll()
          .anyRequest()
          .authenticated()
      }.exceptionHandling { handling ->
        handling.authenticationEntryPoint { _, response, _ ->
          writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required")
        }
        handling.accessDeniedHandler { _, response, _ ->
          writeJsonError(
            response,
            HttpServletResponse.SC_FORBIDDEN,
            "ACCESS_DENIED",
            "You do not have permission to perform this action",
          )
        }
      }.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
    return http.build()
  }

  private fun writeJsonError(
    response: jakarta.servlet.http.HttpServletResponse,
    status: Int,
    code: String,
    message: String,
  ) {
    response.status = status
    response.characterEncoding = "UTF-8"
    response.contentType = MediaType.APPLICATION_JSON_VALUE
    response.writer.write(
      objectMapper.writeValueAsString(
        mapOf(
          "error" to mapOf("code" to code, "message" to message),
          "timestamp" to Instant.now().toString(),
        ),
      ),
    )
  }

  @Bean
  fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

  @Bean
  fun authenticationManager(configuration: AuthenticationConfiguration): AuthenticationManager = configuration.authenticationManager
}
