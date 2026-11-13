package com.squamed.core.identity.security

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Aspect
@Component
class PermissionAspect(
  private val evaluator: SquamedPermissionEvaluator,
) {
  @Around("@annotation(requiresPermission)")
  fun enforce(
    joinPoint: ProceedingJoinPoint,
    requiresPermission: RequiresPermission,
  ): Any? {
    val authentication = SecurityContextHolder.getContext().authentication
    val principal =
      authentication?.principal as? SquamedPrincipal
        ?: throw AccessDeniedException("Authentication required")

    val granted = evaluator.check(principal, requiresPermission.value)
    if (!granted) {
      throw AccessDeniedException(
        "Permission '${requiresPermission.value}' is required for ${joinPoint.signature.toShortString()}",
      )
    }
    return joinPoint.proceed()
  }
}
