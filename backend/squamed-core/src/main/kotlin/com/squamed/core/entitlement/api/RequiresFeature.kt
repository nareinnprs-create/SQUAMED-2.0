package com.squamed.core.entitlement.api

import com.squamed.core.entitlement.service.EntitlementService
import com.squamed.core.identity.security.SquamedPrincipal
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

// 01.10: a module-level gate. Controllers declare the feature they need; a plan without it gets a
// 403 with ENTITLEMENT_REQUIRED, which is distinguishable from a missing permission (ACCESS_DENIED).
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresFeature(
  val value: String,
)

@Component
class EntitlementInterceptor(
  private val entitlementService: EntitlementService,
) : HandlerInterceptor {
  override fun preHandle(
    request: HttpServletRequest,
    response: HttpServletResponse,
    handler: Any,
  ): Boolean {
    val method = handler as? HandlerMethod ?: return true
    val feature =
      AnnotatedElementUtils.findMergedAnnotation(method.method, RequiresFeature::class.java)
        ?: AnnotatedElementUtils.findMergedAnnotation(method.beanType, RequiresFeature::class.java)
        ?: return true
    val principal = SecurityContextHolder.getContext().authentication?.principal as? SquamedPrincipal ?: return true
    val hospitalId = principal.hospitalId ?: return true
    entitlementService.requireFeature(hospitalId, feature.value)
    return true
  }
}
