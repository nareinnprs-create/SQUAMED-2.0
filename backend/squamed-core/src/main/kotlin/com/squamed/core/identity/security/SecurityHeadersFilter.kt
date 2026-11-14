package com.squamed.core.identity.security

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.security.SecurityProperties
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

// Runs OUTSIDE the security chain (and outside the 00.02 idempotency filter) so the hardened
// headers are applied to every response, including idempotent replays that never reach a controller.
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER - 1)
class SecurityHeadersFilter : OncePerRequestFilter() {
  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain,
  ) {
    response.setHeader("X-Content-Type-Options", "nosniff")
    response.setHeader("X-Frame-Options", "DENY")
    response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; sandbox")
    response.setHeader("Referrer-Policy", "no-referrer")
    response.setHeader("X-XSS-Protection", "0")
    response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
    filterChain.doFilter(request, response)
  }
}
