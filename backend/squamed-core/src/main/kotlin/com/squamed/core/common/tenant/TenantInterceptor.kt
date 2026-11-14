package com.squamed.core.common.tenant

import com.squamed.core.identity.security.SquamedPrincipal
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.util.UUID

/**
 * ADR-13 stage 1: hospital-bound users cannot touch another hospital's resources via
 * /hospital-config/hospitals/{hospitalId}/... path identifiers. Platform admins pass.
 */
@Component
class TenantInterceptor : HandlerInterceptor {
  private val hospitalPath = Regex("/api/v1/hospital-config/hospitals/([0-9a-fA-F-]{36})")

  override fun preHandle(
    request: HttpServletRequest,
    response: HttpServletResponse,
    handler: Any,
  ): Boolean {
    val principal = SecurityContextHolder.getContext().authentication?.principal as? SquamedPrincipal ?: return true
    val bound = principal.hospitalId ?: return true
    val target =
      hospitalPath
        .find(request.requestURI)
        ?.groupValues
        ?.get(1)
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return true
    if (target != bound) throw AccessDeniedException("TENANT_FORBIDDEN")
    return true
  }
}
