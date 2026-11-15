package com.squamed.core.identity.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class JwtAuthenticationFilter(
  private val jwtService: JwtService,
  private val tokenBlacklistService: TokenBlacklistService,
) : OncePerRequestFilter() {
  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain,
  ) {
    val header = request.getHeader(HttpHeaders.AUTHORIZATION)
    if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().authentication == null) {
      val token = header.removePrefix("Bearer ").trim()
      val claims = jwtService.parseAccessToken(token)
      if (claims != null) {
        val sessionKey = claims.sessionId.toString()
        if (!tokenBlacklistService.isAccessBlocked(claims.sessionId, sessionKey)) {
          val authentication =
            UsernamePasswordAuthenticationToken(
              SquamedPrincipal(
                userId = claims.userId,
                sessionId = claims.sessionId,
                hospitalId = claims.hospitalId,
                jti = sessionKey,
              ),
              token,
              listOf(SimpleGrantedAuthority("ROLE_AUTHENTICATED")),
            )
          SecurityContextHolder.getContext().authentication = authentication
        }
      }
    }
    filterChain.doFilter(request, response)
  }
}

data class SquamedPrincipal(
  val userId: UUID,
  val sessionId: UUID,
  val hospitalId: UUID?,
  val jti: String,
)
